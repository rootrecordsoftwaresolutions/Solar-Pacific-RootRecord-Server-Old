package com.rootrecord.rootmc.ui.auth

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.domain.model.MembershipTier
import com.rootrecord.rootmc.ui.upsell.ROOTRECORD_BILLING_URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val discordBusy by viewModel.discordBusy.collectAsStateWithLifecycle()
    val discordError by viewModel.discordError.collectAsStateWithLifecycle()
    val codeBusy by viewModel.codeBusy.collectAsStateWithLifecycle()
    val codeError by viewModel.codeError.collectAsStateWithLifecycle()
    val codePreviewUsername by viewModel.codePreviewUsername.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var linkCodeField by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.signedIn) {
        viewModel.refreshServerMembership()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sign_in)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.account_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                uiState.membershipLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (uiState.signedIn) {
                uiState.emailDisplay?.takeIf { it.isNotBlank() }?.let {
                    Text("Signed in as $it")
                }
                uiState.accountId?.let {
                    Text("Account: $it", style = MaterialTheme.typography.bodySmall)
                }
                if (uiState.membershipTier == MembershipTier.SignedInFree) {
                    OutlinedButton(
                        onClick = {
                            CustomTabsIntent.Builder().build().launchUrl(context, ROOTRECORD_BILLING_URL.toUri())
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.view_membership))
                    }
                }
                TextButton(onClick = { viewModel.logout(); onBack() }) {
                    Text(stringResource(R.string.sign_out))
                }
            }
            PlayerLinkSection(
                signedIn = uiState.signedIn,
                minecraftLinked = uiState.minecraftLinked,
                minecraftUsername = uiState.minecraftUsername,
                sessionExpiresLabel = uiState.sessionExpiresLabel,
                linkCode = linkCodeField,
                onLinkCodeChange = {
                    linkCodeField = it
                    viewModel.onLinkCodeChanged(it)
                },
                codePreviewUsername = codePreviewUsername,
                codeBusy = codeBusy,
                codeError = codeError,
                onClearCodeError = viewModel::clearCodeError,
                onSignInWithCode = {
                    viewModel.signInWithLinkCode(linkCodeField) { onBack() }
                },
                discordBusy = discordBusy,
                discordError = discordError,
                onClearDiscordError = viewModel::clearDiscordError,
                onLinkWithDiscord = {
                    viewModel.startDiscordLink(linkCodeField) { url ->
                        CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
                    }
                },
                onSignInWithDiscord = {
                    viewModel.startDiscordSignIn { url ->
                        CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())
                    }
                },
            )
        }
    }
}
