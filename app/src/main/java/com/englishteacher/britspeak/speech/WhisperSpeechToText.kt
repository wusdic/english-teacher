package com.englishteacher.britspeak.speech

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * On-device, offline speech-to-text via **whisper.cpp** (see `app/src/main/cpp`). Whisper is far
 * stronger on accented / non-native English than the Vosk model used on the main branch, at the
 * cost of a native library, a larger model, and non-streaming recognition (it transcribes a whole
 * captured utterance rather than word-by-word).
 *
 * Capture: raw 16 kHz mono PCM via [AudioRecord], with a simple energy-based endpointer so a turn
 * finalises automatically after a short trailing silence (or when [stopListening] is called). The
 * captured audio is then transcribed in one whisper.cpp pass and delivered via [SttCallback].
 */
@Singleton
class WhisperSpeechToText
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SpeechToText {
        private val main = Handler(Looper.getMainLooper())

        @Volatile private var ctxPtr: Long = 0L

        @Volatile private var preparing = true

        @Volatile private var recording = false

        private var recordThread: Thread? = null

        init {
            // Copy the bundled model out of assets and initialise whisper off the main thread.
            thread(name = "whisper-init") {
                runCatching {
                    if (!WhisperLib.ensureLoaded()) return@runCatching
                    val modelFile = ensureModelFile() ?: return@runCatching
                    val ptr = WhisperLib.initContext(modelFile.absolutePath)
                    if (ptr != 0L) ctxPtr = ptr
                }
                preparing = false
            }
        }

        override val isAvailable: Boolean
            get() = ctxPtr != 0L

        override val isLoading: Boolean
            get() = ctxPtr == 0L && preparing

        @SuppressLint("MissingPermission") // caller (ChatScreen) requests RECORD_AUDIO first
        override fun startListening(
            localeTag: String,
            callback: SttCallback,
        ) {
            if (recording) return
            if (ctxPtr == 0L) {
                val msg =
                    if (preparing) {
                        "离线语音模型正在加载，请等待几秒后重试。"
                    } else {
                        "离线语音模型加载失败，请重新安装。"
                    }
                main.post { callback.onError(msg) }
                return
            }

            recording = true
            recordThread =
                thread(name = "whisper-record") {
                    runCatching { runTurn(callback) }
                        .onFailure { main.post { callback.onError("录音失败，请重试。") } }
                    recording = false
                }
            main.post { callback.onReady() }
        }

        override fun stopListening() {
            recording = false
        }

        override fun release() {
            recording = false
            recordThread?.let { runCatching { it.join(500) } }
            val ptr = ctxPtr
            ctxPtr = 0L
            if (ptr != 0L) runCatching { WhisperLib.freeContext(ptr) }
        }

        /**
         * One listening turn: record until a trailing silence (or [stopListening]/timeout), signal
         * end-of-speech immediately (so the UI can react while whisper runs), transcribe, deliver.
         *
         * The endpointer is **noise-adaptive**: the speech threshold tracks a running noise floor
         * (the quietest chunk heard so far) instead of a fixed constant, so background hum can't
         * keep resetting the silence timer and make the app wait forever for the learner to stop.
         */
        private fun runTurn(callback: SttCallback) {
            val minBuf =
                AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufSize = maxOf(minBuf, SAMPLE_RATE * 2)
            val recorder =
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize,
                )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                main.post { callback.onError("无法打开麦克风，请重试。") }
                return
            }

            val samples = ArrayList<Float>(SAMPLE_RATE * 4)
            val chunk = ShortArray(SAMPLE_RATE / 10) // 100 ms
            // Noise-robust endpointing lives in EnergyEndpointer (pure + unit-tested).
            val endpointer = EnergyEndpointer()

            recorder.startRecording()
            try {
                while (recording) {
                    val n = recorder.read(chunk, 0, chunk.size)
                    if (n <= 0) continue
                    var sumSq = 0.0
                    for (i in 0 until n) {
                        val s = chunk[i] / 32768f
                        samples.add(s)
                        sumSq += (s * s).toDouble()
                    }
                    val rms = sqrt(sumSq / n)
                    val chunkMs = n * 1000 / SAMPLE_RATE
                    if (endpointer.feed(rms, chunkMs) != EnergyEndpointer.Decision.CONTINUE) break
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }

            // Require a minimum amount of actual voiced audio, not just a triggered start —
            // brief noise bursts that sneak past the endpointer get dropped here instead of
            // being transcribed into hallucinated words the tutor then responds to.
            if (!endpointer.speechStarted ||
                endpointer.voicedMs < MIN_VOICED_MS ||
                samples.size < SAMPLE_RATE / 2
            ) {
                // Nothing usable was said — end the turn quietly rather than with an error.
                main.post { callback.onResult("") }
                return
            }

            // Capture is over: tell the UI right away so it can play a filler / show "thinking"
            // while whisper transcribes, instead of appearing stuck in listening.
            main.post { callback.onEndOfSpeech() }

            val ptr = ctxPtr
            val text =
                if (ptr == 0L) {
                    ""
                } else {
                    val floats = FloatArray(samples.size) { samples[it] }
                    val threads = minOf(6, Runtime.getRuntime().availableProcessors())
                    WhisperLib.transcribe(ptr, threads, floats).trim()
                }
            // Whisper is known to hallucinate stock phrases on noise-only audio; treat those (and
            // blanks) as "nothing said" so the tutor never answers something the learner didn't say.
            val usable = text.takeUnless { isNoiseHallucination(it) }.orEmpty()
            main.post { callback.onResult(usable) }
        }

        /** Classic whisper outputs for noise/music-only input — never real learner speech here. */
        private fun isNoiseHallucination(text: String): Boolean {
            val normalized = text.lowercase().trim().trim('.', '!', '?', ',', ' ')
            return normalized.isEmpty() ||
                normalized in
                setOf(
                    "thank you",
                    "thanks for watching",
                    "thank you for watching",
                    "please subscribe",
                    "bye",
                )
        }

        private fun ensureModelFile(): File? {
            // Clean up the previous model generation so it doesn't waste the user's storage.
            LEGACY_MODEL_FILES.forEach { runCatching { File(context.filesDir, it).delete() } }
            val outFile = File(context.filesDir, MODEL_FILE)
            if (outFile.exists() && outFile.length() > 0) return outFile
            return runCatching {
                context.assets.open("$MODEL_ASSET_DIR/$MODEL_FILE").use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                outFile
            }.getOrNull()
        }

        private companion object {
            const val SAMPLE_RATE = 16000
            const val MODEL_ASSET_DIR = "whisper-model"

            // small.en: markedly better accuracy on short, accented utterances than base.en; the
            // audio_ctx trimming in the JNI layer keeps short-turn transcription fast enough.
            const val MODEL_FILE = "ggml-small.en-q5_1.bin"
            val LEGACY_MODEL_FILES = listOf("ggml-base.en-q5_1.bin")

            /** Minimum voiced audio required before a capture is worth transcribing. */
            const val MIN_VOICED_MS = 350
        }
    }
