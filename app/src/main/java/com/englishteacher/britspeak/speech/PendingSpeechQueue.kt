package com.englishteacher.britspeak.speech

/**
 * Pure, thread-safe state machine for utterances requested before a speech engine has finished
 * initialising. Extracted from [AndroidTutorVoice] so the buffering/flush ordering — the part
 * with subtle edge cases — is unit-testable without the Android framework.
 *
 * Lifecycle: items [submit]ted while still initialising are buffered; [markReady] returns them in
 * submission order to be spoken, and [markFailed] returns them to be released (their completion
 * callbacks fired) so callers never hang. Both transitions are one-shot and mutually exclusive.
 */
class PendingSpeechQueue<T> {
    enum class Decision {
        /** Engine is ready — speak this item now. */
        SPEAK,

        /** Still initialising — the item has been buffered and will come back from [markReady]. */
        BUFFER,

        /** Initialisation failed — the item cannot be spoken; release it. */
        DROP,
    }

    private val lock = Any()
    private val buffered = mutableListOf<T>()
    private var ready = false
    private var failed = false

    /** Decides what to do with a newly-requested [item], buffering it if still initialising. */
    fun submit(item: T): Decision =
        synchronized(lock) {
            when {
                ready -> Decision.SPEAK
                failed -> Decision.DROP
                else -> {
                    buffered.add(item)
                    Decision.BUFFER
                }
            }
        }

    /** Transition to ready (once); returns the buffered items to speak, in submission order. */
    fun markReady(): List<T> =
        synchronized(lock) {
            if (ready || failed) return emptyList()
            ready = true
            buffered.toList().also { buffered.clear() }
        }

    /** Transition to failed (once); returns the buffered items to release so callers don't hang. */
    fun markFailed(): List<T> =
        synchronized(lock) {
            if (ready || failed) return emptyList()
            failed = true
            buffered.toList().also { buffered.clear() }
        }

    /** Drops and returns anything still buffered (e.g. on stop) without changing ready/failed. */
    fun drainBuffered(): List<T> =
        synchronized(lock) {
            buffered.toList().also { buffered.clear() }
        }

    /** True once [markReady] or [markFailed] has been applied. */
    fun isSettled(): Boolean = synchronized(lock) { ready || failed }
}
