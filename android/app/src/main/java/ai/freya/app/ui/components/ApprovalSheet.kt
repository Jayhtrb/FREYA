package ai.freya.app.ui.components

import ai.freya.app.domain.ToolRisk
import ai.freya.app.session.PendingApproval
import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.ui.theme.MonoFamily
import ai.freya.app.ui.theme.Radius
import ai.freya.app.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * AWAITING APPROVAL interface. Visually distinct (amber, shield, explicit args) and modal.
 * Swiping the sheet away counts as Deny — nothing runs without a deliberate Approve.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(
    pending: PendingApproval,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    val c = FreyaTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val argsText = remember(pending) {
        Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), pending.args)
    }
    ModalBottomSheet(
        onDismissRequest = onDeny,
        sheetState = sheetState,
        containerColor = if (c.isDark) Color(0xFF0E1222) else MaterialTheme.colorScheme.surface,
        shape = Radius.sheet,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, c.approval.copy(alpha = 0.45f), Radius.sheet)
                .padding(Space.xl)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = c.approval, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(Space.md))
                Column {
                    Text("APPROVAL REQUIRED", style = MaterialTheme.typography.labelMedium, color = c.approval)
                    Text(pending.info.title, style = MaterialTheme.typography.headlineSmall)
                }
            }
            Spacer(Modifier.height(Space.lg))
            Text(pending.info.why, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Space.sm))
            StatusChip(
                if (pending.info.risk == ToolRisk.READ_ONLY) "read-only" else "sensitive · ${pending.info.risk.label}",
                color = c.approval,
            )
            Spacer(Modifier.height(Space.lg))
            Text("WHAT WILL BE SENT", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
            Spacer(Modifier.height(Space.xs))
            Text(
                "tool: ${pending.tool}\n$argsText",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(Radius.control)
                    .background(c.glassFill)
                    .border(1.dp, c.glassBorder, Radius.control)
                    .verticalScroll(rememberScrollState())
                    .padding(Space.md),
            )
            Spacer(Modifier.height(Space.xl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Deny")
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.approval, contentColor = Color(0xFF1A1200)),
                ) {
                    Text("Approve and run")
                }
            }
            Spacer(Modifier.height(Space.sm))
            Text(
                "FREYA reports success only after the backend confirms it.",
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
            )
        }
    }
}
