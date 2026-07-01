package com.englishteacher.core.ai

import com.englishteacher.core.domain.model.Correction
import com.englishteacher.core.domain.model.CorrectionType
import com.englishteacher.core.domain.model.TutorTurn
import com.englishteacher.core.domain.port.TutorEngineException
import kotlinx.serialization.json.Json

/**
 * Turns the model's structured JSON text into a [TutorTurn]. Tolerant of minor model quirks
 * (extra fields, blank repeat target) but fails loudly when the payload is unusable.
 */
class ConversationResponseParser(
    private val json: Json = DEFAULT_JSON,
) {
    /** Parses the JSON string the model produced in its (single) text block. */
    fun parse(structuredJson: String): TutorTurn {
        val raw = structuredJson.trim()

        // Many OpenAI-compatible models (e.g. MiniMax) ignore "JSON only" and wrap the object in
        // prose or omit it entirely. Try the whole payload, then the first {...} substring, and
        // finally fall back to treating the text itself as the spoken reply — so a conversation
        // always continues rather than dying with a parse error.
        val dto =
            tryDecode(raw)
                ?: extractJsonObject(raw)?.let { tryDecode(it) }
                ?: return fallbackTurn(raw)

        if (dto.reply.isBlank()) {
            throw TutorEngineException("Tutor response had an empty reply")
        }

        val corrections =
            dto.corrections
                .filter { it.corrected.isNotBlank() }
                .map { c ->
                    Correction(
                        original = c.original.trim(),
                        corrected = c.corrected.trim(),
                        type = CorrectionType.fromWire(c.type),
                        explanationEn = c.explanationEn.trim(),
                        explanationZh = c.explanationZh.trim(),
                    )
                }

        return TutorTurn(
            reply = dto.reply.trim(),
            corrections = corrections,
            repeatTarget = dto.repeatTarget.trim().ifBlank { null },
        )
    }

    private fun tryDecode(candidate: String): TutorTurnDto? =
        try {
            json.decodeFromString(TutorTurnDto.serializer(), candidate)
        } catch (_: Throwable) {
            null
        }

    /** Returns the outermost `{ … }` block, or null if the text has no JSON object at all. */
    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start in 0 until end) text.substring(start, end + 1) else null
    }

    /**
     * Last resort when the model didn't return usable JSON: strip any stray JSON scaffolding and
     * speak the text as-is (no corrections/repeat target). Only fails if there is truly nothing.
     */
    private fun fallbackTurn(raw: String): TutorTurn {
        val spoken = raw.removeSurrounding("\"").trim()
        if (spoken.isBlank()) {
            throw TutorEngineException("Tutor response was empty")
        }
        return TutorTurn(reply = spoken, corrections = emptyList(), repeatTarget = null)
    }

    companion object {
        val DEFAULT_JSON: Json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }
    }
}
