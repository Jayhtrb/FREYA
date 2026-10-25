package ai.freya.app.ui.screens.home

import ai.freya.app.domain.TextInsights
import ai.freya.app.domain.ToolPolicy
import ai.freya.app.domain.model.ChatMessage
import ai.freya.app.domain.model.MessageKind
import ai.freya.app.domain.model.MessageStatus
import ai.freya.app.ui.components.GlassPanel
import ai.freya.app.ui.components.StatusChip
import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.ui.theme.MonoFamily
import ai.freya.app.ui.theme.Radius
import ai.freya.app.ui.theme.Space
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Callbacks for message controls. Every control on a message maps to one of these. */
data class MessageActions(
    val onCopy: (String) -> Unit,
    val onRetry: (Long) -> Unit,
    val onReadAloud: (Long) -> Unit,
    val onFeedback: (Long, Int) -> Unit,
    val canSpeak: Boolean,
    val busy: Boolean,
)

@Composable
fun MessageItem(message: ChatMessage, actions: MessageActions, modifier: Modifier = Modifier) {
    when {
        message.isUser -> UserMessage(message, modifier)
        message.kind == MessageKind.TOOL_RESULT -> ToolResultCard(message, actions, modifier)
        else -> AssistantMessage(message, actions, modifier)
    }
}

