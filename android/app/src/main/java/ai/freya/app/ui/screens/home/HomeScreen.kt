package ai.freya.app.ui.screens.home

import ai.freya.app.domain.CoreState
import ai.freya.app.domain.Persona
import ai.freya.app.domain.Suggestion
import ai.freya.app.domain.SuggestionAction
import ai.freya.app.domain.ToolPolicy
import ai.freya.app.domain.model.ChatMessage
import ai.freya.app.domain.model.MessageKind
import ai.freya.app.domain.model.MessageStatus
import ai.freya.app.ui.components.FreyaHaptics
import ai.freya.app.ui.components.GlassAction
import ai.freya.app.ui.components.GlassPanel
import ai.freya.app.ui.components.NeuStyle
import ai.freya.app.ui.components.neumorphic
import ai.freya.app.ui.components.rememberFreyaHaptics
import ai.freya.app.ui.core.CoreAppearance
import ai.freya.app.ui.core.FreyaCore
import ai.freya.app.ui.theme.DisplayFamily
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onOpenProjects: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
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
    val notice by vm.notice.collectAsStateWithLifecycle()
    val wordTick by vm.wordTick.collectAsStateWithLifecycle()
    val outputMeasured by vm.outputMeasured.collectAsStateWithLifecycle()
    val hapticsOn by vm.haptics.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val provider by vm.providerConfigured.collectAsStateWithLifecycle()
    val reachable by vm.backendReachable.collectAsStateWithLifecycle()
    val model by vm.activeModel.collectAsStateWithLifecycle()

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

    // ---- the one audio envelope the visuals use: mic while listening, voice while speaking ----
    val audioLevel: () -> Float = {
        when (vm.coreState.value) {
            CoreState.LISTENING -> vm.inputLevel.value
            CoreState.SPEAKING -> vm.outputLevel.value
            else -> 0f
        }
    }
    val measured = state == CoreState.LISTENING || (state == CoreState.SPEAKING && outputMeasured)
    // Real spectrum of FREYA's voice, only while measured playback is running.
    val audioBands: () -> FloatArray? = {
        if (vm.coreState.value == CoreState.SPEAKING && vm.outputMeasured.value) vm.outputBands else null
    }

    val actions = MessageActions(
        onCopy = {
            clipboard.setText(AnnotatedString(it))
            scope.launch { snackbar.showSnackbar("Copied") }
        },
        onRetry = vm::retry,
        onReadAloud = vm::readAloud,
        onFeedback = { id, v -> vm.feedback(id, v); haptics.tick() },
        canSpeak = vm.ttsAvailable,
        busy = inFlight,
    )

    fun onSuggestion(s: Suggestion) {
        haptics.press()
        when (val a = s.action) {
            is SuggestionAction.Send -> { vm.send(a.text); conversationMode = true }
            SuggestionAction.OpenControlCenter -> onOpenControlCenter()
            SuggestionAction.OpenPower -> onOpenPower()
            SuggestionAction.OpenProjects -> onOpenProjects()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            // The waveform owns the whole upper region (≈60% of the usable screen on a phone):
            // the wordmark and status sit on top of it, the controls below it.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Fully qualified: inside Box-in-Column the ColumnScope overload would be picked.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !conversationMode,
                    enter = fadeIn(tween(Motion.layout)),
                    exit = fadeOut(tween(Motion.state)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    FreyaCore(
                        state = state,
                        audioLevel = audioLevel,
                        audioMeasured = measured,
                        audioBands = audioBands,
                        wordTick = wordTick,
                        receivedChars = chars,
                        acknowledgeTick = ack,
                        modifier = Modifier.fillMaxSize().padding(top = 28.dp),
                    )
                }
                Column(Modifier.fillMaxSize()) {
                    HomeHeader(
                        conversationMode = conversationMode,
                        connection = when {
                            reachable == false -> Connection.OFFLINE
                            provider == true -> Connection.ONLINE
                            reachable == true -> Connection.NO_PROVIDER
                            else -> Connection.CHECKING
                        },
                        model = model,
                        miniCore = {
                            FreyaCore(
                                state = state, audioLevel = audioLevel, audioMeasured = measured, audioBands = audioBands,
                                wordTick = wordTick, receivedChars = chars, acknowledgeTick = ack,
                                appearance = CoreAppearance.Compact, compact = true, modifier = Modifier.size(40.dp),
                            )
                        },
                        onToggleConversation = { conversationMode = !conversationMode },
                        onNewConversation = {
                            vm.newConversation()
                            scope.launch { snackbar.showSnackbar("Started a new conversation") }
                        },
                        onSearch = onNavigateToSearch,
                        onSettings = onOpenSettings,
                    )

                    AnimatedContent(
                        targetState = conversationMode,
                        transitionSpec = { fadeIn(tween(Motion.layout)) togetherWith fadeOut(tween(Motion.state)) },
                        modifier = Modifier.weight(1f),
                        label = "homeMode",
                    ) { convo ->
                        if (!convo) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                                StatusBlock(
                                    state = state,
                                    caption = if (state == CoreState.IDLE && notice != null) notice!! else captionFor(state, partial, chars, activeTool, error, measured),
                                    latest = messages.lastOrNull { !it.isUser },
                                    onOpenConversation = { conversationMode = true },
                                    canRetry = messages.lastOrNull { !it.isUser }
                                        ?.let { it.status == MessageStatus.FAILED && it.kind == MessageKind.TEXT } == true,
                                    onRetry = {
                                        messages.lastOrNull { !it.isUser }
                                            ?.takeIf { it.status == MessageStatus.FAILED && it.kind == MessageKind.TEXT }
                                            ?.let { vm.retry(it.id) }
                                    },
                                    onCheckConnection = { vm.refreshStatus(); onOpenControlCenter() },
                                    onDismissError = vm::dismissError,
                                )
                            }
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
                }
            }

            QuickActions(suggestions, ::onSuggestion)

            CommandBar(
                input = input,
                onInput = { input = it },
                busy = inFlight,
                listening = state == CoreState.LISTENING,
                partial = partial,
                onSend = {
                    if (input.isNotBlank()) {
                        vm.send(input)
                        input = ""
                        conversationMode = true
                        haptics.press()
                    }
                },
                onClear = { input = "" },
                onAttachToProject = onOpenProjects,
            )

            VoiceDock(
                talkMode = talkModeFor(state, vm.sttAvailable, hasMic),
                stoppable = inFlight || state == CoreState.SPEAKING || state == CoreState.LISTENING,
                onStop = {
                    haptics.press()
                    if (state == CoreState.LISTENING) vm.cancelListening() else vm.stop()
                },
                onStartTalk = { vm.startListening() },
                onFinishTalk = { vm.finishListening() },
                onBargeIn = { vm.stopSpeaking(); vm.startListening() },
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onUnavailable = { scope.launch { snackbar.showSnackbar("Speech recognition isn't available on this device. Type instead.") } },
                haptics = haptics,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
    }
}

