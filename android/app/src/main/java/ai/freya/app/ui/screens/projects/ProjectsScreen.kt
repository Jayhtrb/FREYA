package ai.freya.app.ui.screens.projects

import androidx.compose.ui.graphics.Color
import ai.freya.app.FreyaApplication
import ai.freya.app.data.remote.dto.ProjectDto
import ai.freya.app.domain.model.UploadFile
import ai.freya.app.ui.components.EmptyState
import ai.freya.app.ui.components.ErrorState
import ai.freya.app.ui.components.LoadingState
import ai.freya.app.util.formatStatus
import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProjectsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as FreyaApplication).container.projectsRepository

    private val _projects = MutableStateFlow<List<ProjectDto>>(emptyList())
    val projects: StateFlow<List<ProjectDto>> = _projects.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _projects.value = repo.listProjects()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    fun create(
        files: List<UploadFile>,
        requirement: String,
        name: String,
        additionalContext: String,
        onCreated: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _creating.value = true
            _error.value = null
            try {
                val result = repo.generateOntology(files, requirement, name, additionalContext)
                val projectId = result.projectId
                if (projectId != null) {
                    runCatching { repo.buildGraph(projectId) }
                    _projects.value = repo.listProjects()
                    onCreated(projectId)
                } else {
                    _error.value = "Project created without an id"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _creating.value = false
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                repo.deleteProject(id)
                _projects.value = _projects.value.filterNot { it.projectId == id }
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }
}

@Composable
fun ProjectsScreen(
    onOpenProject: (String) -> Unit,
) {
    val vm: ProjectsViewModel = viewModel()
    val projects by vm.projects.collectAsStateWithLifecycle(initialValue = emptyList())
    val loading by vm.loading.collectAsStateWithLifecycle(initialValue = false)
    val creating by vm.creating.collectAsStateWithLifecycle(initialValue = false)
    val error by vm.error.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New project") },
            )
        },
        containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loading && projects.isEmpty() -> LoadingState(label = "Loading projects…")
                error != null && projects.isEmpty() -> ErrorState(
                    message = error.orEmpty(),
                    onRetry = { vm.load() },
                )
                projects.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Folder,
                    title = "No projects yet",
                    subtitle = "Upload documents to build a simulation graph and run FREYA's agents.",
                )
                else -> {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(projects, key = { it.projectId.orEmpty() }) { project ->
                            ProjectCard(
                                project = project,
                                onClick = { project.projectId?.let(onOpenProject) },
                                onDelete = { project.projectId?.let(vm::delete) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateProjectDialog(
            busy = creating,
            onDismiss = { showCreate = false },
            onConfirm = { files, requirement, name, context ->
                vm.create(files, requirement, name, context) { projectId ->
                    showCreate = false
                    onOpenProject(projectId)
                }
            },
        )
    }
}

@Composable
private fun ProjectCard(
    project: ProjectDto,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = project.name ?: "Unnamed Project",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatStatus(project.status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (!project.simulationRequirement.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = project.simulationRequirement.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            if (project.files.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = project.files.joinToString(", ") { it.filename ?: "file" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CreateProjectDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<UploadFile>, String, String, String) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var requirement by remember { mutableStateOf("") }
    var additional by remember { mutableStateOf("") }
    var files by remember { mutableStateOf<List<UploadFile>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        files = uris.mapNotNull { readUploadFile(context, it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = requirement,
                    onValueChange = { requirement = it },
                    label = { Text("Simulation requirement") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = additional,
                    onValueChange = { additional = it },
                    label = { Text("Additional context (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Choose documents (PDF, MD, TXT)")
                }
                if (files.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = files.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(files, requirement, name, additional) },
                enabled = !busy && files.isNotEmpty() && requirement.isNotBlank(),
            ) {
                Text(if (busy) "Creating…" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun readUploadFile(context: Context, uri: Uri): UploadFile? {
    return runCatching {
        val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: uri.lastPathSegment ?: "document"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        UploadFile(name = name, bytes = bytes, mimeType = mime)
    }.getOrNull()
}
