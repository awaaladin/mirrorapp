package com.mirror.app.ui.editor

import android.graphics.Bitmap
import com.mirror.app.domain.model.FaceLandmarkResult
import com.mirror.app.domain.model.NormalizedLandmark

/** A photo cropped tightly to the detected face, plus landmarks remapped into that crop's
 *  own coordinate space so overlays drawn against [landmarks] line up with [bitmap] pixel-for-pixel. */
data class FaceCrop(
    val bitmap: Bitmap,
    val landmarks: FaceLandmarkResult
)

/**
 * Crops [source] to the bounding box of [landmarks] (with padding around it so the crop
 * isn't a tight skin-only rectangle) so the editor shows "just the person's face" rather than
 * the full original photo with its background. Landmark coordinates are re-normalized against
 * the new cropped dimensions so makeup overlays keep aligning correctly after the crop.
 */
fun cropToFace(source: Bitmap, landmarks: FaceLandmarkResult, paddingFraction: Float = 0.45f): FaceCrop {
    val points = landmarks.points
    if (points.isEmpty()) return FaceCrop(source, landmarks)

    var minX = 1f; var maxX = 0f; var minY = 1f; var maxY = 0f
    for (p in points) {
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
    }

    val faceWidth = (maxX - minX).coerceAtLeast(0.01f)
    val faceHeight = (maxY - minY).coerceAtLeast(0.01f)
    val padX = faceWidth * paddingFraction
    // Extra headroom above (hairline/gele) and below (chin/neck) reads more natural than a
    // symmetric pad, since the mesh itself only covers eyebrows-to-chin.
    val padTop = faceHeight * (paddingFraction + 0.35f)
    val padBottom = faceHeight * paddingFraction

    val cropMinX = (minX - padX).coerceIn(0f, 1f)
    val cropMaxX = (maxX + padX).coerceIn(0f, 1f)
    val cropMinY = (minY - padTop).coerceIn(0f, 1f)
    val cropMaxY = (maxY + padBottom).coerceIn(0f, 1f)

    val left = (cropMinX * source.width).toInt().coerceIn(0, source.width - 1)
    val top = (cropMinY * source.height).toInt().coerceIn(0, source.height - 1)
    val right = (cropMaxX * source.width).toInt().coerceIn(left + 1, source.width)
    val bottom = (cropMaxY * source.height).toInt().coerceIn(top + 1, source.height)
    val cropWidth = right - left
    val cropHeight = bottom - top

    val cropped = Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
    val remapped = points.map { p ->
        val pxX = p.x * source.width
        val pxY = p.y * source.height
        NormalizedLandmark(
            x = ((pxX - left) / cropWidth).coerceIn(0f, 1f),
            y = ((pxY - top) / cropHeight).coerceIn(0f, 1f),
            z = p.z
        )
    }
    return FaceCrop(cropped, FaceLandmarkResult(remapped, cropWidth, cropHeight))
}
