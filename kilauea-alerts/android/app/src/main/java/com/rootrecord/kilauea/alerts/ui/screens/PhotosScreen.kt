package com.rootrecord.kilauea.alerts.ui.screens

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.rootrecord.kilauea.alerts.ui.components.DisclaimerBanner
import com.rootrecord.kilauea.alerts.ui.photos.PhotosViewModel
import java.io.File
import java.io.FileOutputStream

private const val BILLING_URL = "https://rootrecord.online/billing"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    onBack: () -> Unit,
    vm: PhotosViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val signedIn by vm.signedIn.collectAsState()
    val proUnlocked by vm.proUnlocked.collectAsState()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.load() }

    LaunchedEffect(state.toast) {
        val msg = state.toast ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        vm.consumeToast()
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val f = copyToTempFile(ctx, uri) ?: run {
            vm.consumeToast()
            return@rememberLauncherForActivityResult
        }
        vm.upload(f, caption = null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Volcano photos") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DisclaimerBanner(compact = false)
            Text(
                "Submit your volcano photography. All images require approval before they appear in the public gallery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Three-state ladder for the submission control:
            //   not signed in   → sign-in nudge (gallery still browsable below)
            //   signed in, standard access → membership pitch (gating per product decision)
            //   member  → real "Submit photo" button
            // We deliberately don't render a disabled submit button for non-Pro users: it reads as
            // "broken" rather than "locked", which is exactly the confusion that prompted this gate.
            when {
                !signedIn -> {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Sign in required", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "To submit a photo, sign in on the Menu tab with your Root Record account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                !proUnlocked -> {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Photo submissions require additional resources", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "The public gallery stays open to browse. Submitting photos uses storage and review resources, so submissions are limited to members only.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    CustomTabsIntent.Builder().build()
                                        .launchUrl(ctx, Uri.parse(BILLING_URL))
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("View membership options")
                            }
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = { pick.launch("image/*") },
                        enabled = !state.uploading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Text("Submit photo", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            TextButton(onClick = { vm.load() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.loading) "Refreshing…" else "Refresh gallery")
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.items, key = { it.id }) { item ->
                    Card {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AsyncImage(
                                model = item.url,
                                contentDescription = item.caption,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            item.caption?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            item.createdAt?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyToTempFile(ctx: Context, uri: Uri): File? {
    return runCatching {
        val name = queryDisplayName(ctx, uri) ?: "upload.jpg"
        val f = File(ctx.cacheDir, "photo_${System.currentTimeMillis()}_$name")
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            FileOutputStream(f).use { outs -> ins.copyTo(outs) }
        } ?: return null
        f
    }.getOrNull()
}

private fun queryDisplayName(ctx: Context, uri: Uri): String? {
    val c: Cursor? = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    c?.use {
        if (it.moveToFirst()) return it.getString(0)
    }
    return null
}

