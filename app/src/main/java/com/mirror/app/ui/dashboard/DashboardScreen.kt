package com.mirror.app.ui.dashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mirror.app.data.network.NetworkConfig
import com.mirror.app.domain.model.ProPlan
import com.mirror.app.ui.theme.MirrorColorTokens
import com.mirror.app.ui.upload.SavedLook

private enum class StudioMode { SELF, CLIENT }
private enum class UpsellReason { CLIENT_MODE, SHOWROOM, LIVE_SIMULATION }

@Composable
fun DashboardScreen(
    savedLooks: List<SavedLook>,
    proPlan: ProPlan,
    onStartSimulation: () -> Unit,
    onOpenClients: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenShowroom: () -> Unit,
    onOpenLook: (SavedLook) -> Unit,
    onDeleteLook: (SavedLook) -> Unit
) {
    var mode by remember { mutableStateOf(StudioMode.SELF) }
    var upsellReason by remember { mutableStateOf<UpsellReason?>(null) }
    var lookSearchQuery by remember { mutableStateOf("") }
    var showSimulateInfo by remember { mutableStateOf(false) }

    if (showSimulateInfo) {
        AlertDialog(
            onDismissRequest = { showSimulateInfo = false },
            confirmButton = { TextButton(onClick = { showSimulateInfo = false }) { Text("GOT IT") } },
            title = { Text("Simulate is still being designed") },
            text = { Text("Background removal and relighting need a real on-device segmentation model, not just a color overlay - that's a bigger build than a quick fix. It's tracked as its own feature, not shipped yet.") }
        )
    }
    val hasClientMode = proPlan.hasClientMode
    val context = LocalContext.current
    val filteredLooks = if (lookSearchQuery.isBlank()) {
        savedLooks
    } else {
        savedLooks.filter { it.name.contains(lookSearchQuery, ignoreCase = true) }
    }

    upsellReason?.let { reason ->
        // Client Mode and Showroom are paid plans, so the dialog's confirm button leads to them.
        // Live Simulation isn't built yet regardless of payment, so it's dismiss-only.
        val (title, body, confirmLabel) = when (reason) {
            UpsellReason.CLIENT_MODE -> Triple(
                "Client Mode is part of Mirror Pro Studio",
                "Manage client profiles and run bridal & editorial consultations. Mirror Pro is coming soon - see what's planned on mirror's website.",
                "SEE MIRROR PRO"
            )
            UpsellReason.SHOWROOM -> Triple(
                "Showroom is part of Mirror Pro",
                "Showroom reads your skin tone and undertone from a portrait and suggests shades for you. Mirror Pro is coming soon - see what's planned on mirror's website.",
                "SEE MIRROR PRO"
            )
            UpsellReason.LIVE_SIMULATION -> Triple(
                "Live Simulation is a Pro feature",
                "Real-time camera simulation is a Pro capability that's coming in a later release. For now, IMPORT lets you upload a portrait and formulate a look in the Studio - that part works today, on any account.",
                "GOT IT"
            )
        }
        AlertDialog(
            onDismissRequest = { upsellReason = null },
            confirmButton = {
                TextButton(onClick = {
                    if (reason != UpsellReason.LIVE_SIMULATION) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(NetworkConfig.PRICING_URL)))
                    }
                    upsellReason = null
                }) { Text(confirmLabel) }
            },
            dismissButton = if (reason != UpsellReason.LIVE_SIMULATION) {
                { TextButton(onClick = { upsellReason = null }) { Text("NOT NOW") } }
            } else null,
            title = { Text(title) },
            text = { Text(body) }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text(
                        "NIGERIAN ATELIER EDITION",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Studio Overview",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                // A full-width row of its own - rather than squeezed beside the headline -
                // so the labels never get forced into a column of single wrapped characters.
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                        .padding(4.dp)
                ) {
                    listOf(StudioMode.SELF to "Self", StudioMode.CLIENT to "Client").forEach { (value, label) ->
                        val selected = mode == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(MaterialTheme.shapes.small)
                                .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable {
                                    if (value == StudioMode.CLIENT && !hasClientMode) {
                                        upsellReason = UpsellReason.CLIENT_MODE
                                    } else {
                                        mode = value
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.inverseSurface, MaterialTheme.shapes.large)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Text(
                        "ON-DEVICE FORMULATION ENGINE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MirrorColorTokens.PrimaryFixedDim,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Column {
                    Text(
                        if (mode == StudioMode.CLIENT) "New Client Look" else "Create New Look",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                    Text(
                        if (mode == StudioMode.CLIENT)
                            "Capture or upload a client's portrait to start a bridal or editorial consultation, fully offline."
                        else "Capture or upload a portrait to start a face-landmark-guided makeup simulation, fully offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        // Real-time camera simulation isn't built yet (see task backlog) - this
                        // used to silently do the same thing as IMPORT, which was misleading.
                        onClick = { upsellReason = UpsellReason.LIVE_SIMULATION },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            "PRO NEEDED",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    OutlinedButton(onClick = onStartSimulation, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(18.dp))
                        Text(
                            "IMPORT",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                OutlinedButton(onClick = { if (proPlan.hasShowroom) onOpenShowroom() else upsellReason = UpsellReason.SHOWROOM }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(18.dp))
                    Text(
                        "SHOWROOM · AI SKIN ANALYSIS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        item {
            Text("Recent Looks", style = MaterialTheme.typography.headlineSmall)
        }
        if (savedLooks.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = lookSearchQuery,
                    onValueChange = { lookSearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search saved looks") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    colors = OutlinedTextFieldDefaults.colors()
                )
            }
        }
        if (savedLooks.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.AutoAwesome,
                    message = "No looks yet. Start a simulation above and your saved looks will appear here."
                )
            }
        } else if (filteredLooks.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.AutoAwesome,
                    message = "No looks match \"$lookSearchQuery\"."
                )
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredLooks) { look ->
                        SavedLookCard(
                            look = look,
                            onEdit = { onOpenLook(look) },
                            onSimulate = { showSimulateInfo = true },
                            onDelete = { onDeleteLook(look) }
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Atelier Vaults & Archives",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            ArchiveRow(
                icon = Icons.Filled.FolderShared,
                iconBg = MirrorColorTokens.SecondaryFixed,
                iconTint = MirrorColorTokens.OnSecondaryFixed,
                title = "Client Mode Directory",
                subtitle = if (hasClientMode) "Manage saved client profiles" else "Part of Mirror Pro Studio",
                locked = !hasClientMode,
                onClick = { if (hasClientMode) onOpenClients() else upsellReason = UpsellReason.CLIENT_MODE }
            )
        }
        item {
            ArchiveRow(
                icon = Icons.Filled.Colorize,
                iconBg = MirrorColorTokens.PrimaryFixed,
                iconTint = MirrorColorTokens.OnPrimaryFixed,
                title = "Certified Shade Library",
                subtitle = "Browse foundation, lip & eye shades",
                onClick = onOpenLibrary
            )
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(28.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ArchiveRow(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    locked: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(iconBg, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint)
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    if (locked) {
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("PRO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            if (locked) Icons.Filled.Lock else Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SavedLookCard(
    look: SavedLook,
    onEdit: () -> Unit,
    onSimulate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(120.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
            .clickable(onClick = onEdit)
            .padding(8.dp)
    ) {
        Box {
            Image(
                bitmap = look.thumbnail.asImageBitmap(),
                contentDescription = look.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .aspectRatio(4f / 5f),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Box {
                androidx.compose.material3.IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.MoreVert,
                        contentDescription = "Look options",
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
                androidx.compose.material3.DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("EDIT") },
                        onClick = { menuExpanded = false; onEdit() }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("SIMULATE") },
                        onClick = { menuExpanded = false; onSimulate() }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("DELETE", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
        Text(
            look.name,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
