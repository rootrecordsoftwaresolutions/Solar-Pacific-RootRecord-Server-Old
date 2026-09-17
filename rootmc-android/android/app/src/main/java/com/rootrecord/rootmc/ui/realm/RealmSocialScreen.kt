package com.rootrecord.rootmc.ui.realm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootrecord.rootmc.R
import com.rootrecord.rootmc.ui.components.MinecraftCard
import com.rootrecord.rootmc.util.EntryTimestampFromApi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealmSocialScreen(
    onBack: () -> Unit,
    onOpenGroup: (String, String) -> Unit,
    viewModel: RealmSocialViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.realm_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.realm_lead),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            uiState.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.realm_profile_title), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = uiState.realmUsernameField,
                    onValueChange = viewModel::updateRealmUsername,
                    label = { Text(stringResource(R.string.realm_username_label)) },
                    placeholder = { Text(stringResource(R.string.realm_username_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.bioField,
                    onValueChange = viewModel::updateBio,
                    label = { Text(stringResource(R.string.realm_bio_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                uiState.profile?.profileUrl?.let { url ->
                    Text(url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Button(onClick = viewModel::saveProfile, enabled = !uiState.loading, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.realm_save_profile))
                }
            }

            MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.realm_find_friends), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchUsers(it)
                    },
                    label = { Text(stringResource(R.string.realm_search_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                uiState.searchResults.forEach { user ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(user.realmUsername ?: user.accountId, style = MaterialTheme.typography.titleSmall)
                        Button(onClick = { user.realmUsername?.let(viewModel::sendFriendRequest) }) {
                            Text(stringResource(R.string.realm_add_friend))
                        }
                    }
                }
            }

            Text(stringResource(R.string.realm_friends), style = MaterialTheme.typography.titleMedium)
            if (uiState.friends.isEmpty()) {
                Text(stringResource(R.string.realm_no_friends), style = MaterialTheme.typography.bodySmall)
            } else {
                uiState.friends.forEach { friend ->
                    Text(friend.realmUsername ?: friend.minecraftUsername ?: friend.accountId)
                }
            }

            MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.realm_groups), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        R.string.realm_group_quota,
                        uiState.groupLimits.ownedCount,
                        uiState.groupLimits.ownedMax,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
                OutlinedTextField(
                    value = uiState.newGroupName,
                    onValueChange = viewModel::updateNewGroupName,
                    label = { Text(stringResource(R.string.realm_new_group)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = viewModel::createGroup, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.realm_create_group))
                }
            }

            uiState.groups.forEach { group ->
                MinecraftCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenGroup(group.id, group.name) },
                ) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium)
                    Text(group.role, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupName: String,
    onBack: () -> Unit,
    viewModel: GroupChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(groupName) },
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
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    MinecraftCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            msg.realmUsername ?: msg.senderAccountId.take(8),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                        EntryTimestampFromApi(raw = msg.createdAt)
                    }
                }
            }
            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            RowChatInput(
                value = uiState.draft,
                onValueChange = viewModel::updateDraft,
                onSend = viewModel::sendMessage,
                sending = uiState.sending,
            )
        }
    }
}

@Composable
private fun RowChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.realm_message_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onSend, enabled = !sending && value.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (sending) stringResource(R.string.realm_sending) else stringResource(R.string.realm_send))
        }
    }
}
