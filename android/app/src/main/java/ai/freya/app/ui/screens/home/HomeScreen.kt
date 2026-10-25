package ai.freya.app.ui.screens.home

import ai.freya.app.domain.CoreState
import ai.freya.app.domain.Persona
import ai.freya.app.domain.Suggestion
import ai.freya.app.domain.SuggestionAction
import ai.freya.app.domain.ToolPolicy
import ai.freya.app.domain.model.MessageStatus
import ai.freya.app.ui.components.GlassAction
import ai.freya.app.ui.components.GlassPanel
import ai.freya.app.ui.components.StatusChip
import ai.freya.app.ui.components.rememberFreyaHaptics
import ai.freya.app.ui.core.CoreSignals
import ai.freya.app.ui.core.FreyaCore
import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.ui.theme.Motion
import ai.freya.app.ui.theme.Radius
import ai.freya.app.ui.theme.Space
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onOpenControlCenter: () -> Unit = {},
    onOpenPower: () -> Unit = {},
) {
    val vm: HomeViewModel = viewModel()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val messages by vm.messages.collectAsStateWithLifecycle()
    val inFlight by vm.inFlight.collectAsStateWithLifecycle()
    val state by vm.coreState.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val activeTool by vm.activeTool.collectAsStateWithLifecycle()
    val chars by vm.receivedChars.collectAsStateWithLifecycle()
    val ack by vm.acknowledgeTick.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val level by vm.inputLevel.collectAsStateWithLifecycle()
    val wordTick by vm.wordTick.collectAsStateWithLifecycle()
    val wordSynced by vm.wordSynced.collectAsStateWithLifecycle()
    val audible by vm.audible.collectAsStateWithLifecycle()
    val presence by vm.presence.collectAsStateWithLifecycle()
    val hapticsOn by vm.haptics.collectAsStateWithLifecycle()
    val speechRate by vm.speechRate.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val provider by vm.providerConfigured.collectAsStateWithLifecycle()
    val reachable by vm.backendReachable.collectAsStateWithLifecycle()

    val haptics = rememberFreyaHaptics(hapticsOn)
    var conversationMode by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }

    // ---- microphone permission ----
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var hasMic by remember { mutableStateOf(granted()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasMic = granted() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasMic = ok
        if (ok) {
            vm.startListening()
        } else {
            val activity = context as? Activity
            val permanentlyDenied = activity != null &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            scope.launch {
                val r = snackbar.showSnackbar(
                    "Microphone access is off. You can still type to FREYA.",
                    actionLabel = if (permanentlyDenied) "Settings" else null,
                )
                if (r == SnackbarResult.ActionPerformed) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }
            }
        }
    }

    // ---- haptic punctuation on real state changes ----
    LaunchedEffect(state) {
        when (state) {
            CoreState.AWAITING_APPROVAL -> haptics.start()
            CoreState.ERROR -> haptics.reject()
            else -> Unit
        }
    }
    LaunchedEffect(ack) { if (ack > 0) haptics.confirm() }

    val signals = CoreSignals(
        level = vm.inputLevel, wordTick = wordTick, wordSynced = wordSynced, audible = audible,
        receivedChars = chars, acknowledgeTick = ack, speechRate = speechRate,
    )

    val actions = MessageActions(
        onCopy = {
            clipboard.setText(AnnotatedString(it))
            scope.launch { snackbar.showSnackbar("Copied") }
        },
        onRetry = vm::retry,
        onReadAloud = vm::readAloud,
        onFeedback = { id, v ->
            vm.feedback(id, v)
            haptics.tick()
        },
        canSpeak = vm.ttsAvailable,
        busy = inFlight,
    )

    fun onSuggestion(s: Suggestion) {
        haptics.press()
        when (val a = s.action) {
            is SuggestionAction.Send -> { vm.send(a.text); conversationMode = true }
            SuggestionAction.OpenControlCenter -> onOpenControlCenter()
            SuggestionAction.OpenPower -> onOpenPower()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            HomeTopBar(
                state = state,
                conversationMode = conversationMode,
                providerOnline = provider == true,
                reachable = reachable,
                miniCore = {
                    FreyaCore(state = state, signals = signals, presence = presence, modifier = Modifier.size(40.dp))
                },
                onToggleMode = { conversationMode = !conversationMode },
                onSearch = onNavigateToSearch,
                onNewConversation = {
                    vm.newConversation()
                    scope.launch { snackbar.showSnackbar("Started a new conversation") }
                },
                onControl = onOpenControlCenter,
            )

            AnimatedContent(
                targetState = conversationMode,
                transitionSpec = { fadeIn(tween(Motion.layout)) togetherWith fadeOut(tween(Motion.state)) },
                modifier = Modifier.weight(1f),
                label = "homeMode",
            ) { convo ->
                if (!convo) {
                    CoreView(
                        state = state,
                        signals = signals,
                        presence = presence,
                        caption = captionFor(state, partial, chars, activeTool, error),
                        latest = messages.lastOrNull { !it.isUser },
                        showGreeting = messages.isEmpty() && vm.showGreeting,
                        greeting = vm.greeting,
                        onGreetingShown = vm::markGreetingShown,
                        onOpenConversation = { conversationMode = true },
                        onRetry = {
                            messages.lastOrNull { !it.isUser && it.status == MessageStatus.FAILED }?.let { vm.retry(it.id) }
                        },
                        canRetry = messages.lastOrNull { !it.isUser }?.status == MessageStatus.FAILED,
                        onDismissError = vm::dismissError,
                        onCheckConnection = {
                            vm.refreshStatus()
                            onOpenControlCenter()
                        },
                    )
                } else {
                    ConversationView(
                        messages = messages,
                        actions = actions,
                        showGreeting = vm.showGreeting,
                        greeting = vm.greeting,
                        onGreetingShown = vm::markGreetingShown,
                    )
                }
            }

            SuggestionRow(suggestions, ::onSuggestion)

            // ---- live transcription ----
            AnimatedVisibility(state == CoreState.LISTENING) {
                Text(
                    text = partial.ifBlank { "Listening…" },
                    style = MaterialTheme.typography.titleMedium,
                    color = FreyaTheme.colors.cyan,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.xs)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            ControlDock(
                input = input,
                onInput = { input = it },
                onSend = {
                    if (input.isNotBlank()) {
                        vm.send(input)
                        input = ""
                        conversationMode = true
                        haptics.press()
                    }
                },
                talkMode = talkModeFor(state, vm.sttAvailable, hasMic),
                level = level,
                stoppable = inFlight || state == CoreState.SPEAKING || state == CoreState.LISTENING,
                onStop = {
                    haptics.press()
                    if (state == CoreState.LISTENING) vm.cancelListening() else vm.stop()
                },
                busy = inFlight,
                onStartTalk = { vm.startListening() },
                onFinishTalk = { vm.finishListening() },
                onBargeIn = { vm.stopSpeaking(); vm.startListening() },
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onUnavailable = { scope.launch { snackbar.showSnackbar("Speech recognition isn't available on this device. Type instead.") } },
                haptics = haptics,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }
}

private fun captionFor(state: CoreState, partial: String, chars: Int, tool: String?, error: String?): String = when (state) {
    CoreState.IDLE -> "Tap to talk, hold to speak, or type below."
    CoreState.LISTENING -> if (partial.isBlank()) "I'm listening." else "Release or tap when you're done."
    CoreState.PROCESSING -> if (chars == 0) "Waiting for the first words…" else "Receiving · $chars characters"
    CoreState.TOOL_EXECUTION -> "Running: ${ToolPolicy.info(tool ?: "task").title}"
    CoreState.SPEAKING -> "Tap the mic to interrupt, or Stop."
    CoreState.AWAITING_APPROVAL -> "Review the request to continue."
    CoreState.ERROR -> error ?: "Something went wrong."
}

@Composable
private fun HomeTopBar(
    state: CoreState,
    conversationMode: Boolean,
    providerOnline: Boolean,
    reachable: Boolean?,
    miniCore: @Composable () -> Unit,
    onToggleMode: () -> Unit,
    onSearch: () -> Unit,
    onNewConversation: () -> Unit,
    onControl: () -> Unit,
) {
    val c = FreyaTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.xs, top = Space.sm, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(conversationMode) {
            Row { miniCore(); Spacer(Modifier.width(Space.sm)) }
        }
        Column(Modifier.weight(1f)) {
            Text(Persona.NAME, style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = MaterialTheme.typography.headlineSmall.letterSpacing * 6))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(state.label, color = stateChipColor(state))
                Spacer(Modifier.width(6.dp))
                when {
                    reachable == false -> StatusChip("offline", color = c.error)
                    providerOnline -> StatusChip("core online", color = c.success)
                    else -> StatusChip("no provider", color = c.approval)
                }
            }
        }
        IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
        IconButton(onClick = onNewConversation) { Icon(Icons.Outlined.AddComment, contentDescription = "New conversation") }
        IconButton(onClick = onControl) { Icon(Icons.Outlined.Tune, contentDescription = "Control Center") }
        IconButton(onClick = onToggleMode) {
            Icon(
                if (conversationMode) Icons.Outlined.Hub else Icons.Outlined.Forum,
                contentDescription = if (conversationMode) "Show FREYA Core" else "Show conversation",
            )
        }
    }
}

