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
                    val text = runCatching { captureAndTranscribe(callback) }.getOrNull()
                    recording = false
                    if (text != null) {
                        main.post {
                            if (text.isBlank()) callback.onEndOfSpeech() else callback.onResult(text)
                        }
                    } else {
                        main.post { callback.onError("录音失败，请重试。") }
                    }
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

        /** Records until a trailing silence (or [stopListening]/timeout), then runs whisper once. */
        private fun captureAndTranscribe(callback: SttCallback): String {
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
                return ""
            }

            val samples = ArrayList<Float>(SAMPLE_RATE * 4)
            val chunk = ShortArray(SAMPLE_RATE / 10) // 100 ms
            var speechStarted = false
            var silenceMs = 0
            var elapsedMs = 0
            var notifiedProcessing = false

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
                    elapsedMs += chunkMs
                    if (rms > START_RMS) {
                        speechStarted = true
                        silenceMs = 0
                    } else if (speechStarted) {
                        silenceMs += chunkMs
                        if (silenceMs >= TRAILING_SILENCE_MS) break
                    }
                    if (elapsedMs >= MAX_RECORD_MS) break
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }

            if (!speechStarted || samples.size < SAMPLE_RATE / 2) return "" // < 0.5s of speech

            // Let the UI show a "recognising" hint while whisper runs (it isn't instant).
            if (!notifiedProcessing) {
                main.post { callback.onPartial("（识别中…）") }
                notifiedProcessing = true
            }

            val ptr = ctxPtr
            if (ptr == 0L) return ""
            val floats = FloatArray(samples.size) { samples[it] }
            val threads = minOf(4, Runtime.getRuntime().availableProcessors())
            return WhisperLib.transcribe(ptr, threads, floats).trim()
        }

        private fun ensureModelFile(): File? {
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
            const val MODEL_FILE = "ggml-base.en-q5_1.bin"

            /** RMS above this (on [-1,1] samples) counts as speech. Tune from real-device testing. */
            const val START_RMS = 0.015
            const val TRAILING_SILENCE_MS = 1200
            const val MAX_RECORD_MS = 15000
        }
    }
