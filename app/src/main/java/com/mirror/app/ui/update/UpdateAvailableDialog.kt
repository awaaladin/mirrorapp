package com.mirror.app.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Shown once per launch when mirrorcentral reports a newer version than this build - there's no
 *  Play Store auto-update, so "update" always means opening the browser to download a new APK
 *  (see HANDOFF notes / landing page "How to install" guide for why it can't install directly).
 *  A mandatory release has no dismiss action - [onDismiss] simply isn't offered. */
@Composable
fun UpdateAvailableDialog(update: AvailableUpdate, onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!update.isMandatory) onDismiss() },
        title = { Text(if (update.isMandatory) "Update required" else "Update available") },
        text = {
            Column {
                Text("Mirror ${update.versionName} is ready.")
                if (update.releaseNotes.isNotBlank()) {
                    Text(update.releaseNotes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                if (update.isMandatory) {
                    Text(
                        "This version is required to keep using Mirror.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)))
                if (!update.isMandatory) onDismiss()
            }) { Text("DOWNLOAD UPDATE") }
        },
        dismissButton = if (update.isMandatory) null else {
            { TextButton(onClick = onDismiss) { Text("LATER") } }
        }
    )
}
