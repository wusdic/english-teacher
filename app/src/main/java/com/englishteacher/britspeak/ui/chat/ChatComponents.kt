package com.englishteacher.britspeak.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.englishteacher.core.domain.model.ChatMessage
import com.englishteacher.core.domain.model.Correction
import com.englishteacher.core.domain.model.CorrectionType
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.Speaker
import com.englishteacher.core.usecase.RepeatScore

@Composable
fun MessageBubble(
    message: ChatMessage,
    feedbackLanguage: FeedbackLanguage,
    modifier: Modifier = Modifier,
) {
    val isUser = message.speaker == Speaker.USER
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color =
                if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor =
                if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            shape =
                RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp,
                ),
            tonalElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        message.corrections.forEach { correction ->
            CorrectionCard(correction = correction, feedbackLanguage = feedbackLanguage)
        }
    }
}

@Composable
fun CorrectionCard(
    correction: Correction,
    feedbackLanguage: FeedbackLanguage,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeChip(correction.type)
            }
            Text(
                text = "❌ ${correction.original}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB00020),
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = "✅ ${correction.corrected}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1B7A3D),
            )
            Text(
                text = correction.explanationFor(feedbackLanguage),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TypeChip(type: CorrectionType) {
    val label =
        when (type) {
            CorrectionType.GRAMMAR -> "Grammar"
            CorrectionType.VOCABULARY -> "Vocabulary"
            CorrectionType.PRONUNCIATION -> "Pronunciation"
            CorrectionType.NATURALNESS -> "Naturalness"
        }
    Surface(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatScorePanel(
    score: RepeatScore,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    val color = if (score.passed) Color(0xFF1B7A3D) else Color(0xFFB26A00)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Make it unmistakably the跟读 (repeat-after-me) result, not a chat reply.
                    Text(
                        text = "跟读练习 · Repeat practice",
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                    Text(
                        text =
                            if (score.passed) {
                                "读得很棒！  ${score.score}%"
                            } else {
                                "继续练习  ${score.score}%（红色是没读准的词）"
                            },
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                    )
                }
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭跟读评分", tint = color)
                    }
                }
            }

            if (score.expectedTokens.isNotEmpty()) {
                Text(
                    text = "要跟读的句子：",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // Highlight which target words were matched vs missed.
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    score.expectedTokens.forEachIndexed { index, token ->
                        val matched = score.matchedExpected.getOrElse(index) { false }
                        Text(
                            text = token,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (matched) Color(0xFF1B7A3D) else Color(0xFFB00020),
                            modifier =
                                Modifier
                                    .background(
                                        if (matched) Color(0xFF1B7A3D).copy(alpha = 0.10f) else Color(0xFFB00020).copy(alpha = 0.10f),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (score.spokenTokens.isNotEmpty()) {
                Text(
                    text = "你说的：${score.spokenTokens.joinToString(" ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
