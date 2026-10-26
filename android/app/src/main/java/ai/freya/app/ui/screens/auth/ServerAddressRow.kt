package ai.freya.app.ui.screens.auth

import ai.freya.app.FreyaApplication
import ai.freya.app.data.remote.ServerAddress
import ai.freya.app.ui.theme.FreyaTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Shows which FREYA server the app talks to and lets the user change it — needed on a real
 * phone, where the emulator default (10.0.2.2) doesn't exist. "Test" calls the backend's
 * /health endpoint before anything is saved.
 */
@Composable
fun ServerAddressRow(modifier: Modifier = Modifier, startOpen: Boolean = false) {
    val context = LocalContext.current
    val dataStore = remember { (context.applicationContext as FreyaApplication).container.dataStore }
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(startOpen) }
    var current by remember { mutableStateOf(ServerAddress.current) }
    var text by remember { mutableStateOf(if (ServerAddress.isCustom) ServerAddress.current.removePrefix("http://") else "") }
    var status by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf<Boolean?>(null) }
    val c = FreyaTheme.colors

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Server: $current",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { open = !open }) { Text(if (open) "Close" else "Change") }
        }
        AnimatedVisibility(open) {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; status = null; ok = null },
                    label = { Text("Server address") },
                    placeholder = { Text("192.168.1.20:5001") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Your computer's Wi-Fi IP address with the backend port. The phone and computer must be on the same network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        val url = ServerAddress.normalize(text)
                        if (url == null) { ok = false; status = "That doesn't look like an address."; return@OutlinedButton }
                        status = "Testing…"; ok = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()
                                        .newCall(Request.Builder().url(url.resolve("health")!!).build()).execute()
                                        .use { it.isSuccessful }
                                }
                            }
                            ok = result.getOrDefault(false)
                            status = if (ok == true) "Connected to FREYA." else
                                "Can't reach ${url.host}:${url.port}. Is the backend running, and on the same Wi-Fi?"
                        }
                    }) { Text("Test") }
                    OutlinedButton(onClick = {
                        val url = ServerAddress.normalize(text)
                        if (url == null && text.isNotBlank()) { ok = false; status = "That doesn't look like an address."; return@OutlinedButton }
                        val value = url?.toString()?.trimEnd('/')
                        ServerAddress.set(value)
                        scope.launch { dataStore.setServerUrl(value) }
                        current = ServerAddress.current
                        status = if (value == null) "Using the default server." else "Saved."
                        ok = true
                        open = false
                    }) { Text("Save") }
                }
            }
        }
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = when (ok) { true -> c.success; false -> c.error; null -> MaterialTheme.colorScheme.onSurfaceVariant },
                textAlign = TextAlign.Center,
            )
        }
    }
}
