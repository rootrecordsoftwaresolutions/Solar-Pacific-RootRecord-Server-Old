package com.rootrecord.kilauea.alerts.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rootrecord.kilauea.alerts.ui.feedback.FeedbackViewModel

private val feedbackTypes = listOf(
    "general" to "General",
    "bug" to "Bug report",
    "feature" to "Feature request",
    "billing" to "Billing / account",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
    vm: FeedbackViewModel = hiltViewModel(),
) {
    val signedIn by vm.signedIn.collectAsState()
    val sending by vm.sending.collectAsState()
    val toast by vm.toast.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var typeKey by rememberSaveable { mutableStateOf("general") }
    var message by rememberSaveable { mutableStateOf("") }
    var replyEmail by rememberSaveable { mutableStateOf("") }
    var includeDiag by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(toast) {
        val t = toast ?: return@LaunchedEffect
        snackbar.showSnackbar(t)
        if (t.contains("sent", ignoreCase = true)) {
            message = ""
            replyEmail = ""
        }
        vm.consumeToast()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Submit Feedback") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Tell us what is working, what is broken, or what would make Kīlauea Alerts more useful.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!signedIn) {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Sign in under Menu with your Root Record account to submit feedback.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text("Type", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                feedbackTypes.forEach { (key, title) ->
                    FilterChip(
                        selected = typeKey == key,
                        onClick = { typeKey = key },
                        label = { Text(title) },
                        enabled = !sending,
                    )
                }
            }

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Your feedback") },
                placeholder = { Text("Describe what happened or what you would like…") },
                minLines = 5,
                singleLine = false,
                enabled = !sending,
            )

            OutlinedTextField(
                value = replyEmail,
                onValueChange = { replyEmail = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Reply-to email (optional)") },
                placeholder = { Text("you@example.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !sending,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Include device and app version (helps us reproduce issues)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = includeDiag,
                    onCheckedChange = { includeDiag = it },
                    enabled = !sending,
                )
            }

            Button(
                onClick = {
                    vm.send(
                        type = typeKey,
                        message = message,
                        replyEmail = replyEmail.ifBlank { null },
                        includeDiagnostics = includeDiag,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !sending && signedIn && message.isNotBlank(),
            ) {
                Text(if (sending) "Sending…" else "Submit Feedback")
            }
        }
    }
}
