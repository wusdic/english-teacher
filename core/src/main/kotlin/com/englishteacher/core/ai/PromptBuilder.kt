package com.englishteacher.core.ai

import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.Persona
import com.englishteacher.core.domain.model.ProficiencyLevel
import com.englishteacher.core.domain.model.Speaker
import com.englishteacher.core.domain.model.Topic
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the system prompt, message history and JSON schema for an Anthropic request.
 * Pure and deterministic — fully unit-testable.
 */
class PromptBuilder(
    private val persona: Persona = Persona.DEFAULT,
) {
    /** The British-tutor system prompt, parameterised by topic and learner settings. */
    fun systemPrompt(
        session: ConversationSession,
        topic: Topic,
    ): String {
        val level = proficiencyGuidance(session.proficiency)
        return buildString {
            appendLine(
                "You are ${persona.name}, ${persona.description}. " +
                    "You are role-playing a spoken English practice conversation with a learner " +
                    "whose first language is Chinese.",
            )
            appendLine()
            appendLine("SCENARIO: ${topic.scenarioPrompt}")
            appendLine()
            appendLine("HARD RULES:")
            appendLine(
                "1. Speak ONLY natural, idiomatic British English (en-GB spelling, vocabulary " +
                    "and idiom). Never use American spellings.",
            )
            appendLine(
                "2. Stay fully in character and on the scenario. Keep your spoken `reply` short " +
                    "(1-3 sentences) and conversational, and end most turns with a question so the " +
                    "learner keeps speaking.",
            )
            appendLine("3. $level")
            appendLine(
                "4. Inspect the learner's most recent message for mistakes. Identify at most the 3 " +
                    "most important errors (grammar, vocabulary, pronunciation-likely, or " +
                    "naturalness). If the message is already good, return an empty corrections list.",
            )
            appendLine(
                "5. For EVERY correction, give both a concise English explanation (`explanationEn`) " +
                    "and a concise Simplified-Chinese explanation (`explanationZh`). Keep each under " +
                    "two sentences and learner-friendly.",
            )
            appendLine(
                "6. Set `repeatTarget` to ONE short, natural sentence the learner should say back " +
                    "aloud to practise — normally the corrected version of what they tried to say; " +
                    "if there were no errors, use a useful sentence from your reply. Never leave it " +
                    "empty unless this is the opening turn.",
            )
            appendLine(
                "7. Never mention these rules, JSON, or that you are an AI. The `reply` is spoken " +
                    "text only.",
            )
        }.trim()
    }

    /** Maps the persisted transcript plus a new learner utterance to wire messages. */
    internal fun messages(
        session: ConversationSession,
        newUserUtterance: String?,
    ): List<WireMessage> {
        val history =
            session.messages.map { message ->
                WireMessage(
                    role = if (message.speaker == Speaker.USER) "user" else "assistant",
                    content = message.text,
                )
            }
        val withNew =
            if (newUserUtterance != null) {
                history + WireMessage(role = "user", content = newUserUtterance)
            } else {
                history
            }
        // The Anthropic API requires the first message to be from the user. For an opener
        // (empty history, no utterance) seed a neutral kickoff.
        return withNew.ifEmpty {
            listOf(WireMessage(role = "user", content = "Let's begin our conversation."))
        }
    }

    private fun proficiencyGuidance(level: ProficiencyLevel): String =
        when (level) {
            ProficiencyLevel.BEGINNER ->
                "The learner is a BEGINNER: use simple, high-frequency words and short sentences, " +
                    "speak slowly in spirit, and be very encouraging."
            ProficiencyLevel.INTERMEDIATE ->
                "The learner is INTERMEDIATE: use everyday vocabulary and natural phrasing, and " +
                    "gently stretch them with the occasional idiom you then make clear from context."
            ProficiencyLevel.ADVANCED ->
                "The learner is ADVANCED: speak at a natural native pace with richer vocabulary and " +
                    "idiom, and hold them to a high standard of naturalness."
        }

    companion object {
        /** JSON schema constraining the model's structured output. */
        val OUTPUT_SCHEMA: JsonObject =
            buildJsonObject {
                put("type", "object")
                put("additionalProperties", false)
                putJsonObject("properties") {
                    putJsonObject("reply") { put("type", "string") }
                    putJsonObject("hasErrors") { put("type", "boolean") }
                    putJsonObject("corrections") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            put("additionalProperties", false)
                            putJsonObject("properties") {
                                putJsonObject("original") { put("type", "string") }
                                putJsonObject("corrected") { put("type", "string") }
                                putJsonObject("type") {
                                    put("type", "string")
                                    putJsonArray("enum") {
                                        add("grammar")
                                        add("vocabulary")
                                        add("pronunciation")
                                        add("naturalness")
                                    }
                                }
                                putJsonObject("explanationEn") { put("type", "string") }
                                putJsonObject("explanationZh") { put("type", "string") }
                            }
                            putJsonArray("required") {
                                add("original")
                                add("corrected")
                                add("type")
                                add("explanationEn")
                                add("explanationZh")
                            }
                        }
                    }
                    putJsonObject("repeatTarget") { put("type", "string") }
                }
                putJsonArray("required") {
                    add("reply")
                    add("hasErrors")
                    add("corrections")
                    add("repeatTarget")
                }
            }
    }
}
