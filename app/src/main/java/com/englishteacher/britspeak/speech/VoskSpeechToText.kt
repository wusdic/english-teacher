package com.englishteacher.britspeak.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fully **offline, on-device** speech-to-text via Vosk. No network is used at any point: the
 * acoustic/language model is bundled in the APK under `assets/vosk-model/` and unpacked to
 * internal storage on first launch.
 *
 * If the model assets are absent (e.g. a local build that skipped the model-fetch step) the
 * engine reports itself unavailable rather than crashing.
 */
@Singleton
class VoskSpeechToText
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SpeechToText {
        private val main = Handler(Looper.getMainLooper())

        @Volatile private var model: Model? = null

        @Volatile private var unpacking = false

        @Volatile private var delivered = false

        @Volatile private var lastError: String? = null

        private var speechService: SpeechService? = null
        private var callback: SttCallback? = null

        init {
            ensureModel()
        }

        override val isAvailable: Boolean
            get() = model != null

        private fun modelAssetPresent(): Boolean =
            runCatching { context.assets.list(MODEL_ASSET)?.isNotEmpty() == true }.getOrDefault(false)

        private fun ensureModel() {
            if (model != null || unpacking) return
            if (!modelAssetPresent()) return
            unpacking = true
            try {
                StorageService.unpack(
                    context,
                    MODEL_ASSET,
                    MODEL_TARGET,
                    { m ->
                        model = m
                        lastError = null
                        unpacking = false
                    },
                    { e ->
                        lastError = e?.message ?: "unpack failed"
                        unpacking = false
                    },
                )
            } catch (t: Throwable) {
                lastError = t.message ?: "unpack error"
                unpacking = false
            }
        }

        override fun startListening(
            localeTag: String,
            callback: SttCallback,
        ) {
            main.post {
                this.callback = callback
                delivered = false
                val readyModel = model
                if (readyModel == null) {
                    ensureModel()
                    val message =
                        when {
                            !modelAssetPresent() ->
                                "未找到离线语音模型，请重新安装完整版 APK。"
                            lastError != null ->
                                "离线语音模型加载失败：$lastError。请重试。"
                            else ->
                                "离线语音模型正在加载，请等待几秒后重试。"
                        }
                    callback.onError(message)
                    return@post
                }
                try {
                    stopServiceInternal()
                    val recognizer = Recognizer(readyModel, SAMPLE_RATE)
                    val service = SpeechService(recognizer, SAMPLE_RATE)
                    speechService = service
                    service.startListening(listener)
                    callback.onReady()
                } catch (e: Exception) {
                    callback.onError(e.message ?: "语音识别启动失败")
                }
            }
        }

        override fun stopListening() {
            main.post { speechService?.stop() }
        }

        override fun release() {
            main.post {
                stopServiceInternal()
                callback = null
            }
        }

        private fun stopServiceInternal() {
            speechService?.let {
                it.stop()
                it.shutdown()
            }
            speechService = null
        }

        private val listener =
            object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    textOf(hypothesis, "partial")?.let { if (it.isNotBlank()) callback?.onPartial(it) }
                }

                override fun onResult(hypothesis: String?) {
                    deliverOnce(textOf(hypothesis, "text"))
                }

                override fun onFinalResult(hypothesis: String?) {
                    deliverOnce(textOf(hypothesis, "text"))
                    callback?.onEndOfSpeech()
                }

                override fun onError(e: Exception?) {
                    callback?.onError(e?.message ?: "语音识别错误")
                }

                override fun onTimeout() {
                    if (!delivered) callback?.onEndOfSpeech()
                }
            }

        private fun deliverOnce(text: String?) {
            if (delivered) return
            if (text.isNullOrBlank()) return
            delivered = true
            callback?.onResult(text)
            // One utterance per turn: stop after the first result.
            speechService?.stop()
        }

        private fun textOf(
            hypothesis: String?,
            key: String,
        ): String? = VoskHypothesisParser.extract(hypothesis, key)

        companion object {
            private const val SAMPLE_RATE = 16000.0f
            private const val MODEL_ASSET = "vosk-model"
            private const val MODEL_TARGET = "vosk-model"
        }
    }
