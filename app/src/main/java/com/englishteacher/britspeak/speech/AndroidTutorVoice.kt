package com.englishteacher.britspeak.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TutorVoice] backed by the Android [TextToSpeech] engine, configured for British English
 * (`en-GB`). Falls back gracefully if the locale or a voice isn't available.
 *
 * `TextToSpeech.speak()` is a no-op until the engine has finished initialising, so utterances
 * requested before then are **buffered** (via [PendingSpeechQueue]) and flushed in order once
 * `onInit` fires — otherwise the first lines (a session opener, or a reply submitted seconds after
 * launch) would be silently dropped. A safety timeout releases their completion callbacks if init
 * never completes, so the UI can never get stuck waiting on audio that will never play.
 */
@Singleton
class AndroidTutorVoice
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : TutorVoice {
        private data class Pending(val id: String, val text: String, val queueMode: Int)

        private val main = Handler(Looper.getMainLooper())
        private val callbacks = ConcurrentHashMap<String, Pair<() -> Unit, () -> Unit>>()
        private val counter = AtomicLong(0)
        private val queue = PendingSpeechQueue<Pending>()

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
                    // Speak anything requested during initialisation, in order.
                    queue.markReady().forEach { tts.speak(it.text, it.queueMode, null, it.id) }
                } else {
                    releaseBuffered(queue.markFailed())
                }
            }

        init {
            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {
                        callbacks[utteranceId]?.first?.let { cb -> postMain { cb() } }
                    }

                    override fun onDone(utteranceId: String) {
                        callbacks.remove(utteranceId)?.second?.let { cb -> postMain { cb() } }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) {
                        callbacks.remove(utteranceId)?.second?.let { cb -> postMain { cb() } }
                    }

                    override fun onError(
                        utteranceId: String,
                        errorCode: Int,
                    ) {
                        callbacks.remove(utteranceId)?.second?.let { cb -> postMain { cb() } }
                    }
                },
            )
            // Safety net: if the engine never initialises, don't leave buffered turns (and the UI
            // waiting on their completion) stuck forever — release them after a generous timeout.
            main.postDelayed({ if (!queue.isSettled()) releaseBuffered(queue.markFailed()) }, INIT_TIMEOUT_MS)
        }

        override fun speak(
            text: String,
            onStart: () -> Unit,
            onDone: () -> Unit,
        ) = enqueueInternal(text, TextToSpeech.QUEUE_FLUSH, onStart, onDone)

        override fun enqueue(
            text: String,
            onStart: () -> Unit,
            onDone: () -> Unit,
        ) = enqueueInternal(text, TextToSpeech.QUEUE_ADD, onStart, onDone)

        private fun enqueueInternal(
            text: String,
            queueMode: Int,
            onStart: () -> Unit,
            onDone: () -> Unit,
        ) {
            if (text.isBlank()) {
                onDone()
                return
            }
            val id = "tts-${counter.getAndIncrement()}"
            callbacks[id] = onStart to onDone

            when (queue.submit(Pending(id, text, queueMode))) {
                PendingSpeechQueue.Decision.SPEAK -> tts.speak(text, queueMode, null, id)
                PendingSpeechQueue.Decision.DROP -> fireDone(id)
                PendingSpeechQueue.Decision.BUFFER -> Unit // flushed once the engine is ready
            }
        }

        override fun stop() {
            tts.stop()
            // Drop anything still buffered so a later init doesn't speak stale, cancelled text.
            queue.drainBuffered()
            callbacks.keys.toList().forEach { id -> fireDone(id) }
        }

        override fun release() {
            queue.drainBuffered()
            tts.stop()
            tts.shutdown()
        }

        /** Fires (and unregisters) the completion callback for [id], on the main thread. */
        private fun fireDone(id: String) {
            callbacks.remove(id)?.second?.let { cb -> postMain { cb() } }
        }

        private fun releaseBuffered(items: List<Pending>) = items.forEach { fireDone(it.id) }

        /** Posts [block] to the main thread (lambda literal so it SAM-converts to Runnable). */
        private fun postMain(block: () -> Unit) {
            main.post { block() }
        }

        private companion object {
            const val INIT_TIMEOUT_MS = 5_000L
        }
    }