private fun captionFor(state: CoreState, partial: String, chars: Int, tool: String?, error: String?, measured: Boolean): String = when (state) {
    CoreState.IDLE -> "Tap the mic to talk, hold to speak, or type."
    CoreState.LISTENING -> partial.ifBlank { "Go ahead — I'm listening." }
    CoreState.PROCESSING -> if (chars == 0) "Working out a reply…" else "Receiving · $chars characters"
    CoreState.TOOL_EXECUTION -> ToolPolicy.info(tool ?: "task").activeLabel
    CoreState.SPEAKING -> if (measured) "Voice-synced · tap the mic to interrupt" else "Tap the mic to interrupt"
    CoreState.AWAITING_APPROVAL -> "Review the request to continue."
    CoreState.ERROR -> error ?: "Something went wrong."
}

private enum class Connection { ONLINE, NO_PROVIDER, OFFLINE, CHECKING }

@Composable
private fun HomeHeader(
    conversationMode: Boolean,
    connection: Connection,
    model: String?,
    miniCore: @Composable () -> Unit,
    onToggleConversation: () -> Unit,
    onNewConversation: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = FreyaTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().padding(horizontal = Space.xs)) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleConversation) {
                    Icon(
                        if (conversationMode) Icons.Outlined.Hub else Icons.Outlined.Forum,
                        contentDescription = if (conversationMode) "Show FREYA Core" else "Show conversation",
                    )
                }
                AnimatedVisibility(conversationMode) { miniCore() }
            }
            Text(
                Persona.NAME,
                style = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(400), fontSize = 22.sp, letterSpacing = 9.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(Modifier.align(Alignment.CenterEnd)) {
                if (conversationMode) {
                    IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Search") }
                    IconButton(onClick = onNewConversation) { Icon(Icons.Outlined.AddComment, contentDescription = "New conversation") }
                } else {
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                }
            }
        }
        // Compact connection + model indicator.
        val (dot, label) = when (connection) {
            Connection.ONLINE -> c.success to "ONLINE"
            Connection.NO_PROVIDER -> c.approval to "NO PROVIDER"
            Connection.OFFLINE -> c.error to "OFFLINE"
            Connection.CHECKING -> c.textMuted to "CONNECTING"
        }
        Row(
            Modifier
                .neumorphic(Radius.pill, NeuStyle.Flat, c.surface, c.sunken, c.neuHighlight, c.neuShadow)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Text(
                label + (model?.let { "  •  ${it.uppercase()}" } ?: ""),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StatusBlock(
    state: CoreState,
    caption: String,
    latest: ChatMessage?,
    onOpenConversation: () -> Unit,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onCheckConnection: () -> Unit,
    onDismissError: () -> Unit,
) {
    val c = FreyaTheme.colors
    val tint = when (state) {
        CoreState.AWAITING_APPROVAL -> c.approval
        CoreState.ERROR -> c.error
        CoreState.LISTENING -> c.cyan
        else -> MaterialTheme.colorScheme.onBackground
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state == CoreState.LISTENING) {
                // Clear microphone-active indicator (in addition to Android's own privacy dot).
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.error))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                state.label.uppercase(),
                style = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(300), fontSize = 15.sp, letterSpacing = 5.sp),
                color = tint,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (state == CoreState.LISTENING) {
                Spacer(Modifier.width(8.dp))
                Text("MIC ON", style = MaterialTheme.typography.labelSmall, color = c.error)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (state == CoreState.ERROR) {
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (canRetry) GlassAction("Retry", Icons.Outlined.Refresh, onRetry, tint = c.cyan)
                GlassAction("Check connection", null, onCheckConnection)
                GlassAction("Dismiss", null, onDismissError)
            }
        } else if (latest != null && (state == CoreState.IDLE)) {
            Spacer(Modifier.height(Space.sm))
            Text(
                text = when {
                    latest.status == MessageStatus.FAILED -> latest.error ?: "Failed"
                    latest.kind == MessageKind.TOOL_RESULT -> ToolPolicy.info(latest.toolName ?: "task").title + " — done"
                    else -> latest.content
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(Radius.chip)
                    .clickable(onClick = onOpenConversation)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(Space.sm))
    }
}

@Composable
private fun ConversationView(
    messages: List<ChatMessage>,
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

private fun iconFor(action: SuggestionAction, label: String): ImageVector = when (action) {
    SuggestionAction.OpenProjects -> Icons.Outlined.Folder
    SuggestionAction.OpenControlCenter -> Icons.Outlined.Layers
    SuggestionAction.OpenPower -> Icons.Outlined.Bolt
    is SuggestionAction.Send -> if (label.startsWith("Plan") || label.startsWith("Wrap") || label.startsWith("Unblock")) Icons.Outlined.WbSunny else Icons.Outlined.AutoAwesome
}

@Composable
private fun QuickActions(suggestions: List<Suggestion>, onClick: (Suggestion) -> Unit) {
    AnimatedVisibility(suggestions.isNotEmpty()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter, vertical = Space.xs),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            suggestions.forEach { s -> GlassAction(s.label, iconFor(s.action, s.label), { onClick(s) }, tint = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

@Composable
private fun CommandBar(
    input: String,
    onInput: (String) -> Unit,
    busy: Boolean,
    listening: Boolean,
    partial: String,
    onSend: () -> Unit,
    onClear: () -> Unit,
    onAttachToProject: () -> Unit,
) {
    val c = FreyaTheme.colors
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.xs)
            .heightIn(min = 54.dp)
            .neumorphic(Radius.pill, NeuStyle.Sunken, c.surface, c.sunken, c.neuHighlight, c.neuShadow)
            .padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
            if (input.isEmpty()) {
                Text(
                    if (listening) partial.ifBlank { "Listening…" } else "Talk to FREYA…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (listening) c.cyan else c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
        if (input.isNotEmpty()) {
            IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = c.textMuted) }
            IconButton(onClick = onSend, enabled = !busy) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (busy) c.textMuted else c.cyan)
            }
        } else {
            Box {
                IconButton(onClick = { menu = true }) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(c.glassFillStrong), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Add, contentDescription = "Attach", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    // Chat itself doesn't accept files yet; documents go to a project, which FREYA's tools can use.
                    DropdownMenuItem(
                        text = { Text("Upload documents to a project") },
                        leadingIcon = { Icon(Icons.Outlined.UploadFile, contentDescription = null) },
                        onClick = { menu = false; onAttachToProject() },
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceDock(
    talkMode: TalkMode,
    stoppable: Boolean,
    onStop: () -> Unit,
    onStartTalk: () -> Unit,
    onFinishTalk: () -> Unit,
    onBargeIn: () -> Unit,
    onRequestPermission: () -> Unit,
    onUnavailable: () -> Unit,
    haptics: FreyaHaptics,
) {
    val c = FreyaTheme.colors
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        PushToTalkButton(
            mode = talkMode,
            level = 0f,
            haptics = haptics,
            onStart = onStartTalk,
            onFinish = onFinishTalk,
            onBargeIn = onBargeIn,
            onRequestPermission = onRequestPermission,
            onUnavailable = onUnavailable,
            size = 80.dp,
        )
        AnimatedVisibility(stoppable, modifier = Modifier.align(Alignment.CenterEnd).padding(end = Space.xl)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(c.error.copy(alpha = 0.14f))
                    .border(1.dp, c.error.copy(alpha = 0.5f), CircleShape)
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = "Stop", tint = c.error)
            }
        }
    }
}
