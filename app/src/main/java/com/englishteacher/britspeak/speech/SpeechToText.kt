package com.englishteacher.britspeak.speech

/** Callbacks for streaming speech recognition. All delivered on the main thread. */
interface SttCallback {
    fun onReady() {}

    fun onPartial(text: String) {}

    fun onResult(text: String) {}

    fun onEndOfSpeech() {}

    fun onError(message: String) {}
}

/** Speech-to-text port. Abstracted so ViewModels can be unit-tested with a fake recogniser. */
interface SpeechToText {
    val isAvailable: Boolean

    fun startListening(
        localeTag: String,
        callback: SttCallback,
    )

    fun stopListening()

    fun release()
}
