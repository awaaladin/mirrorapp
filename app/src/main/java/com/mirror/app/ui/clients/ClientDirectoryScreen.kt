package com.mirror.app.ui.clients

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecentActors
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mirror.app.domain.model.ClientProfile
import com.mirror.app.domain.model.ClientTag

@Composable
fun ClientDirectoryScreen(viewModel: ClientDirectoryViewModel, onNewConsultation: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

    val filtered = remember(uiState.clients, query) {
        if (query.isBlank()) uiState.clients
        else uiState.clients.filter { it.name.contains(query, ignoreCase = true) || it.notes.contains(query, ignoreCase = true) }
    }

    if (showAddDialog) {
        AddClientDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { name, notes, tag -> viewModel.addClient(name, notes, tag); showAddDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("DIRECTORY · ATELIER V4", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Studio Consultations", style = MaterialTheme.typography.headlineSmall, fontStyle = FontStyle.Italic)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by name or notes...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent
                )
            )
        }
        if (uiState.errorMessage != null) {
            item {
                Text(
                    uiState.errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (uiState.isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (filtered.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.RecentActors, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
                    Text(
                        if (query.isBlank()) "No client consultations yet." else "No clients match \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    if (query.isBlank()) {
                        Text(
                            "Add a client to start tracking their formulations and consultation history.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { client ->
                ClientRow(client = client, onDelete = { viewModel.deleteClient(client.id) })
            }
        }
        item {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("NEW CLIENT CONSULTATION", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
        item {
            TextButton(onClick = onNewConsultation, modifier = Modifier.fillMaxWidth()) {
                Text("START A LOOK IN THE STUDIO")
            }
        }
    }
}

@Composable
private fun ClientRow(client: ClientProfile, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(client.name, style = MaterialTheme.typography.bodyLarge)
            if (client.notes.isNotBlank()) {
                Text(client.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Remove client", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddClientDialog(onDismiss: () -> Unit, onCreate: (name: String, notes: String, tag: ClientTag) -> Unit) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf(ClientTag.CLIENT) }
    var tagMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New client consultation") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("CLIENT NAME") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("NOTES (undertone, wedding date, etc.)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text("TAG", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                            .clickable { tagMenuExpanded = true }
                            .padding(12.dp)
                    ) {
                        Text(if (tag == ClientTag.SELF) "Self" else "Client", color = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(expanded = tagMenuExpanded, onDismissRequest = { tagMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Client") }, onClick = { tag = ClientTag.CLIENT; tagMenuExpanded = false })
                        DropdownMenuItem(text = { Text("Self") }, onClick = { tag = ClientTag.SELF; tagMenuExpanded = false })
                    }
                }
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) error = "Enter a client name." else onCreate(name, notes, tag)
            }) { Text("ADD") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}
