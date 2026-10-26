package ai.freya.app.ui.screens.settings

import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Slider
import androidx.compose.material3.FilterChip
import ai.freya.app.FreyaApplication
import ai.freya.app.domain.model.MemoryPreference
import ai.freya.app.ui.components.EmptyState
import ai.freya.app.ui.components.ErrorState
import ai.freya.app.ui.components.LoadingState
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as FreyaApplication).container
    private val settingsRepo = container.settingsRepository
    private val authRepo = container.authRepository
    val dataStore = container.dataStore

    val storedUser = authRepo.storedUser

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun save(personality: String, model: String, voice: String) {
        viewModelScope.launch {
            _saving.value = true
            try {
                dataStore.setPersonality(personality)
                dataStore.setModel(model)
                dataStore.setVoice(voice)
                runCatching { settingsRepo.update(personality, model, voice) }
                _message.value = "Settings saved"
            } catch (e: Exception) {
                _message.value = e.message
            } finally {
                _saving.value = false
            }
        }
    }

    fun setVoiceEnabled(value: Boolean) {
        viewModelScope.launch { dataStore.setVoiceEnabled(value) }
    }

    fun setHandsFree(value: Boolean) {
        viewModelScope.launch { dataStore.setHandsFree(value) }
    }

    fun setThemeMode(value: String) {
        viewModelScope.launch { dataStore.setThemeMode(value) }
    }

    fun setReduceMotion(value: Boolean) {
        viewModelScope.launch { dataStore.setReduceMotion(value) }
    }

    fun setHaptics(value: Boolean) {
        viewModelScope.launch { dataStore.setHaptics(value) }
    }

    fun setSpeechRate(value: Float) {
        viewModelScope.launch { dataStore.setSpeechRate(value) }
    }

    fun setVoiceEngine(value: String) {
        viewModelScope.launch { dataStore.setVoiceEngine(value) }
        if (value == "voicebox") refreshTts()
    }

    private val _tts = MutableStateFlow<ai.freya.app.data.remote.dto.TtsStatusDto?>(null)
    val tts: StateFlow<ai.freya.app.data.remote.dto.TtsStatusDto?> = _tts.asStateFlow()
    private val _ttsError = MutableStateFlow<String?>(null)
    val ttsError: StateFlow<String?> = _ttsError.asStateFlow()

    fun refreshTts() {
        viewModelScope.launch {
            runCatching { container.keysRepository.ttsStatus() }
                .onSuccess { _tts.value = it; _ttsError.value = null }
                .onFailure { _ttsError.value = it.message }
        }
    }

    init {
        refreshTts()
    }

    fun signOut() {
        viewModelScope.launch { authRepo.logout() }
    }
}

