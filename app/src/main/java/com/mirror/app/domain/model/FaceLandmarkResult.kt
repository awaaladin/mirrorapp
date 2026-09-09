package com.mirror.app.domain.model

/**
 * Output of on-device face landmark detection for a single static photo.
 *
 * [points] holds all 468 MediaPipe Face Mesh landmarks in *normalized* image space
 * (x and y in [0, 1], relative to the analyzed bitmap's width/height respectively).
 * Normalized coordinates are kept - rather than pixel coordinates - so the same result
 * can be reused to render onto the original bitmap or a scaled-down preview without
 * recomputing detection.
 */
data class FaceLandmarkResult(
    val points: List<NormalizedLandmark>,
    val imageWidth: Int,
    val imageHeight: Int
)

data class NormalizedLandmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f
)
