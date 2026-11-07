package com.catsblock.weather.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.catsblock.weather.i18n.tr

private const val POLICY_NOTICE_VERSION = 2026110701
private const val PREFS = "policy_notice"
private const val KEY_ACK = "acknowledged_version"

private fun hasAcknowledged(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ACK, 0) >= POLICY_NOTICE_VERSION

private fun acknowledge(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_ACK, POLICY_NOTICE_VERSION).apply()
}

@Composable
fun PolicyUpdateDialog(onOpenTerms: () -> Unit, onOpenPrivacy: () -> Unit) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(!hasAcknowledged(context)) }
    if (!visible) return
    val dismiss = {
        acknowledge(context)
        visible = false
    }
    AlertDialog(
        onDismissRequest = dismiss,
        shape = RoundedCornerShape(4.dp),
        title = { Text(tr("Policy update")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(tr("We updated our Terms of Service and Privacy Policy."), style = MaterialTheme.typography.bodyMedium)
                Text(
                    tr("To keep the service reliable and within its usage limits, we now monitor our infrastructure in the background. This monitoring uses protected, pseudonymous identifiers and aggregated usage counts. It never includes your name, precise location or messages."),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    tr("Your personal data is strictly private. It is never sold or shared with third parties."),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { dismiss(); onOpenTerms() }, modifier = Modifier) { Text(tr("Read the Terms of Service")) }
                TextButton(onClick = { dismiss(); onOpenPrivacy() }, modifier = Modifier) { Text(tr("Read the Privacy Policy")) }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(tr("Got it")) } }
    )
}
