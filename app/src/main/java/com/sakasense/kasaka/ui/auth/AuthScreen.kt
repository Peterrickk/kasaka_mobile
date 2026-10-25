package com.sakasense.kasaka.ui.auth

import android.util.Patterns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sakasense.kasaka.LocalAppContainer
import com.sakasense.kasaka.data.SessionRepository
import com.sakasense.kasaka.ui.components.KCard
import com.sakasense.kasaka.ui.components.LogoBadge
import com.sakasense.kasaka.ui.theme.KasakaTheme
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val signUp = tab == 1

    fun submit(demo: Boolean = false) {
        error = null
        if (!demo) {
            val problem = when {
                signUp && name.isBlank() -> "Enter your name."
                !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."
                password.length < (if (signUp) 8 else 1) -> if (signUp) "Use at least 8 characters for your password." else "Enter your password."
                else -> null
            }
            if (problem != null) { error = problem; return }
        }
        busy = true
        scope.launch {
            val result = when {
                demo -> container.session.signIn(SessionRepository.DEMO_EMAIL, SessionRepository.DEMO_PASSWORD)
                signUp -> container.session.signUp(name, email, contact, password)
                else -> container.session.signIn(email, password)
            }
            result.onSuccess { user ->
                // The demo account comes with the sample farm; new accounts start empty.
                if (user.isDemo) container.soil.seedDemo() else if (signUp) container.soil.clearAll()
                onSignedIn()
            }.onFailure { error = it.message }
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        LogoBadge(72.dp)
        Spacer(Modifier.height(20.dp))
        Text("Welcome to Kasaka", style = MaterialTheme.typography.headlineMedium)
        Text("Smart soil intelligence by SakaSense", style = MaterialTheme.typography.bodyMedium, color = KasakaTheme.colors.muted)
        Spacer(Modifier.height(28.dp))

        SecondaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = tab == 0, onClick = { tab = 0; error = null }, text = { Text("Sign in") })
            Tab(selected = tab == 1, onClick = { tab = 1; error = null }, text = { Text("Create account") })
        }
        Spacer(Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedVisibility(signUp) {
                Field(name, { name = it }, "Full name", Icons.Rounded.Badge)
            }
            Field(email, { email = it }, "Email", Icons.Rounded.Email, KeyboardType.Email)
            AnimatedVisibility(signUp) {
                Field(contact, { contact = it }, "Mobile number (optional)", Icons.Rounded.Phone, KeyboardType.Phone)
            }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            if (showPassword) "Hide password" else "Show password",
                        )
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(if (signUp) "Create account" else "Sign in")
        }

        Spacer(Modifier.height(24.dp))
        KCard(Modifier.fillMaxWidth()) {
            Text("Just looking around?", style = MaterialTheme.typography.titleSmall)
            Text(
                "Explore a sample farm in Baybay, Leyte with 30 days of readings — the same demo as the SakaSense web dashboard.",
                style = MaterialTheme.typography.bodySmall, color = KasakaTheme.colors.muted,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { submit(demo = true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("Try the demo account")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Accounts are stored on this phone for now. Cloud sync with sakasense.com is coming.",
            style = MaterialTheme.typography.labelSmall, color = KasakaTheme.colors.muted,
        )
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector, type: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
