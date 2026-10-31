package ai.freya.app.ui.screens.auth

import ai.freya.app.domain.CoreState
import ai.freya.app.ui.core.CoreAppearance
import ai.freya.app.ui.core.FreyaCore
import ai.freya.app.domain.Persona
import ai.freya.app.ui.components.NeumorphicButton
import ai.freya.app.ui.components.NeumorphicField
import ai.freya.app.ui.theme.FreyaTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onGoToRegister: () -> Unit,
) {
    val vm: AuthViewModel = viewModel()
    val loading by vm.loading.collectAsStateWithLifecycle(initialValue = false)
    val error by vm.error.collectAsStateWithLifecycle()
    val success by vm.success.collectAsStateWithLifecycle(initialValue = false)

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(success) {
        if (success) onLoggedIn()
    }

    AuthScaffold(
        title = "Sign in",
        subtitle = Persona.TAGLINE,
    ) {
        NeumorphicField(
            value = email,
            onValueChange = { email = it },
            placeholder = "Email",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        NeumorphicField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Password",
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(20.dp))
        NeumorphicButton(
            label = if (loading) "Signing in…" else "Sign in",
            onClick = { vm.login(email, password) },
            enabled = !loading,
            tint = FreyaTheme.colors.violet,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onGoToRegister,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("New to FREYA? Create an account")
        }
    }
}

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val vm: AuthViewModel = viewModel()
    val loading by vm.loading.collectAsStateWithLifecycle(initialValue = false)
    val error by vm.error.collectAsStateWithLifecycle()
    val success by vm.success.collectAsStateWithLifecycle(initialValue = false)

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(success) {
        if (success) onRegistered()
    }

    AuthScaffold(
        title = "Create account",
        subtitle = "Your intelligence, your world.",
    ) {
        NeumorphicField(
            value = displayName,
            onValueChange = { displayName = it },
            placeholder = "Display name",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        NeumorphicField(
            value = email,
            onValueChange = { email = it },
            placeholder = "Email",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        NeumorphicField(
            value = password,
            onValueChange = { password = it },
            placeholder = "Password",
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(20.dp))
        NeumorphicButton(
            label = if (loading) "Creating…" else "Create account",
            onClick = { vm.register(email, password, displayName) },
            enabled = !loading,
            tint = FreyaTheme.colors.violet,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onBackToLogin,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Already have an account? Sign in")
        }
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        FreyaCore(
            state = CoreState.IDLE,
            appearance = CoreAppearance.Compact,
            compact = true,
            modifier = Modifier.size(170.dp),
            animate = false,
        )
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            content()
        }
        Spacer(Modifier.height(20.dp))
        ServerAddressRow()
    }
}
