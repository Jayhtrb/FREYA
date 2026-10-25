package ai.freya.app.ui.screens.control

import ai.freya.app.data.remote.dto.ToolDto
import ai.freya.app.domain.ToolPolicy
import ai.freya.app.domain.ToolRisk
import ai.freya.app.session.ToolRunStatus
import ai.freya.app.ui.components.GlassAction
import ai.freya.app.ui.components.GlassPanel
import ai.freya.app.ui.components.InfoRow
import ai.freya.app.ui.components.SectionLabel
import ai.freya.app.ui.components.StatusChip
import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.ui.theme.MonoFamily
import ai.freya.app.ui.theme.Space
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat

@Composable
fun ControlCenterScreen(
    onBack: (() -> Unit)?,
    onOpenPower: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenJobs: () -> Unit = {},
) {
    val vm: ControlCenterViewModel = viewModel()
    val context = LocalContext.current
    val c = FreyaTheme.colors
    val snackbar = remember { SnackbarHostState() }

    val keys by vm.keys.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val tools by vm.tools.collectAsStateWithLifecycle()
    val toolsError by vm.toolsError.collectAsStateWithLifecycle()
    val memoryCount by vm.memoryCount.collectAsStateWithLifecycle()
    val checks by vm.checks.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val runs by vm.toolRuns.collectAsStateWithLifecycle()
    val activeTool by vm.activeTool.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var mic by remember { mutableStateOf(micGranted()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { mic = micGranted() }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { mic = it }

    var modelEdit by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(model) { if (modelEdit.isBlank()) modelEdit = model.orEmpty() }
    var formFor by remember { mutableStateOf<ToolDto?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(Unit) { vm.runChecks(mic) }

    val nf = remember { NumberFormat.getIntegerInstance() }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("TOOLS & CONTROL", style = MaterialTheme.typography.labelMedium, color = c.cyan)
                        Text("AI systems", style = MaterialTheme.typography.headlineMedium)
                    }
                    IconButton(onClick = { vm.refresh(); vm.runChecks(mic) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh everything")
                    }
                }
            }

            // ---------------------------------------------------- providers
            item {
                SectionLabel("Connected AI providers")
                GlassPanel(Modifier.fillMaxWidth()) {
                    ProviderRow("DeepSeek", "Direct API key", keys?.deepseek?.set)
                    ProviderRow(
                        "OpenAI-compatible endpoint",
                        keys?.llm?.let { if (it.set) "${it.baseUrl ?: "default URL"} · ${it.model ?: "default model"}" else "Any /v1 API: OpenAI, OmniRoute, local servers" }
                            ?: "Any /v1 API: OpenAI, OmniRoute, local servers",
                        keys?.llm?.set,
                    )
                    ProviderRow("Zep memory graph", "Used by simulations", keys?.zep?.set)
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "Keys are stored encrypted on your FREYA backend, never on this phone. To route across many providers, point the OpenAI-compatible endpoint at your OmniRoute gateway (http://<computer-ip>:20128/v1).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.md))
                    GlassAction("Manage keys", Icons.Outlined.Bolt, onOpenPower, tint = c.cyan)
                }
            }

            // ---------------------------------------------------- model & routing
            item {
                SectionLabel("Model selection & routing")
                GlassPanel(Modifier.fillMaxWidth()) {
                    InfoRow("Active model (backend setting)", model ?: "—")
                    Spacer(Modifier.height(Space.sm))
                    OutlinedTextField(
                        value = modelEdit,
                        onValueChange = { modelEdit = it },
                        label = { Text("Model identifier") },
                        supportingText = { Text("Use an exact identifier from your provider's docs. FREYA doesn't hard-code model names.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        GlassAction("Save model", null, { vm.saveModel(modelEdit) }, enabled = modelEdit.isNotBlank() && modelEdit != model, tint = c.cyan)
                    }
                    Spacer(Modifier.height(Space.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Routing strategy", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StatusChip("manual", color = c.violet)
                    }
                    Text(
                        "Requests go to the provider configured above. Automatic, cost-, latency- and privacy-based routing and provider fallback are not built yet — no request is ever switched silently.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------------------------------------------------- integrations
            item {
                SectionLabel("Integration status") {
                    TextButton(onClick = { vm.runChecks(mic) }) { Text("Run checks", color = c.cyan) }
                }
                GlassPanel(Modifier.fillMaxWidth()) {
                    checks.forEach { check ->
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(check.title, style = MaterialTheme.typography.titleSmall)
                                Text(check.method, style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                                Text(check.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(Space.sm))
                            StatusChip(
                                check.status.name.lowercase(),
                                color = when (check.status) {
                                    CheckStatus.OK -> c.success
                                    CheckStatus.WARN -> c.approval
                                    CheckStatus.FAIL -> c.error
                                    CheckStatus.RUNNING -> c.cyan
                                    CheckStatus.UNKNOWN -> c.textMuted
                                },
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------- task execution
            item {
                SectionLabel("Task execution")
                GlassPanel(Modifier.fillMaxWidth()) {
                    Text(
                        "Run a backend tool directly. Read-only tools run immediately; anything that changes data or spends credits asks for approval first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.sm))
                    if (toolsError != null && tools.isEmpty()) {
                        Text("Couldn't load tools: $toolsError", style = MaterialTheme.typography.bodyMedium, color = c.error)
                    }
                    tools.forEach { tool ->
                        val name = tool.name ?: return@forEach
                        val info = ToolPolicy.info(name)
                        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(info.title, style = MaterialTheme.typography.titleSmall)
                                Text(name, style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                                StatusChip(
                                    if (info.risk == ToolRisk.READ_ONLY) "read-only" else "needs approval",
                                    color = if (info.risk == ToolRisk.READ_ONLY) c.success else c.approval,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            GlassAction(
                                if (activeTool == name) "Running…" else "Run",
                                Icons.Outlined.PlayArrow,
                                { formFor = tool },
                                enabled = activeTool == null,
                                tint = c.cyan,
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.sm))
                    GlassAction("Background jobs & notifications", null, onOpenJobs)
                    if (runs.isNotEmpty()) {
                        Spacer(Modifier.height(Space.md))
                        Text("RECENT TASKS (THIS DEVICE)", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                        runs.take(8).forEach { run ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(ToolPolicy.info(run.tool).title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                run.durationMs?.let { Text("$it ms  ", style = MaterialTheme.typography.labelSmall, color = c.textMuted) }
                                StatusChip(
                                    run.status.name.lowercase(),
                                    color = when (run.status) {
                                        ToolRunStatus.RUNNING -> c.ice
                                        ToolRunStatus.SUCCEEDED -> c.success
                                        ToolRunStatus.FAILED -> c.error
                                        ToolRunStatus.DENIED -> c.approval
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------- memory & learning
            item {
                SectionLabel("Memory & learning")
                GlassPanel(Modifier.fillMaxWidth()) {
                    InfoRow("Approved preferences", memoryCount?.toString() ?: "—")
                    InfoRow("Replies marked helpful", nf.format(usage.feedbackUp))
                    InfoRow("Replies marked not helpful", nf.format(usage.feedbackDown))
                    Text(
                        "Ratings stay on this phone. FREYA doesn't change her own behaviour from them automatically; preferences change only when you save them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.md))
                    GlassAction("Review memory", Icons.Outlined.Memory, onOpenMemory, tint = c.cyan)
                }
            }

            // ---------------------------------------------------- usage
            item {
                SectionLabel("Usage & estimated cost")
                GlassPanel(Modifier.fillMaxWidth()) {
                    InfoRow("Chat requests", nf.format(usage.requests))
                    InfoRow("Failed requests", nf.format(usage.failures), valueColor = if (usage.failures > 0) c.approval else MaterialTheme.colorScheme.onSurface)
                    InfoRow("Characters sent / received", "${nf.format(usage.charsSent)} / ${nf.format(usage.charsReceived)}")
                    InfoRow("Tokens (estimate, ≈4 chars each)", nf.format(usage.estimatedTokens))
                    InfoRow("Cost", "Unavailable", valueColor = c.textMuted)
                    Text(
                        "Measured by this app on this device. The backend doesn't report token usage or pricing yet, so no cost is shown rather than a guess. Check your provider's dashboard for billing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.md))
                    GlassAction("Reset counters", Icons.Outlined.Refresh, vm::resetUsage)
                }
            }

            // ---------------------------------------------------- privacy
            item {
                SectionLabel("Privacy & permissions")
                GlassPanel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Microphone", style = MaterialTheme.typography.titleSmall)
                            Text(if (mic) "Allowed — used only while you hold or tap Talk" else "Not allowed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (mic) {
                            GlassAction("Manage", null, {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                            })
                        } else {
                            GlassAction("Allow", null, { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }, tint = c.cyan)
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    PrivacyLine("Stays on this phone", "Appearance and voice settings, usage counters, reply ratings, cached conversation list.")
                    PrivacyLine("Sent to your FREYA backend", "Your messages (voice is sent as text only), tool requests, and keys you enter in Power. Server: ${vm.baseUrl}")
                    PrivacyLine("Sent to the AI provider", "Your backend forwards the conversation text to the provider configured above.")
                    PrivacyLine("Speech recognition", "Handled by Android's recognizer service, which may run on-device or in the cloud depending on your phone's settings. FREYA never records or uploads raw audio.")
                }
            }
            item { Spacer(Modifier.height(Space.xl)) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    formFor?.let { tool ->
        ToolFormDialog(
            tool = tool,
            fields = vm.fieldsFor(tool),
            build = vm::buildArgs,
            onDismiss = { formFor = null },
            onRun = { args ->
                formFor = null
                vm.runTool(tool.name.orEmpty(), args)
            },
        )
    }
}

@Composable
private fun ProviderRow(title: String, subtitle: String, set: Boolean?) {
    val c = FreyaTheme.colors
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusChip(
            when (set) { true -> "configured"; false -> "not set"; null -> "unknown" },
            color = when (set) { true -> c.success; false -> c.textMuted; null -> c.approval },
        )
    }
}

@Composable
private fun PrivacyLine(title: String, body: String) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = FreyaTheme.colors.cyan)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ToolFormDialog(
    tool: ToolDto,
    fields: List<ToolField>,
    build: (List<ToolField>, Map<String, String>) -> Result<kotlinx.serialization.json.JsonObject>,
    onDismiss: () -> Unit,
    onRun: (kotlinx.serialization.json.JsonObject) -> Unit,
) {
    val values = remember(tool) { mutableStateMapOf<String, String>() }
    var error by remember(tool) { mutableStateOf<String?>(null) }
    val info = ToolPolicy.info(tool.name.orEmpty())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(info.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(tool.description ?: info.why, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Space.md))
                if (fields.isEmpty()) {
                    Text("This tool takes no arguments.", style = MaterialTheme.typography.bodySmall)
                }
                fields.forEach { f ->
                    OutlinedTextField(
                        value = values[f.name].orEmpty(),
                        onValueChange = { values[f.name] = it; error = null },
                        label = { Text(f.name + if (f.required) " *" else "") },
                        supportingText = {
                            Text(
                                when (f.type) {
                                    "array" -> "JSON array, or comma-separated values"
                                    "object" -> "JSON object"
                                    "integer" -> "Whole number"
                                    else -> f.type
                                }
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (f.type == "array" || f.type == "object") MonoFamily else MaterialTheme.typography.bodyMedium.fontFamily),
                        keyboardOptions = KeyboardOptions(keyboardType = if (f.type == "integer" || f.type == "number") KeyboardType.Number else KeyboardType.Text),
                        singleLine = f.type != "array" && f.type != "object",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    )
                }
                error?.let { Text(it, color = FreyaTheme.colors.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                build(fields, values.toMap())
                    .onSuccess(onRun)
                    .onFailure { error = it.message }
            }) {
                Text(if (info.risk == ToolRisk.READ_ONLY) "Run now" else "Review & approve")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
