package com.mirror.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * "Create your own shade" - a plain name + hex-color form rather than a full HSV color wheel:
 * hex is the exact, unambiguous way to specify a color, and it's how the rest of the catalog
 * (bundled + backend) is already represented, so no extra color-space conversion is needed.
 * Available to every account tier since custom shades are stored locally (see CustomShadeStore),
 * not tied to a Pro entitlement.
 */
@Composable
fun AddCustomShadeDialog(
    initialCategory: EditorCategory,
    onDismiss: () -> Unit,
    onCreate: (category: EditorCategory, name: String, hexColor: String) -> Boolean
) {
    // Lashes has no color category (see EditorCategory.toShadeCategory) - fall back to Lips
    // if the dialog was somehow opened while Lashes was the active tab.
    var category by remember { mutableStateOf(if (initialCategory == EditorCategory.LASHES) EditorCategory.LIPS else initialCategory) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var hex by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val previewColor = remember(hex) {
        val normalized = if (hex.startsWith("#")) hex else "#$hex"
        try {
            Color(android.graphics.Color.parseColor(normalized))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a custom shade") },
        text = {
            Column {
                Text("CATEGORY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                            .clickable { categoryMenuExpanded = true }
                            .padding(12.dp)
                    ) {
                        Text(category.label, color = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                        EditorCategory.entries.filter { it != EditorCategory.LASHES }.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { category = option; categoryMenuExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("SHADE NAME") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = it; error = null },
                    label = { Text("HEX COLOR (e.g. #B85C38)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(previewColor ?: MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
                    )
                    Text(
                        if (previewColor != null) "Preview" else "Enter a valid hex color",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val created = onCreate(category, name, hex)
                if (created) onDismiss() else error = "Enter a shade name and a valid hex color."
            }) {
                Text("CREATE")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}
