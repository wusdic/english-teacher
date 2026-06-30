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

    fun stop()

    fun release()
}
