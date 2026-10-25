package ai.freya.app.ui.screens.search

import androidx.compose.ui.graphics.Color
import ai.freya.app.FreyaApplication
import ai.freya.app.domain.model.SearchHit
import ai.freya.app.ui.components.EmptyState
import ai.freya.app.ui.components.ErrorState
import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

private val SCOPES = listOf("all", "conversations", "projects", "simulations", "reports")

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as FreyaApplication).container.searchRepository

    private val _results = MutableStateFlow<List<SearchHit>>(emptyList())
    val results: StateFlow<List<SearchHit>> = _results.asStateFlow()

    private val _scope = MutableStateFlow("all")
    val scope: StateFlow<String> = _scope.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setScope(value: String) {
        _scope.value = value
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            _results.value = emptyList()
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                _results.value = repo.search(q, _scope.value)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _busy.value = false
            }
        }
    }
}

@Composable
fun SearchScreen() {
    val vm: SearchViewModel = viewModel()
    val results by vm.results.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope by vm.scope.collectAsStateWithLifecycle(initialValue = "all")
    val busy by vm.busy.collectAsStateWithLifecycle(initialValue = false)
    val error by vm.error.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search FREYA…") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.search(query) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SCOPES.forEach { s ->
                    FilterChip(
                        selected = scope == s,
                        onClick = {
                            vm.setScope(s)
                            vm.search(query)
                        },
                        label = { Text(s) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                busy -> {
                    Spacer(Modifier.height(24.dp))
                    Text("Searching…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error != null -> ErrorState(message = error.orEmpty(), onRetry = { vm.search(query) })
                results.isEmpty() && query.isNotBlank() -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "No matches",
                    subtitle = "Try a different term or scope.",
                )
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(results, key = { "${it.type}:${it.id}" }) { hit ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        text = hit.type.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                    Text(hit.title, style = MaterialTheme.typography.titleMedium)
                                    if (hit.snippet.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = hit.snippet,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
