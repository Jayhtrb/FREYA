package ai.freya.app.ui.screens.interview

import ai.freya.app.FreyaApplication
import ai.freya.app.data.remote.dto.SimulationProfilesDto
import ai.freya.app.domain.model.ChatMessage
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class InterviewViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as FreyaApplication).container.simulationRepository

    private val _profiles = MutableStateFlow<SimulationProfilesDto?>(null)
    val profiles: StateFlow<SimulationProfilesDto?> = _profiles.asStateFlow()

    private val _replies = MutableStateFlow<List<ChatMessage>>(emptyList())
    val replies: StateFlow<List<ChatMessage>> = _replies.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(simulationId: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                _profiles.value = repo.profiles(simulationId, "reddit")
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun interview(simulationId: String, agentId: Int, prompt: String) {
        val p = prompt.trim()
        if (p.isEmpty()) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val result = repo.interview(simulationId, agentId, p)
                val reply = extractReply(result)
                _replies.value = _replies.value + ChatMessage("assistant", "Agent $agentId: $reply")
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _busy.value = false
            }
        }
    }

    private fun extractReply(json: kotlinx.serialization.json.JsonElement): String {
        val obj = json as? JsonObject ?: return json.toString()
        val result = obj["result"]
        if (result is JsonObject) {
            result["response"]?.let { if (it is JsonPrimitive) return it.contentOrNull ?: it.toString() }
            val platforms = result["platforms"] as? JsonObject
            if (platforms != null) {
                val parts = platforms.entries.mapNotNull { (platform, value) ->
                    val resp = (value as? JsonObject)?.get("response") as? JsonPrimitive
                    resp?.contentOrNull?.let { "$platform: $it" }
                }
                if (parts.isNotEmpty()) return parts.joinToString("\n\n")
            }
            return result.toString()
        }
        return result?.toString() ?: json.toString()
    }
}

@Composable
fun InterviewScreen(
    simulationId: String,
    onBack: () -> Unit,
) {
    val vm: InterviewViewModel = viewModel()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val replies by vm.replies.collectAsStateWithLifecycle(initialValue = emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle(initialValue = false)
    val error by vm.error.collectAsStateWithLifecycle()

    var agentId by rememberSaveable { mutableStateOf("0") }
    var prompt by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(simulationId) { vm.load(simulationId) }

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
            Text("Interview agents", style = MaterialTheme.typography.titleLarge)
        }

        when {
            error != null && profiles == null -> ErrorState(message = error.orEmpty(), onRetry = { vm.load(simulationId) })
            profiles == null -> LoadingState(label = "Loading agents…")
            else -> {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        text = "${profiles?.count ?: 0} agents available. The simulation must be running to interview them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(replies) { m ->
                            Text(
                                text = m.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = agentId,
                                onValueChange = { agentId = it.filter(Char::isDigit) },
                                label = { Text("Agent id") },
                                singleLine = true,
                                modifier = Modifier.width(110.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = prompt,
                                onValueChange = { prompt = it },
                                placeholder = { Text("Ask this agent…") },
                                modifier = Modifier.weight(1f),
                                maxLines = 3,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.interview(simulationId, agentId.toIntOrNull() ?: 0, prompt) },
                            enabled = !busy && prompt.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (busy) "Asking…" else "Interview")
                        }
                    }
                }
            }
        }
    }
}
