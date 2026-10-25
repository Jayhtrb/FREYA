package ai.freya.app.ui.screens.jobs

import androidx.compose.ui.graphics.Color
import ai.freya.app.FreyaApplication
import ai.freya.app.domain.model.JobItem
import ai.freya.app.domain.model.NotificationItem
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

class JobsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as FreyaApplication).container.settingsRepository

    private val _jobs = MutableStateFlow<List<JobItem>>(emptyList())
    val jobs: StateFlow<List<JobItem>> = _jobs.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

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
                _jobs.value = repo.jobs()
                _notifications.value = repo.notifications()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _busy.value = false
            }
        }
    }

    fun markRead(id: String) {
        viewModelScope.launch {
            runCatching { repo.markNotificationRead(id) }
        }
    }
}

@Composable
fun JobsScreen() {
    val vm: JobsViewModel = viewModel()
    val jobs by vm.jobs.collectAsStateWithLifecycle(initialValue = emptyList())
    val notifications by vm.notifications.collectAsStateWithLifecycle(initialValue = emptyList())
    val busy by vm.busy.collectAsStateWithLifecycle(initialValue = false)
    val error by vm.error.collectAsStateWithLifecycle()

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tasks & notifications", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.load() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }

            when {
                busy && jobs.isEmpty() && notifications.isEmpty() -> LoadingState(label = "Loading…")
                error != null && jobs.isEmpty() && notifications.isEmpty() ->
                    ErrorState(message = error.orEmpty(), onRetry = { vm.load() })
                jobs.isEmpty() && notifications.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Notifications,
                    title = "Nothing yet",
                    subtitle = "Long-running tasks and notifications will appear here.",
                )
                else -> {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (jobs.isNotEmpty()) {
                            item { Text("Jobs", style = MaterialTheme.typography.titleMedium) }
                            items(jobs, key = { it.id }) { job -> JobCard(job) }
                        }
                        if (notifications.isNotEmpty()) {
                            item { Text("Notifications", style = MaterialTheme.typography.titleMedium) }
                            items(notifications, key = { it.id }) { n ->
                                NotificationCard(n, onMarkRead = { vm.markRead(n.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JobCard(job: JobItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.status.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(job.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { (job.progress.coerceIn(0, 100)) / 100f }, modifier = Modifier.fillMaxWidth())
            job.error?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun NotificationCard(notification: NotificationItem, onMarkRead: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (notification.read) notification.title else "• ${notification.title}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    notification.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!notification.read) {
                TextButton(onClick = onMarkRead) { Text("Mark read") }
            }
        }
    }
}
