package com.mirror.app.ui.beforeafter

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mirror.app.data.network.NetworkConfig
import com.mirror.app.domain.model.ProPlan
import com.mirror.app.ui.editor.EditorCategory
import com.mirror.app.ui.editor.cropToFace
import com.mirror.app.ui.editor.renderMakeupBitmap
import com.mirror.app.ui.editor.resolveMakeupLayer
import com.mirror.app.ui.share.shareLook
import com.mirror.app.ui.upload.UploadUiState
import com.mirror.app.ui.upload.UploadViewModel
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

/**
 * Interactive before/after wipe comparator. "Before" is the actual detected-face crop with no
 * makeup; "after" is that same crop rendered through [renderMakeupBitmap] with whatever the
 * user picked in the editor - the same shared-selection state EditorScreen reads, via
 * [UploadViewModel], so this is always a true diff of the live formulation, not a placeholder.
 */
@Composable
fun BeforeAfterScreen(viewModel: UploadViewModel, proPlan: ProPlan, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val selections by viewModel.selections.collectAsState()
    val shadeCatalog by viewModel.shadeCatalog.collectAsState()
    val lashStyle by viewModel.lashStyle.collectAsState()
    val detected = uiState as? UploadUiState.Detected

    if (detected == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val faceCrop = remember(detected.photo, detected.landmarks) { cropToFace(detected.photo, detected.landmarks) }
    val afterBitmap = remember(faceCrop, selections, shadeCatalog, lashStyle) {
        renderMakeupBitmap(
            photo = faceCrop.bitmap,
            landmarks = faceCrop.landmarks,
            foundation = resolveMakeupLayer(EditorCategory.FOUNDATION, selections, shadeCatalog),
            cheek = resolveMakeupLayer(EditorCategory.CHEEK, selections, shadeCatalog),
            lips = resolveMakeupLayer(EditorCategory.LIPS, selections, shadeCatalog),
            eyeshadow = resolveMakeupLayer(EditorCategory.EYES, selections, shadeCatalog),
            eyeliner = resolveMakeupLayer(EditorCategory.EYELINER, selections, shadeCatalog),
            eyeColor = resolveMakeupLayer(EditorCategory.EYE_COLOR, selections, shadeCatalog),
            lashStyle = lashStyle,
            brow = resolveMakeupLayer(EditorCategory.BROWS, selections, shadeCatalog),
            gele = resolveMakeupLayer(EditorCategory.GELE, selections, shadeCatalog)
        )
    }

    var wipeFraction by remember { mutableFloatStateOf(0.5f) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.inverseSurface)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onBack)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                Text("BACK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f), modifier = Modifier.padding(start = 6.dp))
            }
            Text("WIPE COMPARISON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.inverseOnSurface)
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 5f)
                        .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                wipeFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            }
                        }
                ) {
                    val fullWidth = maxWidth
                    Image(
                        bitmap = afterBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(fullWidth * wipeFraction)
                            .clipToBounds()
                    ) {
                        Image(
                            bitmap = faceCrop.bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.width(fullWidth).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .align(Alignment.CenterStart)
                            .padding(start = fullWidth * wipeFraction)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                    Text(
                        "BEFORE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f), MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        "AFTER",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f), MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            item {
                Text(
                    "Drag across the photo to compare your actual formulation against the original portrait.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Button(
                    onClick = { scope.launch { shareLook(context, afterBitmap, withWatermark = !proPlan.removesWatermark) } },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("SHARE LOOK", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
                }
            }

            if (!proPlan.removesWatermark) {
                item {
                    Text(
                        "Shared looks carry a small mirror watermark.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(NetworkConfig.PRICING_URL)))
                    }) {
                        Text("REMOVE IT FOREVER · ONE-TIME ₦7,500", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
