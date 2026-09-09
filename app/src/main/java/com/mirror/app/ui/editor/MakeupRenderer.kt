package com.mirror.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import com.mirror.app.domain.model.FaceLandmarkResult
import kotlin.math.roundToInt

/** A single resolved makeup layer ready to paint: an actual color plus how strongly to apply it. */
data class MakeupLayer(val color: Color, val intensity: Float)

// Standard MediaPipe Face Mesh (468-point) index sequences for the regions we paint.
// FACEMESH_FACE_OVAL and FACEMESH_LIPS (outer boundary), in walking order around each loop.
private val FACE_OVAL_INDICES = intArrayOf(
    10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377,
    152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
)
private val LIP_OUTER_INDICES = intArrayOf(
    61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291, 375, 321, 405, 314, 17, 84, 181, 91, 146
)
private const val LEFT_CHEEK_INDEX = 50
private const val RIGHT_CHEEK_INDEX = 280

/**
 * Draws [photo] scaled to cover [modifier]'s bounds (matching ContentScale.Crop), then paints
 * the selected makeup layers directly on top *in the same draw pass* using BlendMode.Multiply -
 * this has to happen in one Canvas call rather than as separate stacked composables, since
 * Compose blend modes only mix with content drawn earlier in the same layer, not with sibling
 * composables underneath.
 */
@Composable
fun FaceCanvas(
    photo: Bitmap,
    landmarks: FaceLandmarkResult,
    foundation: MakeupLayer?,
    cheek: MakeupLayer?,
    lips: MakeupLayer?,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(photo) { photo.asImageBitmap() }

    Canvas(modifier = modifier) {
        val imgW = imageBitmap.width.toFloat()
        val imgH = imageBitmap.height.toFloat()
        val coverScale = maxOf(size.width / imgW, size.height / imgH)
        val drawW = imgW * coverScale
        val drawH = imgH * coverScale
        val offsetX = (size.width - drawW) / 2f
        val offsetY = (size.height - drawH) / 2f

        drawImage(
            image = imageBitmap,
            dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
            dstSize = androidx.compose.ui.unit.IntSize(drawW.roundToInt(), drawH.roundToInt())
        )

        fun toCanvasOffset(normX: Float, normY: Float) =
            Offset(offsetX + normX * drawW, offsetY + normY * drawH)

        fun closedPath(indices: IntArray): Path {
            val path = Path()
            indices.forEachIndexed { i, idx ->
                val p = landmarks.points.getOrNull(idx) ?: return@forEachIndexed
                val o = toCanvasOffset(p.x, p.y)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            path.close()
            return path
        }

        // Order matters: foundation across the whole face first, then blush, then lips last
        // so lip color always reads clearly on top rather than being muted by the base tint.
        foundation?.let { layer ->
            drawPath(
                path = closedPath(FACE_OVAL_INDICES),
                color = layer.color.copy(alpha = (layer.intensity * 0.35f).coerceIn(0f, 1f)),
                blendMode = BlendMode.Multiply
            )
        }
        cheek?.let { layer ->
            val radius = minOf(drawW, drawH) * 0.11f
            for (idx in intArrayOf(LEFT_CHEEK_INDEX, RIGHT_CHEEK_INDEX)) {
                val p = landmarks.points.getOrNull(idx) ?: continue
                drawCircle(
                    color = layer.color.copy(alpha = (layer.intensity * 0.45f).coerceIn(0f, 1f)),
                    radius = radius,
                    center = toCanvasOffset(p.x, p.y),
                    blendMode = BlendMode.Multiply
                )
            }
        }
        lips?.let { layer ->
            drawPath(
                path = closedPath(LIP_OUTER_INDICES),
                color = layer.color.copy(alpha = layer.intensity.coerceIn(0f, 1f)),
                blendMode = BlendMode.Multiply
            )
        }
    }
}
