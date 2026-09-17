package com.rootrecord.kilauea.alerts.ui.upsell

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val BILLING_URL = "https://rootrecord.online/billing"

/**
 * Membership dialog. Renders at the root of `MainActivity` and listens to [UpsellEvents].
 * Visibility is driven by:
 *   - `MainActivity.onCreate` — bumps the open counter once per real launch, then fires the
 *     trigger when the count is even (>= 2) and the user is free.
 *   - Feature gates (e.g. WeatherScreen non-Volcano tap) — fire the trigger directly.
 */
@Composable
fun UpsellOverlay() {
    val show by UpsellEvents.show.collectAsState()
    if (!show) return

    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = { UpsellEvents.dismiss() },
        title = { Text("Some features require additional resources") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Core access includes the Volcano view and Kīlauea volcano push alerts.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Some features are limited to members only:",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text("• All Big Island locations", style = MaterialTheme.typography.bodySmall)
                Text("• NWS, earthquake, and live-feed change alerts", style = MaterialTheme.typography.bodySmall)
                Text("• The web dashboard at kilauea.rootrecord.info", style = MaterialTheme.typography.bodySmall)
                Text("• Weather Manager and Business Manager member features too", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                UpsellEvents.dismiss()
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(BILLING_URL))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { ctx.startActivity(i) }
            }) {
                Text("View membership options")
            }
        },
        dismissButton = {
            TextButton(onClick = { UpsellEvents.dismiss() }) {
                Text("No thanks")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
