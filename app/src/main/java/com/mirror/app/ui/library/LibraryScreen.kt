package com.mirror.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mirror.app.domain.model.ShadeItem
import com.mirror.app.ui.upload.UploadViewModel

/**
 * The certified shade library - a full-catalog, searchable view over the same live backend
 * shade catalog the Editor's per-category swatches pull from (see UploadViewModel.shadeCatalog).
 * This is also where new shades added on the backend become visible/searchable/browsable in the
 * app without an app update; there's no separate "upload your own shade" flow yet since shades
 * are curated centrally (see mirrorcentral's admin console) rather than user-submitted.
 */
@Composable
fun LibraryScreen(viewModel: UploadViewModel) {
    val shadeCatalog by viewModel.shadeCatalog.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.refreshShadeCatalog() }

    val filteredByCategory = shadeCatalog.mapValues { (_, shades) ->
        if (searchQuery.isBlank()) shades
        else shades.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val totalCount = filteredByCategory.values.sumOf { it.size }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    "CERTIFIED SHADE LIBRARY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Shade Catalogue",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Synced from Mirror's servers - new certified shades show up here automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search all shades") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                colors = OutlinedTextFieldDefaults.colors()
            )
        }

        if (totalCount == 0) {
            item {
                Text(
                    "No shades match \"$searchQuery\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            filteredByCategory.forEach { (category, shades) ->
                if (shades.isEmpty()) return@forEach
                item {
                    Text(
                        category.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(shades) { shade -> ShadeRow(shade) }
            }
        }
    }
}

@Composable
private fun ShadeRow(shade: ShadeItem) {
    val color = remember(shade.hexColor) {
        try {
            Color(android.graphics.Color.parseColor(shade.hexColor))
        } catch (e: IllegalArgumentException) {
            Color.Gray
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(shade.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                shade.finish.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
