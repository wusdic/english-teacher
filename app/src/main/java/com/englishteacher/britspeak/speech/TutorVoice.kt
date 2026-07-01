package com.englishteacher.britspeak.speech

/** Text-to-speech port for the tutor's British voice. Abstracted so ViewModels stay testable. */
interface TutorVoice {
    /**
     * Speaks [text] in British English. [onStart] fires when audio begins, [onDone] when it
     * finishes (or fails). Callbacks are delivered on the main thread.
     */
    fun speak(
        text: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
    )

    /**
     * Same as [speak] but appends to whatever is currently queued instead of interrupting it —
     * used to play a streamed reply's sentences back-to-back without cutting each other off.
     * Defaults to [speak] for implementations that don't support queuing.
     */
    fun enqueue(
        text: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
    ) = speak(text, onStart, onDone)

    fun stop()

    fun release()
}
