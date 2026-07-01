package com.englishteacher.britspeak.ui.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.englishteacher.core.domain.model.Topic

@Composable
fun TopicScreen(
    onTopicSelected: (String) -> Unit,
    viewModel: TopicViewModel = hiltViewModel(),
) {
    val ui = viewModel.uiState
    var showCustomDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
        item {
            Text(
                text = "Topic of the day",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            TopicCard(
                topic = ui.topicOfTheDay,
                featured = true,
                onClick = { onTopicSelected(ui.topicOfTheDay.id) },
            )
            CustomTopicCard(onClick = { showCustomDialog = true })
            Text(
                text = "All topics",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
        }
        items(ui.topics, key = { it.id }) { topic ->
            TopicCard(topic = topic, featured = false, onClick = { onTopicSelected(topic.id) })
        }
    }

    if (showCustomDialog) {
        CustomTopicDialog(
            onDismiss = { showCustomDialog = false },
            onStart = { title, background, goal ->
                showCustomDialog = false
                onTopicSelected(viewModel.startCustomTopic(title, background, goal))
            },
        )
    }
}

@Composable
private fun CustomTopicCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "自定义场景",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "自己设定聊天背景和目的，AI 将按你的设定进行练习",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CustomTopicDialog(
    onDismiss: () -> Unit,
    onStart: (title: String, background: String, goal: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var background by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义场景") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = background,
                    onValueChange = { background = it },
                    label = { Text("聊天背景（必填）") },
                    placeholder = { Text("例如：你是一家跨国公司的产品经理，正在向外国客户介绍新产品") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("对话目的（可选）") },
                    placeholder = { Text("例如：说服客户签约") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onStart(title, background, goal) },
                enabled = background.isNotBlank(),
            ) {
                Text("开始对话")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun TopicCard(
    topic: Topic,
    featured: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (featured) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = topic.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (featured) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = topic.description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (featured) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
