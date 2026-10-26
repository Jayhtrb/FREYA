package ai.freya.app.debug

import ai.freya.app.domain.CoreState
import ai.freya.app.ui.components.FreyaBackdrop
import ai.freya.app.ui.core.FreyaCore
import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.ui.theme.ThemeMode
import ai.freya.app.voice.AudioEnvelope
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * DEBUG ONLY. Shows the real FREYA Core in one state with animation forced on, so CI can record
 * how each state actually moves on a device even when system animations are disabled.
 *
 * For LISTENING and SPEAKING it feeds a synthetic, speech-shaped test signal (syllable bursts
 * with pauses and moving formant peaks) because the CI emulator has no microphone or speaker.
 * The screen says so. The production app only ever uses measured mic/voice levels.
 *
 * adb shell am start -n ai.freya.app.debug/ai.freya.app.debug.CorePreviewActivity --es state SPEAKING
 */
class CorePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val state = intent.getStringExtra("state")?.let { runCatching { CoreState.valueOf(it) }.getOrNull() } ?: CoreState.IDLE
        val start = System.nanoTime()
        val bands = FloatArray(AudioEnvelope.BANDS)
        fun t() = (System.nanoTime() - start) / 1e9f
        fun level(): Float {
            val t = t()
            val phrase = t % 3.2f
            if (phrase > 2.5f) return 0.04f // pause between phrases
            val syllable = abs(sin(t * 3.14159f * 2.1f)).pow(1.6f)
            return (0.12f + 0.78f * syllable * (0.7f + 0.3f * sin(t * 1.3f))).coerceIn(0f, 1f)
        }
        fun bands(): FloatArray {
            val t = t()
            val lv = level()
            val f1 = 1.5f + 1.0f * sin(t * 2.3f)
            val f2 = 4.2f + 1.3f * sin(t * 1.7f + 1f)
            for (k in 0 until AudioEnvelope.BANDS) {
                val a = exp(-(k - f1) * (k - f1) / 1.4f) + 0.7f * exp(-(k - f2) * (k - f2) / 1.1f)
                bands[k] = (lv * (0.25f + a)).coerceIn(0f, 1f)
            }
            return bands
        }
        val audio = state == CoreState.LISTENING || state == CoreState.SPEAKING
        setContent {
            FreyaTheme(themeMode = ThemeMode.DARK, reduceMotion = false) {
                FreyaBackdrop(Modifier.fillMaxSize()) {
                    FreyaCore(
                        state = state,
                        audioLevel = remember { { if (audio) level() else 0f } },
                        audioMeasured = audio,
                        audioBands = remember { { if (state == CoreState.SPEAKING) bands() else null } },
                        receivedChars = if (state == CoreState.PROCESSING) 240 else 0,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(
                        "DEBUG PREVIEW · ${state.label.uppercase()}" + if (audio) " · synthetic test signal" else "",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    )
                }
            }
        }
    }
}
