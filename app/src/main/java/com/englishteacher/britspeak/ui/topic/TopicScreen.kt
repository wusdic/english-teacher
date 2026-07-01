package com.englishteacher.britspeak.ui.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
