package com.rootrecord.rootmc.ui.upsell

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rootrecord.rootmc.R

const val ROOTRECORD_BILLING_URL = "https://rootmc.net/"
const val ROOTRECORD_ACCOUNT_URL = "https://rootmc.net/"

@Composable
fun UpsellOverlay() {
    val show by UpsellEvents.show.collectAsState()
    if (!show) return

    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = { UpsellEvents.dismiss() },
        title = { Text("RootMC membership") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.upsell_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("• ${stringResource(R.string.upsell_perk_ads)}", style = MaterialTheme.typography.bodySmall)
                Text("• ${stringResource(R.string.upsell_perk_account)}", style = MaterialTheme.typography.bodySmall)
                Text("• ${stringResource(R.string.upsell_perk_sync)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                UpsellEvents.dismiss()
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ROOTRECORD_BILLING_URL))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(intent) }
            }) {
                Text(stringResource(R.string.view_membership))
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { UpsellEvents.navigateToSignIn() }) {
                    Text(stringResource(R.string.upsell_sign_in))
                }
                TextButton(onClick = { UpsellEvents.dismiss() }) {
                    Text(stringResource(R.string.upsell_continue_guest))
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
