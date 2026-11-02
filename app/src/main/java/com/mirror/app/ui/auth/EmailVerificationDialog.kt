package com.mirror.app.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** Asks for the 6-digit code from the welcome email (or a fresh one). */
@Composable
fun EmailVerificationDialog(
    busy: Boolean,
    message: String?,
    onVerify: (code: String) -> Unit,
    onSendNewCode: () -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm your email") },
        text = {
            Column {
                Text(
                    "Free photos and Showroom need a confirmed email. Enter the 6-digit code from your welcome email - check spam too.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { value -> code = value.filter { it.isDigit() }.take(6) },
                    placeholder = { Text("123456") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                TextButton(onClick = onSendNewCode, enabled = !busy) { Text("SEND A NEW CODE") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onVerify(code) }, enabled = !busy && code.length == 6) { Text("CONFIRM") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("LATER") } }
    )
}