@Composable
private fun stateChipColor(s: CoreState) = FreyaTheme.colors.let { c ->
    when (s) {
        CoreState.IDLE -> c.violet
        CoreState.LISTENING, CoreState.SPEAKING -> c.cyan
        CoreState.PROCESSING -> c.violet
        CoreState.TOOL_EXECUTION -> c.ice
        CoreState.AWAITING_APPROVAL -> c.approval
        CoreState.ERROR -> c.error
    }
}

@Composable
private fun CoreView(
    state: CoreState,
    signals: CoreSignals,
    presence: ai.freya.app.ui.core.Presence,
    caption: String,
    latest: ai.freya.app.domain.model.ChatMessage?,
    showGreeting: Boolean,
    greeting: String,
    onGreetingShown: () -> Unit,
    onOpenConversation: () -> Unit,
    onRetry: () -> Unit,
    canRetry: Boolean,
    onDismissError: () -> Unit,
    onCheckConnection: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = Space.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = min(min(maxWidth * 0.92f, maxHeight), 380.dp)
            FreyaCore(state = state, signals = signals, presence = presence, modifier = Modifier.size(side))
        }
        Text(
            state.label,
            style = MaterialTheme.typography.headlineSmall,
            color = stateChipColor(state),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (state == CoreState.ERROR) {
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (canRetry) GlassAction("Retry", Icons.Outlined.Refresh, onRetry, tint = FreyaTheme.colors.cyan)
                GlassAction("Check connection", null, onCheckConnection)
                GlassAction("Dismiss", null, onDismissError)
            }
        }
        Spacer(Modifier.height(Space.md))
        when {
            showGreeting -> {
                GlassPanel(Modifier.fillMaxWidth(), contentPadding = Space.md) {
                    Text(Persona.GREETING_TITLE, style = MaterialTheme.typography.labelMedium, color = FreyaTheme.colors.cyan)
                    Spacer(Modifier.height(4.dp))
                    Text(greeting, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                LaunchedEffect(Unit) { onGreetingShown() }
            }
            latest != null -> LatestReplyCard(latest, onOpenConversation)
        }
        Spacer(Modifier.height(Space.sm))
    }
}

