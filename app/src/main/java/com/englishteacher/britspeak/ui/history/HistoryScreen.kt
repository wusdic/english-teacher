package com.englishteacher.britspeak.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishteacher.core.domain.model.Speaker

@Composable
fun HistoryScreen(
    onOpen: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    if (sessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No conversations yet. Pick a topic to begin!", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(sessions, key = { it.id }) { session ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onOpen(session.id) },
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column {
                        Text(session.title, style = MaterialTheme.typography.titleMedium)
                        val last = session.messages.lastOrNull { it.speaker == Speaker.TUTOR }?.text
                            ?: session.messages.lastOrNull()?.text.orEmpty()
                        Text(
                            text = last,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp),
                            maxLines = 2,
                        )
                        Text(
                            text = "${session.messages.size} messages",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.delete(session.id) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete conversation")
                    }
                }
            }
        }
    }
}
