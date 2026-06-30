package com.englishteacher.britspeak.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TutorVoice] backed by the Android [TextToSpeech] engine, configured for British English
 * (`en-GB`). Falls back gracefully if the locale or a voice isn't available.
 */
@Singleton
class AndroidTutorVoice
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : TutorVoice {
        private val main = Handler(Looper.getMainLooper())
        private val callbacks = ConcurrentHashMap<String, Pair<() -> Unit, () -> Unit>>()
        private var ready = false
        private var counter = 0L

        private val tts: TextToSpeech =
            TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts.setLanguage(Locale.UK)
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        tts.language = Locale.ENGLISH
                    }
                    tts.setSpeechRate(0.95f)
                    tts.setPitch(1.0f)
                    ready = true
                }
            }

        init {
            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        callbacks[utteranceId]?.first?.let { cb -> main.post(cb) }
                    }

                    override fun onDone(utteranceId: String) {
                        callbacks.remove(utteranceId)?.second?.let { cb -> main.post(cb) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) {
                        callbacks.remove(utteranceId)?.second?.let { cb -> main.post(cb) }
                    }

                    override fun onError(
                        utteranceId: String,
                        errorCode: Int,
                    ) {
                        callbacks.remove(utteranceId)?.second?.let { cb -> main.post(cb) }
                    }
                },
            )
        }

        override fun speak(
            text: String,
            onStart: () -> Unit,
            onDone: () -> Unit,
        ) {
            if (text.isBlank()) {
                onDone()
                return
            }
            val id = "tts-${counter++}"
            callbacks[id] = onStart to onDone
            if (!ready) {
                // Engine not initialised yet — don't leave the UI stuck.
                main.postDelayed({ callbacks.remove(id)?.second?.invoke() }, 300)
                return
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }

        override fun stop() {
            tts.stop()
            callbacks.keys.toList().forEach { id -> callbacks.remove(id)?.second?.let(main::post) }
        }

        override fun release() {
            tts.stop()
            tts.shutdown()
        }
    }
