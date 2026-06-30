package com.englishteacher.core.domain.model

/**
 * The digital-human tutor's persona. Defaults to a warm British tutor named Emma.
 * [voiceLocale] is a BCP-47 tag handed to the platform TTS engine.
 */
data class Persona(
    val name: String = "Emma",
    val voiceLocale: String = "en-GB",
    val description: String =
        "a warm, encouraging English tutor from London who speaks natural, " +
            "everyday British English",
) {
    companion object {
        val DEFAULT = Persona()
    }
}
