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
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.englishteacher.britspeak.ui.avatar.DigitalHumanAvatar
import com.englishteacher.britspeak.ui.avatar.avatarPortraitFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Tap-the-avatar → pick a photo → save it locally → re-render as the animated character.
    var avatarVersion by remember { mutableIntStateOf(0) }
    val avatarPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val ok =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    avatarPortraitFile(context).outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                true
                            }.getOrDefault(false)
                        }
                    if (ok) {
                        avatarVersion++
                    } else {
                        snackbarHostState.showSnackbar("图片保存失败，请重试")
                    }
                }
            }
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
                imageVersion = avatarVersion,
                onClick = {
                    avatarPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
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

            if (state.sttLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(6.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = "  离线语音模型首次加载中，请稍候…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (!state.hasApiKey) {
                Text(
                    text = "Add your API key in Settings to start chatting.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp),
                )
            }

            // Optional bottom subtitles.
            if (state.subtitlesEnabled && state.subtitle.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = state.subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            ControlBar(
                state = state,
                onToggleSubtitles = viewModel::toggleSubtitles,
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
    onToggleSubtitles: () -> Unit,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleLanguage) {
                Icon(Icons.Filled.Translate, contentDescription = "Toggle correction language")
            }
            IconButton(onClick = onToggleSubtitles) {
                Icon(
                    if (state.subtitlesEnabled) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                    contentDescription = "Toggle subtitles",
                )
            }
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
