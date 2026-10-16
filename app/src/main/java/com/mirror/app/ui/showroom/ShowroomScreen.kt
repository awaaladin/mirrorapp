package com.mirror.app.ui.showroom

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mirror.app.domain.model.FaceAnalysis
import com.mirror.app.domain.model.ShadeItem
import com.mirror.app.ui.editor.EditorCategory
import com.mirror.app.ui.editor.FaceCanvas
import com.mirror.app.ui.editor.MakeupSelection
import com.mirror.app.ui.editor.cropToFace
import com.mirror.app.ui.editor.resolveMakeupLayer
import com.mirror.app.ui.upload.UploadErrorMessage
import com.mirror.app.ui.upload.UploadUiState
import kotlinx.coroutines.delay

/**
 * A separate, self-contained screen: upload a portrait, Gemini reads the skin tone and suggests
 * shades, and the photo stays exactly as uploaded until the user taps a shade or moves a slider.
 * It never touches the Studio's state or saved looks.
 */
@Composable
fun ShowroomScreen(viewModel: ShowroomViewModel, onBack: () -> Unit, onSaveLook: (String, ShowroomSnapshot) -> Unit) {
    val photoState by viewModel.photoState.collectAsState()
    val analysis by viewModel.analysis.collectAsState()
    val selections by viewModel.selections.collectAsState()
    val catalog by viewModel.catalog.collectAsState()
    val aiPickIds by viewModel.aiPickIds.collectAsState()
    val context = LocalContext.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onPhotoPicked(it, context.contentResolver) }
    }
    fun launchPicker() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    var category by remember { mutableStateOf(EditorCategory.FOUNDATION) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Showroom", style = MaterialTheme.typography.titleLarge)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            when (val state = photoState) {
                is UploadUiState.Idle -> ShowroomPrompt(onUpload = ::launchPicker)
                is UploadUiState.Detecting -> CenteredProgress("Detailing your photo…")
                is UploadUiState.Detected -> {
                    val faceCrop = remember(state.photo) { cropToFace(state.photo, state.landmarks) }
                    FaceCanvas(
                        photo = faceCrop.bitmap,
                        landmarks = faceCrop.landmarks,
                        // Resolved from the collected `selections`/`catalog` State (not by asking
                        // the ViewModel) so this part of the screen recomposes when a shade is
                        // tapped - reading the StateFlow's raw value here left the photo frozen.
                        foundation = resolveMakeupLayer(EditorCategory.FOUNDATION, selections, catalog),
                        cheek = resolveMakeupLayer(EditorCategory.CHEEK, selections, catalog),
                        lips = resolveMakeupLayer(EditorCategory.LIPS, selections, catalog),
                        brow = resolveMakeupLayer(EditorCategory.BROWS, selections, catalog),
                        eyeColor = resolveMakeupLayer(EditorCategory.EYE_COLOR, selections, catalog),
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
                is UploadUiState.Error -> ShowroomError(state.message, onRetry = ::launchPicker)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .background(MaterialTheme.colorScheme.inverseSurface),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (photoState is UploadUiState.Detected) {
                item { AnalysisCard(analysis = analysis, onRetry = viewModel::retryAnalysis) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        showroomCategories.forEach { cat ->
                            Text(
                                cat.label.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (category == cat) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                                modifier = Modifier.clickable { category = cat }.padding(bottom = 4.dp)
                            )
                        }
                    }
                }

                item {
                    val shades = catalog[category].orEmpty()
                    val selection = selections[category] ?: MakeupSelection()
                    val activeShade = shades.firstOrNull { it.id == selection.shadeId }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                activeShade?.name?.uppercase() ?: "TAP A SHADE TO TRY IT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
                            )
                            if (activeShade != null) {
                                TextButton(onClick = { viewModel.clearShade(category) }) {
                                    Text("REMOVE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            shades.forEach { shade ->
                                ShowroomSwatch(
                                    shade = shade,
                                    selected = shade.id == activeShade?.id,
                                    aiPick = shade.id in aiPickIds,
                                    onClick = {
                                        if (shade.id == activeShade?.id) viewModel.clearShade(category)
                                        else viewModel.selectShade(category, shade.id)
                                    }
                                )
                            }
                        }
                        if (aiPickIds.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                Box(Modifier.size(10.dp).border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape))
                                Text(
                                    " = AI pick for your skin tone",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                item {
                    val selection = selections[category] ?: MakeupSelection()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.06f), MaterialTheme.shapes.small)
                            .padding(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "PIGMENT SATURATION",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                "${(selection.intensity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        }
                        Slider(
                            value = selection.intensity,
                            onValueChange = { viewModel.setIntensity(category, it) },
                            valueRange = 0.1f..1f,
                            enabled = selection.shadeId != null
                        )
                    }
                }

                item {
                    var justSaved by remember { mutableStateOf(false) }
                    var showSaveDialog by remember { mutableStateOf(false) }
                    var saveName by remember { mutableStateOf("") }
                    val hasMakeup = selections.values.any { it.shadeId != null }
                    LaunchedEffect(justSaved) { if (justSaved) { delay(1600); justSaved = false } }

                    if (showSaveDialog) {
                        AlertDialog(
                            onDismissRequest = { showSaveDialog = false },
                            title = { Text("Name this look") },
                            text = {
                                OutlinedTextField(
                                    value = saveName,
                                    onValueChange = { saveName = it },
                                    placeholder = { Text("e.g. Bridal Glam") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.snapshot()?.let { onSaveLook(saveName, it) }
                                    showSaveDialog = false
                                    saveName = ""
                                    justSaved = true
                                }) { Text("SAVE") }
                            },
                            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("CANCEL") } }
                        )
                    }

                    Button(
                        onClick = { showSaveDialog = true },
                        enabled = hasMakeup,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            when {
                                justSaved -> "SAVED TO RECENT LOOKS"
                                hasMakeup -> "SAVE LOOK"
                                else -> "PICK A SHADE TO SAVE"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                item {
                    Text(
                        "Your photo is sent securely to be analysed and is not stored. Makeup is only applied when you tap a shade.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisCard(analysis: AnalysisState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.06f), MaterialTheme.shapes.small)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "  AI SKIN ANALYSIS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f)
            )
        }
        when (analysis) {
            is AnalysisState.Idle, is AnalysisState.Loading -> Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    "  Reading your skin tone and undertone…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f)
                )
            }
            is AnalysisState.Failed -> {
                Text(
                    analysis.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
                TextButton(onClick = onRetry) { Text("TRY AGAIN", style = MaterialTheme.typography.labelSmall) }
            }
            is AnalysisState.Ready -> AnalysisSummary(analysis.analysis)
        }
    }
}