@Composable
private fun UserMessage(message: ChatMessage, modifier: Modifier) {
    val c = FreyaTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(Radius.panel)
                .background(c.violet.copy(alpha = if (c.isDark) 0.16f else 0.12f))
                .border(1.dp, c.violet.copy(alpha = 0.35f), Radius.panel)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            SelectionContainer {
                Text(message.content, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantMessage(message: ChatMessage, actions: MessageActions, modifier: Modifier) {
    val c = FreyaTheme.colors
    var showDetails by rememberSaveable(message.id) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val streaming = message.status == MessageStatus.STREAMING

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FREYA", style = MaterialTheme.typography.labelMedium, color = c.cyan)
            Spacer(Modifier.width(Space.sm))
            when (message.status) {
                MessageStatus.STREAMING -> StatusChip(if (message.content.isEmpty()) "thinking" else "streaming", color = c.violet)
                MessageStatus.STOPPED -> StatusChip("stopped", color = c.approval)
                MessageStatus.FAILED -> StatusChip("failed", color = c.error)
                MessageStatus.DONE -> message.meta?.model?.let { StatusChip(it, color = c.textMuted, dot = false) }
            }
        }
        Spacer(Modifier.height(6.dp))

        if (message.content.isNotEmpty()) {
            RichText(message.content + if (streaming) " ▍" else "")
        } else if (streaming) {
            TypingDots()
        }

        if (message.status == MessageStatus.FAILED) {
            Text(
                message.error ?: "FREYA couldn't answer.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.error,
            )
        }

        // ---- sources referenced in the reply ----
        val links = if (streaming) emptyList() else TextInsights.links(message.content)
        if (links.isNotEmpty()) {
            Spacer(Modifier.height(Space.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                links.take(6).forEachIndexed { i, link ->
                    Row(
                        Modifier
                            .clip(Radius.chip)
                            .background(c.glassFill)
                            .border(1.dp, c.glassBorder, Radius.chip)
                            .clickable { runCatching { uri.openUri(link) } }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Link, contentDescription = null, tint = c.cyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("[${i + 1}] ${TextInsights.host(link)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // ---- controls ----
        if (!streaming) {
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (message.content.isNotEmpty()) {
                    SmallAction(Icons.Outlined.ContentCopy, "Copy") { actions.onCopy(message.content) }
                    if (actions.canSpeak) SmallAction(Icons.AutoMirrored.Outlined.VolumeUp, "Read aloud") { actions.onReadAloud(message.id) }
                }
                SmallAction(Icons.Outlined.Refresh, "Retry", enabled = !actions.busy) { actions.onRetry(message.id) }
                if (message.status == MessageStatus.DONE) {
                    SmallAction(
                        if (message.feedback > 0) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        if (message.feedback > 0) "Marked helpful" else "Helpful",
                        tint = if (message.feedback > 0) c.success else null,
                    ) { actions.onFeedback(message.id, 1) }
                    SmallAction(
                        if (message.feedback < 0) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        if (message.feedback < 0) "Marked not helpful" else "Not helpful",
                        tint = if (message.feedback < 0) c.error else null,
                    ) { actions.onFeedback(message.id, -1) }
                }
                Spacer(Modifier.weight(1f))
                if (message.meta != null) {
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text("Details", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                        Icon(if (showDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        AnimatedVisibility(showDetails && message.meta != null) {
            message.meta?.let { meta -> TechDetails(meta) }
        }
    }
}

@Composable
private fun TechDetails(meta: ai.freya.app.domain.model.TurnMeta) {
    val lines = buildList {
        add("model" to (meta.model ?: "not reported by backend"))
        add("transport" to meta.transport)
        meta.firstTokenMs?.let { add("first token" to "$it ms") }
        meta.totalMs?.let { add("total" to "$it ms") }
        add("characters" to meta.chars.toString())
        add("≈ tokens" to "${meta.chars / 4} (estimate)")
        meta.conversationId?.let { add("conversation" to it) }
        if (meta.toolEvents.isNotEmpty()) add("tools" to meta.toolEvents.joinToString("  "))
    }
    GlassPanel(Modifier.fillMaxWidth().padding(top = 4.dp), shape = Radius.control, contentPadding = Space.md) {
        lines.forEach { (k, v) ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(k.uppercase(), style = MaterialTheme.typography.labelSmall, color = FreyaTheme.colors.textMuted, modifier = Modifier.width(104.dp))
                Text(v, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ToolResultCard(message: ChatMessage, actions: MessageActions, modifier: Modifier) {
    val c = FreyaTheme.colors
    val info = ToolPolicy.info(message.toolName ?: "tool")
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val failed = message.status == MessageStatus.FAILED
    GlassPanel(modifier.fillMaxWidth(), accent = if (failed) c.error else c.ice, contentPadding = Space.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Build, contentDescription = null, tint = if (failed) c.error else c.ice, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text("TASK RESULT", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                Text(info.title, style = MaterialTheme.typography.titleSmall)
            }
            StatusChip(if (failed) "failed" else "verified by backend", color = if (failed) c.error else c.success)
        }
        Spacer(Modifier.height(Space.sm))
        if (failed) {
            Text(message.error ?: "Task failed", style = MaterialTheme.typography.bodyMedium, color = c.error)
        } else {
            Text(
                message.content,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallAction(Icons.Outlined.ContentCopy, "Copy result") { actions.onCopy(message.content) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Collapse" else "Show full result", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                }
            }
        }
        val meta = message.meta
        if (meta?.totalMs != null) {
            Text("${meta.transport} · ${meta.totalMs} ms", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, enabled: Boolean = true, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.85f else 0.35f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Splits fenced code blocks out of a reply so code is readable and copyable. */
@Composable
fun RichText(text: String) {
    val parts = remember(text) { splitCode(text) }
    val clipboard = LocalClipboardManager.current
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.forEach { (isCode, body) ->
                if (isCode) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(Radius.control)
                            .background(FreyaTheme.colors.glassFill)
                            .border(1.dp, FreyaTheme.colors.glassBorder, Radius.control)
                            .padding(12.dp),
                    ) {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            Text(body, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily), color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            "Copy code",
                            style = MaterialTheme.typography.labelSmall,
                            color = FreyaTheme.colors.cyan,
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable { clipboard.setText(AnnotatedString(body)) }
                                .padding(top = 6.dp),
                        )
                    }
                } else if (body.isNotBlank()) {
                    Text(body.trim('\n'), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

internal fun splitCode(text: String): List<Pair<Boolean, String>> {
    val out = mutableListOf<Pair<Boolean, String>>()
    val pieces = text.split("```")
    pieces.forEachIndexed { i, piece ->
        if (i % 2 == 1) {
            // Drop an optional language tag on the first line.
            val body = piece.substringAfter('\n', piece).trimEnd()
            out += true to body
        } else {
            out += false to piece
        }
    }
    return out
}

@Composable
private fun TypingDots() {
    val reduce = FreyaTheme.motion.reduceMotion
    val t = rememberInfiniteTransition(label = "dots")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dotAlpha")
    Text(
        "● ● ●",
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Default),
        color = FreyaTheme.colors.violet,
        modifier = Modifier.alpha(if (reduce) 0.7f else a),
    )
}

/** Compact glass card summarising the latest reply on the Core view. */
@Composable
fun LatestReplyCard(message: ChatMessage, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier.fillMaxWidth(), onClick = onOpen, contentPadding = Space.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (message.kind == MessageKind.TOOL_RESULT) "LATEST TASK" else "LATEST REPLY", style = MaterialTheme.typography.labelSmall, color = FreyaTheme.colors.textMuted, modifier = Modifier.weight(1f))
            Text("Open conversation", style = MaterialTheme.typography.labelSmall, color = FreyaTheme.colors.cyan)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                message.status == MessageStatus.FAILED -> message.error ?: "Failed"
                message.kind == MessageKind.TOOL_RESULT -> ToolPolicy.info(message.toolName ?: "tool").title + " — completed"
                message.content.isEmpty() -> "…"
                else -> message.content
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
