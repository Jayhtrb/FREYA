package ai.freya.app.ui.components

import ai.freya.app.voice.VoiceState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A restrained, cheap holographic avatar: the FREYA emblem over a slow soft glow.
 * No physics, no heavy animation; the accent shifts with the current voice state.
 */
@Composable
fun HolographicAvatar(
    state: VoiceState,
    modifier: Modifier = Modifier,
    contentDescription: String = "FREYA avatar",
) {
    val transition = rememberInfiniteTransition(label = "avatarGlow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    val accent = when (state) {
        VoiceState.IDLE -> MaterialTheme.colorScheme.primary
        VoiceState.LISTENING -> MaterialTheme.colorScheme.secondary
        VoiceState.THINKING -> MaterialTheme.colorScheme.primary
        VoiceState.SPEAKING -> MaterialTheme.colorScheme.secondary
        VoiceState.TASK_EXECUTION -> MaterialTheme.colorScheme.tertiary
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = glowAlpha))
        )
        FreyaEmblem(
            modifier = Modifier.size(56.dp),
            contentDescription = contentDescription,
        )
    }
}