@Composable
private fun AnalysisSummary(analysis: FaceAnalysis) {
    val textColor = MaterialTheme.colorScheme.inverseOnSurface
    Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        SkinDot(analysis.skinHex)
        Text(
            "  Depth ${analysis.skinDepth}/10 · ${analysis.undertone.replaceFirstChar { it.uppercase() }} undertone · ${analysis.faceShape.replaceFirstChar { it.uppercase() }} face",
            style = MaterialTheme.typography.bodySmall,
            color = textColor
        )
    }
    if (analysis.summary.isNotBlank()) {
        Text(
            analysis.summary,
            style = MaterialTheme.typography.bodySmall,
            color = textColor.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
    analysis.photoWarnings.forEach { warning ->
        Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
            Text("  $warning", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun SkinDot(hex: String) {
    val color = remember(hex) {
        try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: IllegalArgumentException) {
            Color.Gray
        }
    }
    Box(Modifier.size(18.dp).clip(CircleShape).background(color))
}

@Composable
private fun ShowroomSwatch(shade: ShadeItem, selected: Boolean, aiPick: Boolean, onClick: () -> Unit) {
    val color = remember(shade.hexColor) {
        try {
            Color(android.graphics.Color.parseColor(shade.hexColor))
        } catch (e: IllegalArgumentException) {
            Color.Gray
        }
    }
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color)
                .then(if (aiPick) Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape) else Modifier)
        )
    }
}

@Composable
private fun ShowroomPrompt(onUpload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
        Text("Upload a portrait", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Text(
            "AI reads your skin tone and suggests shades. Nothing is applied until you tap one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        Button(onClick = onUpload) { Text("CHOOSE A PHOTO", style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun CenteredProgress(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun ShowroomError(message: UploadErrorMessage, onRetry: () -> Unit) {
    val text = when (message) {
        UploadErrorMessage.NO_FACE_DETECTED -> "We couldn't find a face in this photo. Try a clearer, front-facing photo with good lighting."
        UploadErrorMessage.MULTIPLE_FACES_DETECTED -> "This photo has more than one face. Showroom works best with a single face."
        UploadErrorMessage.IMAGE_LOAD_FAILED -> "We couldn't open that image. Please try a different photo."
        UploadErrorMessage.LANDMARK_DETECTION_FAILED -> "Something went wrong while analyzing this photo. Please try again."
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onRetry) { Text("TRY AGAIN", style = MaterialTheme.typography.labelMedium) }
    }
}
