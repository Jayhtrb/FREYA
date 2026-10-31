package ai.freya.app.ui.screens.power

import ai.freya.app.FreyaApplication
import ai.freya.app.data.remote.dto.KeysStatusDto
import ai.freya.app.ui.components.ErrorState
import ai.freya.app.ui.components.FreyaEmblem
import ai.freya.app.ui.components.LoadingState
import ai.freya.app.ui.components.NeuStyle
import ai.freya.app.ui.components.NeumorphicButton
import ai.freya.app.ui.components.NeumorphicField
import ai.freya.app.ui.components.neumorphic
import ai.freya.app.ui.theme.FreyaTheme
import ai.freya.app.ui.theme.Radius
import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PowerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as FreyaApplication).container.keysRepository

    private val _status = MutableStateFlow<KeysStatusDto?>(null)
    val status: StateFlow<KeysStatusDto?> = _status.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _clearing = MutableStateFlow(false)
    val clearing: StateFlow<Boolean> = _clearing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _status.value = repo.status()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun save(deepseek: String, llm: String, llmBaseUrl: String, llmModel: String, zep: String) {
        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            _message.value = null
            try {
                _status.value = repo.save(
                    deepseek = deepseek.blankToNull(),
                    llm = llm.blankToNull(),
                    llmBaseUrl = llmBaseUrl.blankToNull(),
                    llmModel = llmModel.blankToNull(),
                    zep = zep.blankToNull(),
                )
                _message.value = "Core ignited"
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _saving.value = false
            }
        }
    }

    fun clear() {
        viewModelScope.launch {
            _clearing.value = true
            _error.value = null
            _message.value = null
            try {
                repo.clear()
                _status.value = KeysStatusDto()
                _message.value = "Keys cleared"
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _clearing.value = false
            }
        }
    }
}

private fun String.blankToNull(): String? = if (isBlank()) null else this

@Composable
fun PowerScreen(onBack: () -> Unit) {
    val vm: PowerViewModel = viewModel()
    val status by vm.status.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle(initialValue = false)
    val saving by vm.saving.collectAsStateWithLifecycle(initialValue = false)
    val clearing by vm.clearing.collectAsStateWithLifecycle(initialValue = false)
    val error by vm.error.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    var deepseek by rememberSaveable { mutableStateOf("") }
    var llm by rememberSaveable { mutableStateOf("") }
    var llmBaseUrl by rememberSaveable { mutableStateOf("") }
    var llmModel by rememberSaveable { mutableStateOf("") }
    var zep by rememberSaveable { mutableStateOf("") }

    // Prefill the non-secret LLM base URL / model once status loads.
    LaunchedEffect(status) {
        val current = status ?: return@LaunchedEffect
        if (llmBaseUrl.isBlank()) llmBaseUrl = current.llm.baseUrl.orEmpty()
        if (llmModel.isBlank()) llmModel = current.llm.model.orEmpty()
    }

    val anySet = status?.anySet == true

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Power", style = MaterialTheme.typography.titleLarge)
        }

        when {
            loading && status == null -> LoadingState(label = "Reading your core…")
            error != null && status == null -> ErrorState(message = error.orEmpty(), onRetry = { vm.load() })
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CoreOrb(ignited = anySet, modifier = Modifier.size(168.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (anySet) "CORE ONLINE" else "CORE OFFLINE",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (anySet) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))

                    ProviderChips(status)
                    Spacer(Modifier.height(20.dp))

                    NeumorphicField(
                        value = deepseek,
                        onValueChange = { deepseek = it },
                        placeholder = "DeepSeek API key",
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    NeumorphicField(
                        value = llm,
                        onValueChange = { llm = it },
                        placeholder = "LLM API key",
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    NeumorphicField(
                        value = llmBaseUrl,
                        onValueChange = { llmBaseUrl = it },
                        placeholder = "LLM base URL (optional)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    NeumorphicField(
                        value = llmModel,
                        onValueChange = { llmModel = it },
                        placeholder = "LLM model",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    NeumorphicField(
                        value = zep,
                        onValueChange = { zep = it },
                        placeholder = "Zep API key",
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    message?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(Modifier.height(20.dp))
                    NeumorphicButton(
                        label = if (saving) "Igniting…" else "Ignite",
                        onClick = { vm.save(deepseek, llm, llmBaseUrl, llmModel, zep) },
                        enabled = !saving && !clearing,
                        tint = FreyaTheme.colors.violet,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { vm.clear() },
                        enabled = !clearing && anySet,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (clearing) "Clearing…" else "Clear keys", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ProviderChips(status: KeysStatusDto?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProviderChip("DeepSeek", status?.deepseek?.set == true)
        ProviderChip("LLM", status?.llm?.set == true)
        ProviderChip("Zep", status?.zep?.set == true)
    }
}

@Composable
private fun ProviderChip(name: String, configured: Boolean) {
    val c = FreyaTheme.colors
    val dot = if (configured) c.success else MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .neumorphic(Radius.pill, NeuStyle.Flat, c.surface, c.sunken, c.neuHighlight, c.neuShadow)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$name ${if (configured) "configured" else "not set"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A cheap, non-blocking neumorphic core: a raised orb in a sunken well. Dim and
 * still when no keys are set; when ignited it breathes gently and gains a muted
 * violet accent ring. No glow, blur, or trails.
 */
@Composable
private fun CoreOrb(ignited: Boolean, modifier: Modifier = Modifier) {
    val c = FreyaTheme.colors
    val reduce = FreyaTheme.motion.reduceMotion
    val accent by animateColorAsState(
        targetValue = if (ignited) c.violet else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(700),
        label = "accent",
    )
    val emblemAlpha by animateFloatAsState(
        targetValue = if (ignited) 1f else 0.5f,
        animationSpec = tween(700),
        label = "emblemAlpha",
    )
    val transition = rememberInfiniteTransition(label = "core")
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "corePulse",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Sunken well.
        Box(
            Modifier
                .size(156.dp)
                .neumorphic(CircleShape, NeuStyle.Sunken, c.surface, c.sunken, c.neuHighlight, c.neuShadow)
        )
        // Raised orb (breathing only when ignited and motion is allowed).
        Box(
            Modifier
                .size(112.dp)
                .scale(if (ignited && !reduce) pulse else 1f)
                .neumorphic(CircleShape, NeuStyle.Raised, c.surface, c.sunken, c.neuHighlight, c.neuShadow)
        )
        // Muted accent ring.
        Box(
            Modifier
                .size(102.dp)
                .border(2.dp, accent.copy(alpha = if (ignited) 0.65f else 0.22f), CircleShape)
        )
        FreyaEmblem(
            modifier = Modifier
                .size(60.dp)
                .alpha(emblemAlpha),
            contentDescription = if (ignited) "FREYA core ignited" else "FREYA core offline",
        )
    }
}
