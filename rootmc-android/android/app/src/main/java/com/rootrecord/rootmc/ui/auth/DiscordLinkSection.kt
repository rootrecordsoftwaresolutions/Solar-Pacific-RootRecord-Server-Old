package com.rootrecord.rootmc.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rootrecord.rootmc.R

@Composable
fun PlayerLinkSection(
    signedIn: Boolean,
    minecraftLinked: Boolean,
    minecraftUsername: String?,
    sessionExpiresLabel: String?,
    linkCode: String,
    onLinkCodeChange: (String) -> Unit,
    codePreviewUsername: String?,
    codeBusy: Boolean,
    codeError: String?,
    onClearCodeError: () -> Unit,
    onSignInWithCode: () -> Unit,
    discordBusy: Boolean,
    discordError: String?,
    onClearDiscordError: () -> Unit,
    onLinkWithDiscord: () -> Unit,
    onSignInWithDiscord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.player_link_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.player_link_lead),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (signedIn && minecraftLinked && !minecraftUsername.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(
                    model = "https://minotar.net/helm/${minecraftUsername}/100.png",
                    contentDescription = stringResource(R.string.discord_linked_avatar_cd, minecraftUsername),
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Fit,
                )
                Column {
                    Text(
                        stringResource(R.string.discord_linked_as, minecraftUsername),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    sessionExpiresLabel?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (!signedIn) {
            OutlinedTextField(
                value = linkCode,
                onValueChange = {
                    onLinkCodeChange(it.filter { ch -> ch.isLetterOrDigit() }.take(6).uppercase())
                    onClearCodeError()
                },
                label = { Text(stringResource(R.string.discord_link_code_label)) },
                placeholder = { Text(stringResource(R.string.discord_link_code_hint)) },
                singleLine = true,
                enabled = !codeBusy && !discordBusy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                codePreviewUsername != null -> {
                    Text(
                        stringResource(R.string.link_code_preview, codePreviewUsername),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                linkCode.length == 6 -> {
                    Text(
                        stringResource(R.string.link_code_checking),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            codeError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onSignInWithCode,
                enabled = !codeBusy && !discordBusy && linkCode.length == 6,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (codeBusy) {
                        stringResource(R.string.link_code_signing_in)
                    } else {
                        stringResource(R.string.link_code_sign_in)
                    },
                )
            }
            Text(
                stringResource(R.string.link_code_session_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                stringResource(R.string.player_link_discord_optional),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            discordError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = onLinkWithDiscord,
                enabled = !codeBusy && !discordBusy && linkCode.length == 6,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (discordBusy) {
                        stringResource(R.string.discord_link_busy)
                    } else {
                        stringResource(R.string.discord_link_action)
                    },
                )
            }
            OutlinedButton(
                onClick = onSignInWithDiscord,
                enabled = !codeBusy && !discordBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.discord_sign_in_action))
            }
        } else if (!minecraftLinked) {
            Text(
                stringResource(R.string.discord_not_linked_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** @deprecated Use [PlayerLinkSection] */
@Composable
fun DiscordLinkSection(
    minecraftLinked: Boolean,
    minecraftUsername: String?,
    linkCode: String,
    onLinkCodeChange: (String) -> Unit,
    discordBusy: Boolean,
    discordError: String?,
    onClearError: () -> Unit,
    onLinkWithDiscord: () -> Unit,
    onSignInWithDiscord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerLinkSection(
        signedIn = minecraftLinked,
        minecraftLinked = minecraftLinked,
        minecraftUsername = minecraftUsername,
        sessionExpiresLabel = null,
        linkCode = linkCode,
        onLinkCodeChange = onLinkCodeChange,
        codePreviewUsername = null,
        codeBusy = false,
        codeError = null,
        onClearCodeError = onClearError,
        onSignInWithCode = {},
        discordBusy = discordBusy,
        discordError = discordError,
        onClearDiscordError = onClearError,
        onLinkWithDiscord = onLinkWithDiscord,
        onSignInWithDiscord = onSignInWithDiscord,
        modifier = modifier,
    )
}
