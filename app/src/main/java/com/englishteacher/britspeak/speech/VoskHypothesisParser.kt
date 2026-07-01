package com.englishteacher.britspeak.speech

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Pure parser for Vosk hypothesis JSON (e.g. `{"partial":"..."}` or `{"text":"..."}`).
 * Kept free of Android APIs so it can be unit-tested on the JVM.
 */
object VoskHypothesisParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns the trimmed value for [key], or null if absent/blank/unparseable. */
    fun extract(
        hypothesis: String?,
        key: String,
    ): String? {
        if (hypothesis.isNullOrBlank()) return null
        return try {
            val value = json.parseToJsonElement(hypothesis).jsonObject[key]
            (value as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Throwable) {
            null
        }
    }
}
