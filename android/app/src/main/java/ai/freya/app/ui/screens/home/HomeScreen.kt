package ai.freya.app.ui.screens.home

import ai.freya.app.domain.Persona
import ai.freya.app.domain.model.ChatMessage
import ai.freya.app.ui.components.FreyaEmblem
import ai.freya.app.ui.components.HolographicAvatar
import ai.freya.app.voice.VoiceState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
) {
    val vm: HomeViewModel = viewModel()
    val messages by vm.messages.collectAsStateWithLifecycle(initialValue = emptyList())
    val streaming by vm.streaming.collectAsStateWithLifecycle(initialValue = false)
    val error by vm.error.collectAsStateWithLifecycle()
    val voiceState by vm.voiceState.collectAsStateWithLifecycle(initialValue = VoiceState.IDLE)
    val coreOnline by vm.coreOnline.collectAsStateWithLifecycle(initialValue = false)

    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, streaming) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            HomeTopBar(
                voiceState = voiceState,
                coreOnline = coreOnline,
                onSearch = onNavigateToSearch,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (vm.showGreeting) {
                    item(key = "greeting") {
                        GreetingCard(greeting = vm.greeting)
                        LaunchedEffect(Unit) { vm.markGreetingShown() }
                    }
                }
                items(messages) { message ->
                    MessageBubble(message)
                }
                if (streaming) {
                    item(key = "streaming") {
                        Text(
                            text = "…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (error != null) {
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            ChatInputBar(
                value = input,
                onValueChange = { input = it },
                onSend = {
                    vm.send(input)
                    input = ""
                },
                listening = voiceState == VoiceState.LISTENING,
                onMic = { vm.toggleListening() },
                busy = streaming,
                onStop = { vm.interrupt() },
                micEnabled = vm.sttAvailable,
            )
        }
    }
}

@Composable
private fun HomeTopBar(
    voiceState: VoiceState,
    coreOnline: Boolean,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HolographicAvatar(state = voiceState, modifier = Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Persona.NAME, style = MaterialTheme.typography.titleLarge)
            Text(
                text = voiceStateLabel(voiceState),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CoreIndicator(online = coreOnline)
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onSearch) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
            )
        }
    }
}

@Composable
private fun CoreIndicator(online: Boolean) {
    val accent = if (online) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (online) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
        )
        FreyaEmblem(
            modifier = Modifier
                .size(16.dp)
                .alpha(if (online) 1f else 0.45f),
            contentDescription = if (online) "Core online" else "Core offline",
        )
    }
}

@Composable
private fun GreetingCard(greeting: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = Persona.GREETING_TITLE,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = greeting,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        val shape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (isUser) 18.dp else 4.dp,
            bottomEnd = if (isUser) 4.dp else 18.dp,
        )
        Box(
            modifier = Modifier
                .clip(shape)
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    listening: Boolean,
    onMic: () -> Unit,
    busy: Boolean,
    onStop: () -> Unit,
    micEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (micEnabled) {
            IconButton(onClick = onMic) {
                Icon(
                    imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (listening) "Stop listening" else "Voice input",
                    tint = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message FREYA…") },
            maxLines = 4,
        )
        Spacer(Modifier.width(6.dp))
        if (busy) {
            IconButton(onClick = onStop) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Stop",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            IconButton(onClick = onSend, enabled = value.isNotBlank()) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun voiceStateLabel(state: VoiceState): String = when (state) {
    VoiceState.IDLE -> "Idle"
    VoiceState.LISTENING -> "Listening"
    VoiceState.THINKING -> "Thinking"
    VoiceState.SPEAKING -> "Speaking"
    VoiceState.TASK_EXECUTION -> "Executing task"
}
