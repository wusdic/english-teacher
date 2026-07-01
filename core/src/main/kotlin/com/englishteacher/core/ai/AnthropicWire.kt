package com.englishteacher.core.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** A single message in the Anthropic `messages` array. */
@Serializable
internal data class WireMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class WireOutputFormat(
    val type: String = "json_schema",
    val schema: JsonObject,
)

@Serializable
internal data class WireOutputConfig(
    val format: WireOutputFormat,
)

/** The request body POSTed to `/v1/messages`. */
@Serializable
internal data class WireRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<WireMessage>,
    @SerialName("output_config") val outputConfig: WireOutputConfig,
)

@Serializable
internal data class WireContentBlock(
    val type: String,
    val text: String? = null,
)

/** The response body returned by `/v1/messages`. */
@Serializable
internal data class WireResponse(
    val content: List<WireContentBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
)

@Serializable
internal data class WireErrorEnvelope(
    val error: WireError? = null,
)

@Serializable
internal data class WireError(
    val type: String? = null,
    val message: String? = null,
)

/** The structured payload the model is constrained to emit (mirrors [TutorTurnDto]). */
@Serializable
internal data class TutorTurnDto(
    val reply: String = "",
    val hasErrors: Boolean = false,
    val corrections: List<CorrectionDto> = emptyList(),
    val repeatTarget: String = "",
)

@Serializable
internal data class CorrectionDto(
    val original: String = "",
    val corrected: String = "",
    val type: String = "naturalness",
    val explanationEn: String = "",
    val explanationZh: String = "",
)
