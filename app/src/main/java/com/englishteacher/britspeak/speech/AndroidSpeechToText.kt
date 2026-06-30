package com.englishteacher.britspeak.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [SpeechToText] backed by the Android [SpeechRecognizer]. Must be driven on the main thread. */
@Singleton
class AndroidSpeechToText
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SpeechToText {
        private val main = Handler(Looper.getMainLooper())
        private var recognizer: SpeechRecognizer? = null
        private var callback: SttCallback? = null

        override val isAvailable: Boolean
            get() = SpeechRecognizer.isRecognitionAvailable(context)

        override fun startListening(
            localeTag: String,
            callback: SttCallback,
        ) {
            main.post {
                this.callback = callback
                if (!isAvailable) {
                    callback.onError("Speech recognition is not available on this device")
                    return@post
                }
                recognizer?.destroy()
                val sr = SpeechRecognizer.createSpeechRecognizer(context)
                sr.setRecognitionListener(listener)
                recognizer = sr

                val intent =
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                sr.startListening(intent)
            }
        }

        override fun stopListening() {
            main.post { recognizer?.stopListening() }
        }

        override fun release() {
            main.post {
                recognizer?.destroy()
                recognizer = null
                callback = null
            }
        }

        private val listener =
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    callback?.onReady()
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    callback?.onEndOfSpeech()
                }

                override fun onError(error: Int) {
                    callback?.onError(describeError(error))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    firstResult(partialResults)?.let { callback?.onPartial(it) }
                }

                override fun onResults(results: Bundle?) {
                    val text = firstResult(results)
                    if (text.isNullOrBlank()) {
                        callback?.onError("Didn't catch that — please try again")
                    } else {
                        callback?.onResult(text)
                    }
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) {}
            }

        private fun firstResult(bundle: Bundle?): String? =
            bundle
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()

        private fun describeError(error: Int): String =
            when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                -> "Network error during recognition"
                SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — please try again"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser is busy"
                else -> "Speech recognition error ($error)"
            }
    }
