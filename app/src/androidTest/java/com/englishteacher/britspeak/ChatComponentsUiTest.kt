package com.englishteacher.britspeak

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.englishteacher.britspeak.ui.chat.MessageBubble
import com.englishteacher.britspeak.ui.theme.BritSpeakTheme
import com.englishteacher.core.domain.model.ChatMessage
import com.englishteacher.core.domain.model.Correction
import com.englishteacher.core.domain.model.CorrectionType
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.Speaker
import org.junit.Rule
import org.junit.Test

class ChatComponentsUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun tutorMessageShowsTextAndCorrection() {
        val message =
            ChatMessage(
                id = "m1",
                speaker = Speaker.TUTOR,
                text = "Lovely choice!",
                timestampMillis = 0,
                corrections =
                    listOf(
                        Correction(
                            original = "I want soup",
                            corrected = "I'd like the soup, please",
                            type = CorrectionType.NATURALNESS,
                            explanationEn = "More polite.",
                            explanationZh = "更礼貌。",
                        ),
                    ),
                repeatTarget = "I'd like the soup, please.",
            )

        composeRule.setContent {
            BritSpeakTheme {
                MessageBubble(message = message, feedbackLanguage = FeedbackLanguage.ENGLISH)
            }
        }

        composeRule.onNodeWithText("Lovely choice!").assertIsDisplayed()
        composeRule.onNodeWithText("More polite.").assertIsDisplayed()
        composeRule.onNodeWithText("✅ I'd like the soup, please").assertIsDisplayed()
    }
}