@Composable
fun SettingsScreen(
    onOpenMemory: () -> Unit,
    onOpenPower: () -> Unit,
    onOpenControlCenter: () -> Unit = {},
) {
    val vm: SettingsViewModel = viewModel()
    val user by vm.storedUser.collectAsStateWithLifecycle(initialValue = null)
    val personality by vm.dataStore.personality.collectAsStateWithLifecycle(initialValue = "composed")
    val model by vm.dataStore.model.collectAsStateWithLifecycle(initialValue = "deepseek-chat")
    val voice by vm.dataStore.voice.collectAsStateWithLifecycle(initialValue = "system_tts")
    val voiceEnabled by vm.dataStore.voiceEnabled.collectAsStateWithLifecycle(initialValue = false)
    val handsFree by vm.dataStore.handsFreeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val themeMode by vm.dataStore.themeMode.collectAsStateWithLifecycle(initialValue = "dark")
    val reduceMotion by vm.dataStore.reduceMotion.collectAsStateWithLifecycle(initialValue = false)
    val hapticsOn by vm.dataStore.haptics.collectAsStateWithLifecycle(initialValue = true)
    val speechRate by vm.dataStore.speechRate.collectAsStateWithLifecycle(initialValue = 1f)
    val voiceEngine by vm.dataStore.voiceEngine.collectAsStateWithLifecycle(initialValue = "system")
    val tts by vm.tts.collectAsStateWithLifecycle()
    val ttsError by vm.ttsError.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle(initialValue = false)
    val message by vm.message.collectAsStateWithLifecycle()

    var personalityEdit by rememberSaveable { mutableStateOf("") }
    var modelEdit by rememberSaveable { mutableStateOf("") }
    var voiceEdit by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(personality) { if (personalityEdit.isBlank()) personalityEdit = personality }
    LaunchedEffect(model) { if (modelEdit.isBlank()) modelEdit = model }
    LaunchedEffect(voice) { if (voiceEdit.isBlank()) voiceEdit = voice }

    Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(user?.displayName ?: "Account", style = MaterialTheme.typography.titleMedium)
                    Text(
                        user?.email ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Server", style = MaterialTheme.typography.titleMedium)
            ai.freya.app.ui.screens.auth.ServerAddressRow()
            Spacer(Modifier.height(16.dp))
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ChoiceRow(
                label = "Theme",
                options = listOf("dark" to "Dark", "light" to "Light", "system" to "System"),
                selected = themeMode,
                onSelect = vm::setThemeMode,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Reduce motion", style = MaterialTheme.typography.bodyLarge)
                    Text("Stops ambient animation; states stay visible by colour and shape. Also follows the system setting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = reduceMotion, onCheckedChange = { vm.setReduceMotion(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Haptics", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = hapticsOn, onCheckedChange = { vm.setHaptics(it) })
            }

            Spacer(Modifier.height(16.dp))
            Text("Personality & model", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = personalityEdit,
                onValueChange = { personalityEdit = it },
                label = { Text("Personality") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = modelEdit,
                onValueChange = { modelEdit = it },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = voiceEdit,
                onValueChange = { voiceEdit = it },
                label = { Text("Voice provider") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Text("Voice", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Voice output", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = voiceEnabled, onCheckedChange = { vm.setVoiceEnabled(it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hands-free (listen again after each reply)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = handsFree, onCheckedChange = { vm.setHandsFree(it) })
            }
            ChoiceRow(
                label = "Voice engine",
                options = listOf("system" to "Phone voice", "voicebox" to "Voicebox"),
                selected = voiceEngine,
                onSelect = vm::setVoiceEngine,
            )
            Text(
                text = when {
                    ttsError != null -> "Voicebox status unavailable: $ttsError"
                    tts == null -> "Checking Voicebox…"
                    tts?.configured != true -> "Voicebox isn't set up on your FREYA server yet. Add VOICEBOX_URL (and VOICEBOX_VOICE) to .env.local and restart it. Until then FREYA uses the phone voice."
                    tts?.reachable != true -> "Voicebox is configured but not reachable. Is it running? FREYA falls back to the phone voice."
                    else -> "Voicebox ready" + (tts?.voice?.let { " · voice \"$it\"" } ?: " · default voice") + ". Replies are spoken by Voicebox, synced to the Core."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text("Speech speed · ${"%.1f".format(speechRate)}×", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = speechRate,
                onValueChange = { vm.setSpeechRate((it * 10).roundToInt() / 10f) },
                valueRange = 0.5f..2.0f,
                steps = 14,
            )

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.save(personalityEdit, modelEdit, voiceEdit) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Saving…" else "Save settings")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenControlCenter, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("AI Control Center")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenPower, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Power / API keys")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenMemory, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Memory, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Memory management")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.signOut() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

class MemoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as FreyaApplication).container.memoryRepository

    private val _prefs = MutableStateFlow<List<MemoryPreference>>(emptyList())
    val prefs: StateFlow<List<MemoryPreference>> = _prefs.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                _prefs.value = repo.preferences()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _busy.value = false
            }
        }
    }

    fun delete(key: String) {
        viewModelScope.launch {
            try {
                repo.deletePreference(key)
                _prefs.value = _prefs.value.filterNot { it.key == key }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun add(key: String, value: String) {
        viewModelScope.launch {
            try {
                repo.upsertPreference(key, value)
                load()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}

@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val vm: MemoryViewModel = viewModel()
    val prefs by vm.prefs.collectAsStateWithLifecycle(initialValue = emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle(initialValue = false)
    val error by vm.error.collectAsStateWithLifecycle()

    var key by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }

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
            Text("Memory", style = MaterialTheme.typography.titleLarge)
        }

        when {
            busy && prefs.isEmpty() -> LoadingState(label = "Loading memory…")
            error != null && prefs.isEmpty() -> ErrorState(message = error.orEmpty(), onRetry = { vm.load() })
            prefs.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Memory,
                title = "No stored preferences",
                subtitle = "Preferences you approve are stored here, separated from project context.",
            )
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(prefs.size) { index ->
                        val pref = prefs[index]
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(pref.key, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        pref.value,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { vm.delete(pref.key) }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Key") },
                singleLine = true,
                modifier = Modifier.weight(0.4f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Value") },
                singleLine = true,
                modifier = Modifier.weight(0.6f),
            )
        }
        Button(
            onClick = { vm.add(key, value); key = ""; value = "" },
            enabled = key.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text("Add preference")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(text) },
                )
            }
        }
    }
}