@Composable
private fun ConversationView(
    messages: List<ai.freya.app.domain.model.ChatMessage>,
    actions: MessageActions,
    showGreeting: Boolean,
    greeting: String,
    onGreetingShown: () -> Unit,
) {
    val listState = rememberLazyListState()
    val lastContentLength = messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(messages.size, lastContentLength) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        if (messages.isEmpty()) {
            item(key = "empty") {
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text(if (showGreeting) Persona.GREETING_TITLE else "NEW CONVERSATION", style = MaterialTheme.typography.labelMedium, color = FreyaTheme.colors.cyan)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (showGreeting) greeting else "Ask anything by voice or text. Replies stream in here with sources, task results and details.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (showGreeting) LaunchedEffect(Unit) { onGreetingShown() }
            }
        }
        items(messages, key = { it.id }) { MessageItem(it, actions) }
    }
}

@Composable
private fun SuggestionRow(suggestions: List<Suggestion>, onClick: (Suggestion) -> Unit) {
    AnimatedVisibility(suggestions.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(vertical = Space.xs),
        ) {
            items(suggestions, key = { it.label }) { s ->
                GlassAction(s.label, null, { onClick(s) })
            }
        }
    }
}

@Composable
private fun ControlDock(
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    talkMode: TalkMode,
    level: Float,
    stoppable: Boolean,
    onStop: () -> Unit,
    busy: Boolean,
    onStartTalk: () -> Unit,
    onFinishTalk: () -> Unit,
    onBargeIn: () -> Unit,
    onRequestPermission: () -> Unit,
    onUnavailable: () -> Unit,
    haptics: ai.freya.app.ui.components.FreyaHaptics,
) {
    val c = FreyaTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.lg, top = Space.xs, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Text input alternative.
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .clip(Radius.pill)
                .background(c.glassFill)
                .border(1.dp, c.glassBorder, Radius.pill)
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                if (input.isEmpty()) {
                    Text("Message FREYA…", style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(c.cyan),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!busy) onSend() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(input.isNotBlank()) {
                IconButton(onClick = onSend, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (busy) c.textMuted else c.cyan)
                }
            }
        }
        Spacer(Modifier.width(Space.md))
        PushToTalkButton(
            mode = talkMode,
            level = level,
            haptics = haptics,
            onStart = onStartTalk,
            onFinish = onFinishTalk,
            onBargeIn = onBargeIn,
            onRequestPermission = onRequestPermission,
            onUnavailable = onUnavailable,
            size = 72.dp,
        )
        AnimatedVisibility(stoppable) {
            Row {
                Spacer(Modifier.width(Space.sm))
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(Radius.pill)
                        .background(c.error.copy(alpha = 0.14f))
                        .border(1.dp, c.error.copy(alpha = 0.5f), Radius.pill)
                        .clickable(onClick = onStop),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = "Stop", tint = c.error)
                }
            }
        }
    }
}
