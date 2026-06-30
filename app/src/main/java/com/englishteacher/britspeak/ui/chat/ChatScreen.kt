package com.englishteacher.britspeak.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishteacher.britspeak.ui.avatar.DigitalHumanAvatar
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    topicId: String?,
    sessionId: String?,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var micGranted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            micGranted = granted
            if (granted) viewModel.startListening()
        }

    LaunchedEffect(topicId, sessionId) {
        when {
            sessionId != null -> viewModel.openSession(sessionId)
            topicId != null -> viewModel.startOnTopic(topicId)
            else -> viewModel.resumeOrStartDaily()
        }
        viewModel.refresh()
    }

    LaunchedEffect(state.session?.messages?.size) {
        val count = state.session?.messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.topicTitle.ifBlank { "Practice with Emma" },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp),
            )

            DigitalHumanAvatar(
                mood = state.avatarMood,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(state.session?.messages.orEmpty(), key = { it.id }) { message ->
                    MessageBubble(message = message, feedbackLanguage = state.feedbackLanguage)
                }
                state.lastRepeatScore?.let { score ->
                    item {
                        Box(modifier = Modifier.padding(12.dp)) {
                            RepeatScorePanel(score = score)
                        }
                    }
                }
            }

            if (state.partialTranscript.isNotBlank()) {
                Text(
                    text = "“${state.partialTranscript}”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp),
                )
            }

            if (!state.hasApiKey) {
                Text(
                    text = "Add your Anthropic API key in Settings to start chatting.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                )
            }

            ControlBar(
                state = state,
                onMic = {
                    if (micGranted) {
                        viewModel.startListening()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStop = viewModel::stopListening,
                onRepeat = {
                    if (micGranted) viewModel.startRepeat() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onToggleLanguage = viewModel::toggleFeedbackLanguage,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ControlBar(
    state: ChatUiState,
    onMic: () -> Unit,
    onStop: () -> Unit,
    onRepeat: () -> Unit,
    onToggleLanguage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleLanguage) {
            Icon(Icons.Filled.Translate, contentDescription = "Toggle correction language")
        }

        Box(contentAlignment = Alignment.Center) {
            when (state.phase) {
                ChatPhase.THINKING -> CircularProgressIndicator(modifier = Modifier.size(64.dp))
                ChatPhase.LISTENING, ChatPhase.REPEATING ->
                    FilledIconButton(
                        onClick = onStop,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "Listening — tap to stop", modifier = Modifier.size(34.dp))
                    }
                else ->
                    FilledIconButton(
                        onClick = onMic,
                        enabled = state.canSpeak,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "Tap to speak", modifier = Modifier.size(34.dp))
                    }
            }
        }

        if (state.pendingRepeat != null) {
            IconButton(onClick = onRepeat, enabled = state.canSpeak) {
                Icon(Icons.Filled.RecordVoiceOver, contentDescription = "Repeat after Emma")
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}
