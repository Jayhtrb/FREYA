package ai.freya.app.ui.screens.report

import ai.freya.app.FreyaApplication
import ai.freya.app.data.remote.dto.ReportChatMessageDto
import ai.freya.app.data.remote.dto.ReportDto
import ai.freya.app.domain.model.ChatMessage
import ai.freya.app.ui.components.EmptyState
import ai.freya.app.ui.components.ErrorState
import ai.freya.app.ui.components.LoadingState
import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReportViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext
    private val repo = (app as FreyaApplication).container.reportRepository

    private val _report = MutableStateFlow<ReportDto?>(null)
    val report: StateFlow<ReportDto?> = _report.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chat: StateFlow<List<ChatMessage>> = _chat.asStateFlow()

    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy.asStateFlow()

    private val history = mutableListOf<ReportChatMessageDto>()

    fun load(simulationId: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _report.value = repo.bySimulation(simulationId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun chat(simulationId: String, message: String) {
        val m = message.trim()
        if (m.isEmpty()) return
        _chat.value = _chat.value + ChatMessage("user", m)
        history.add(ReportChatMessageDto("user", m))
        viewModelScope.launch {
            _chatBusy.value = true
            _error.value = null
            try {
                val resp = repo.chat(simulationId, m, history.toList())
                val text = resp.response.orEmpty()
                _chat.value = _chat.value + ChatMessage("assistant", text)
                history.add(ReportChatMessageDto("assistant", text))
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _chatBusy.value = false
            }
        }
    }

    suspend fun download(reportId: String, uri: Uri): Boolean = try {
        val body = repo.download(reportId)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            body.byteStream().use { it.copyTo(out) }
        }
        true
    } catch (e: Exception) {
        _error.value = e.message
        false
    }
}

@Composable
fun ReportScreen(
    simulationId: String,
    onBack: () -> Unit,
) {
    val vm: ReportViewModel = viewModel()
    val report by vm.report.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle(initialValue = false)
    val error by vm.error.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle(initialValue = emptyList())
    val chatBusy by vm.chatBusy.collectAsStateWithLifecycle(initialValue = false)

    var input by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        val reportId = report?.reportId
        if (uri != null && reportId != null) {
            scope.launch { vm.download(reportId, uri) }
        }
    }

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
            Text("Report", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            report?.reportId?.let { id ->
                IconButton(onClick = { downloadLauncher.launch("$id.md") }) {
                    Icon(Icons.Filled.Download, contentDescription = "Download report")
                }
            }
        }

        when {
            loading -> LoadingState(label = "Loading report…")
            error != null && report == null -> ErrorState(message = error.orEmpty(), onRetry = { vm.load(simulationId) })
            report == null -> EmptyState(
                icon = Icons.Outlined.Description,
                title = "No report yet",
                subtitle = "Run a simulation to completion, then generate a report.",
            )
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(0.6f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Text(
                            text = report?.title ?: "Report",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = report?.markdownContent.orEmpty().ifBlank { "No content available yet." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Ask the report agent",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    LazyColumn(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(chat) { m ->
                            Text(
                                text = if (m.isUser) "You: ${m.content}" else "FREYA: ${m.content}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (m.isUser) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("Ask about the report…") },
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                vm.chat(simulationId, input)
                                input = ""
                            },
                            enabled = !chatBusy && input.isNotBlank(),
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}
