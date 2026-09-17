package com.rootrecord.rootmc.ui.more

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootrecord.rootmc.ui.auth.PlayerLinkSection
import com.rootrecord.rootmc.BuildConfig
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.domain.model.MembershipTier
import com.rootrecord.rootmc.ui.components.MinecraftCard
import com.rootrecord.rootmc.ui.upsell.ROOTRECORD_BILLING_URL
import com.rootrecord.rootmc.ui.upsell.UpsellEvents

@Composable
fun MoreScreen(
    onFeedback: () -> Unit,
    onAuth: () -> Unit,
    onRealm: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val codeBusy by viewModel.codeBusy.collectAsStateWithLifecycle()
    val codeError by viewModel.codeError.collectAsStateWithLifecycle()
    val codePreviewUsername by viewModel.codePreviewUsername.collectAsStateWithLifecycle()
    val discordBusy by viewModel.discordBusy.collectAsStateWithLifecycle()
    val discordError by viewModel.discordError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var linkCodeField by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState.signedIn) {
        viewModel.refreshServerMembership()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.nav_more), style = MaterialTheme.typography.headlineSmall)

        MinecraftCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.account_section_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.account_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                uiState.membershipLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
            when {
                uiState.membershipTier == MembershipTier.Lifetime -> {
                    Text(
                        stringResource(R.string.membership_lifetime_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.membershipTier == MembershipTier.Pro -> {
                    Text(
                        stringResource(R.string.membership_pro_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.signedIn -> {
                    Text(
                        stringResource(R.string.membership_free_signed_in),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    Text(
                        stringResource(R.string.membership_guest_detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (uiState.signedIn && uiState.email != null) {
                Text(uiState.email!!, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                uiState.accountId?.let { id ->
                    Text(
                        "Account: $id",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                uiState.cloudSyncError?.let { err ->
                    Text(
                        stringResource(R.string.account_cloud_sync_error, err),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } ?: run {
                    val syncLabel = if (uiState.cloudSyncUpdatedAt > 0L) {
                        stringResource(
                            R.string.account_cloud_sync_last,
                            java.text.DateFormat.getDateTimeInstance(
                                java.text.DateFormat.SHORT,
                                java.text.DateFormat.SHORT,
                            ).format(java.util.Date(uiState.cloudSyncUpdatedAt)),
                        )
                    } else {
                        stringResource(R.string.account_cloud_sync_pending)
                    }
                    Text(
                        syncLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                TextButton(
                    onClick = { viewModel.syncNow() },
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(stringResource(R.string.account_sync_now))
                }
            }
            if (!uiState.adsRemoved) {
                OutlinedButton(
                    onClick = {
                        CustomTabsIntent.Builder().build().launchUrl(context, ROOTRECORD_BILLING_URL.toUri())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.view_membership))
                }
            }
            if (uiState.signedIn) {
                OutlinedButton(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
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
                onSignInWithCode = { viewModel.signInWithLinkCode(linkCodeField) },
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
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!uiState.adsRemoved) {
                TextButton(onClick = { UpsellEvents.trigger() }) {
                    Text(stringResource(R.string.membership_benefits_link))
                }
            }
        }

        MinecraftCard(modifier = Modifier.fillMaxWidth()) {
            Text("Theme: ${uiState.themeMode}", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { viewModel.cycleTheme(uiState.themeMode) }) {
                Text("Cycle theme (light / dark / system)")
            }
        }

        MinecraftCard(modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.analytics_opt_in))
            Switch(
                checked = uiState.analyticsOptIn,
                onCheckedChange = viewModel::setAnalyticsOptIn,
            )
        }

        HorizontalDivider()
        Text("Export", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = {
            viewModel.exportJson { json ->
                shareText(context, "rootmc-export.json", "application/json", json)
            }
        }) {
            Text("${stringResource(R.string.export_notes)} (JSON)")
        }
        TextButton(onClick = {
            viewModel.exportMarkdown { md ->
                shareText(context, "rootmc-export.md", "text/markdown", md)
            }
        }) {
            Text("${stringResource(R.string.export_notes)} (Markdown)")
        }

        HorizontalDivider()
        TextButton(onClick = onRealm) { Text(stringResource(R.string.realm_title)) }
        TextButton(onClick = onFeedback) { Text(stringResource(R.string.feedback)) }
        TextButton(onClick = onAuth) { Text(stringResource(R.string.account_full_screen)) }
        TextButton(onClick = {
            CustomTabsIntent.Builder().build().launchUrl(context, DISCORD_SUPPORT_URL.toUri())
        }) {
            Text(stringResource(R.string.discord_support))
        }

        HorizontalDivider()
        Text(
            "About RootMC v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 88.dp),
        )
    }
}

private fun shareText(context: android.content.Context, filename: String, mime: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_TEXT, content)
        putExtra(Intent.EXTRA_SUBJECT, filename)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_notes)))
}
