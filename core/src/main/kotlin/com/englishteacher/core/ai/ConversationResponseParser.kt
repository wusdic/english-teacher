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
        val dto =
            try {
                json.decodeFromString(TutorTurnDto.serializer(), structuredJson)
            } catch (t: Throwable) {
                throw TutorEngineException("Could not parse tutor response JSON", t)
            }

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

    companion object {
        val DEFAULT_JSON: Json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }
    }
}
