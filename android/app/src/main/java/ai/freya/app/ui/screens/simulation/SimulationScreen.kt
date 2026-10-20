package ai.freya.app.ui.screens.simulation

import ai.freya.app.FreyaApplication
import ai.freya.app.data.remote.dto.ProjectDto
import ai.freya.app.data.remote.dto.PrepareStatusDto
import ai.freya.app.data.remote.dto.RunStatusDto
import ai.freya.app.data.remote.dto.SimulationDto
import ai.freya.app.ui.components.EmptyState
import ai.freya.app.ui.components.ErrorState
import ai.freya.app.ui.components.LoadingState
import ai.freya.app.util.formatStatus
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SimulationViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as FreyaApplication).container
    private val simRepo = container.simulationRepository
    private val projRepo = container.projectsRepository

    private val _project = MutableStateFlow<ProjectDto?>(null)
    val project: StateFlow<ProjectDto?> = _project.asStateFlow()

    private val _simulation = MutableStateFlow<SimulationDto?>(null)
    val simulation: StateFlow<SimulationDto?> = _simulation.asStateFlow()

    private val _runStatus = MutableStateFlow<RunStatusDto?>(null)
    val runStatus: StateFlow<RunStatusDto?> = _runStatus.asStateFlow()

    private val _prepare = MutableStateFlow<PrepareStatusDto?>(null)
    val prepare: StateFlow<PrepareStatusDto?> = _prepare.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var projectId: String = ""

    fun load(pid: String) {
        projectId = pid
        viewModelScope.launch {
            _error.value = null
            try {
                _project.value = projRepo.getProject(pid)
                val sims = simRepo.listSimulations(pid)
                _simulation.value = sims.firstOrNull()
                _simulation.value?.simulationId?.let { refreshRunStatus(it) }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun create() {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                _simulation.value = simRepo.createSimulation(projectId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _busy.value = false
            }
        }
    }

    fun prepare() {
        val simId = _simulation.value?.simulationId ?: return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val resp = simRepo.prepare(simId)
                val taskId = resp.taskId
                if (resp.alreadyPrepared || resp.status == "ready") {
                    _prepare.value = null
                } else if (taskId != null) {
                    pollPrepare(simId, taskId)
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun pollPrepare(simId: String, taskId: String) {
        var done = false
        while (!done) {
            delay(2000)
            val status = runCatching { simRepo.prepareStatus(simId, taskId) }.getOrNull() ?: break
            _prepare.value = status
            done = status.status in setOf("ready", "completed") || status.alreadyPrepared == true
        }
    }

    fun run(platform: String, maxRounds: Int?) {
        val simId = _simulation.value?.simulationId ?: return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                _runStatus.value = simRepo.start(simId, platform, maxRounds)
                pollRunStatus(simId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun pollRunStatus(simId: String) {
        var done = false
        while (!done) {
            delay(2500)
            val status = runCatching { simRepo.runStatus(simId) }.getOrNull() ?: break
            _runStatus.value = status
            val runner = status.runnerStatus ?: "idle"
            done = runner in setOf("completed", "stopped", "failed", "idle")
        }
    }

    fun stop() {
        val simId = _simulation.value?.simulationId ?: return
        viewModelScope.launch {
            try {
                _runStatus.value = simRepo.stop(simId)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private fun refreshRunStatus(simId: String) {
        viewModelScope.launch {
            runCatching { simRepo.runStatus(simId) }.getOrNull()?.let { _runStatus.value = it }
        }
    }
}

@Composable
fun SimulationScreen(
    projectId: String,
    onBack: () -> Unit,
    onOpenReport: (String) -> Unit,
    onOpenInterview: (String) -> Unit,
) {
    val vm: SimulationViewModel = viewModel()
    val project by vm.project.collectAsStateWithLifecycle()
    val simulation by vm.simulation.collectAsStateWithLifecycle()
    val runStatus by vm.runStatus.collectAsStateWithLifecycle()
    val prepare by vm.prepare.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle(initialValue = false)
    val error by vm.error.collectAsStateWithLifecycle()

    var platform by remember { mutableStateOf("parallel") }
    var maxRounds by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(projectId) { vm.load(projectId) }

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
            Text("Simulation", style = MaterialTheme.typography.titleLarge)
        }

        when {
            error != null && simulation == null -> ErrorState(message = error.orEmpty(), onRetry = { vm.load(projectId) })
            project == null && simulation == null -> LoadingState(label = "Loading…")
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    project?.let { p ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(p.name ?: "Project", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Graph: ${formatStatus(p.status)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }

                    if (simulation == null) {
                        Button(onClick = { vm.create() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (busy) "Working…" else "Create simulation")
                        }
                    } else {
                        SimulationControls(
                            simulation = simulation,
                            prepare = prepare,
                            runStatus = runStatus,
                            busy = busy,
                            platform = platform,
                            maxRounds = maxRounds,
                            onPlatformChange = { platform = it },
                            onMaxRoundsChange = { maxRounds = it },
                            onPrepare = { vm.prepare() },
                            onRun = { vm.run(platform, maxRounds.toIntOrNull()) },
                            onStop = { vm.stop() },
                        )

                        simulation?.simulationId?.let { simId ->
                            val runner = runStatus?.runnerStatus
                            val finished = runner == "completed" || runner == "stopped"
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onOpenReport(simId) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Report") }
                                OutlinedButton(
                                    onClick = { onOpenInterview(simId) },
                                    enabled = finished,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Interview") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimulationControls(
    simulation: SimulationDto?,
    prepare: PrepareStatusDto?,
    runStatus: RunStatusDto?,
    busy: Boolean,
    platform: String,
    maxRounds: String,
    onPlatformChange: (String) -> Unit,
    onMaxRoundsChange: (String) -> Unit,
    onPrepare: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Simulation: ${formatStatus(simulation?.status)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (simulation?.entitiesCount != null) {
                Text(
                    "Agents: ${simulation.entitiesCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            prepare?.let {
                Spacer(Modifier.height(8.dp))
                Text("Preparing…", style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(
                    progress = { (it.progress ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!it.message.isNullOrBlank()) {
                    Text(
                        it.message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            runStatus?.let { rs ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "Runner: ${formatStatus(rs.runnerStatus)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                val progress = (rs.progressPercent ?: 0.0).toFloat().coerceIn(0f, 100f)
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Round ${rs.currentRound ?: 0} / ${rs.totalRounds ?: 0} · ${rs.totalActionsCount ?: 0} actions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Platform", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("parallel", "reddit", "twitter").forEach { p ->
                    FilterChip(
                        selected = platform == p,
                        onClick = { onPlatformChange(p) },
                        label = { Text(p) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = maxRounds,
                onValueChange = { onMaxRoundsChange(it.filter(Char::isDigit)) },
                label = { Text("Max rounds (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPrepare, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Prepare")
                }
                Button(onClick = onRun, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Run")
                }
            }
            val runner = runStatus?.runnerStatus
            if (runner in setOf("running", "starting", "paused")) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
