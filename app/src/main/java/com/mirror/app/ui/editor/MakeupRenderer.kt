package com.mirror.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mirror.app.domain.model.FaceLandmarkResult
import com.mirror.app.domain.model.LashStyle
import com.mirror.app.domain.model.NormalizedLandmark
import com.mirror.app.domain.model.ShadeFinish
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.roundToInt

/** A single resolved makeup layer ready to paint: an actual color plus how strongly to apply it.
 *  [finish] (matte/satin/glossy/shimmer, straight from the selected shade's real data) currently
 *  only changes lip rendering - a glossy/shimmer pick adds a reflective highlight the way a real
 *  gloss catches light, instead of every finish looking like flat matte color. */
data class MakeupLayer(val color: Color, val intensity: Float, val finish: ShadeFinish = ShadeFinish.MATTE)

// Standard MediaPipe Face Mesh (468-point) index sequences for the regions we paint.
private val FACE_OVAL_INDICES = intArrayOf(
    10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377,
    152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
)

// Cheek "apple" point plus the temple/zygomatic-edge point on the same side (both already
// verified against FACE_OVAL_INDICES above) - the vector between them gives the natural
// cheekbone angle so blush reads as an angled sweep toward the ear rather than a flat circle.
private const val RIGHT_CHEEK_APPLE = 50
private const val RIGHT_TEMPLE = 234
private const val LEFT_CHEEK_APPLE = 280
private const val LEFT_TEMPLE = 454

// Full eye contour (outer corner -> upper lid arc -> inner corner), used as the shared base
// line for eyeshadow, eyeliner and lashes so all three hug the actual tracked eye shape
// instead of an approximated oval - this is what fixes them reading as "off" from a real photo.
private val RIGHT_EYE_UPPER_CONTOUR = intArrayOf(33, 246, 161, 160, 159, 158, 157, 173, 133)
private val LEFT_EYE_UPPER_CONTOUR = intArrayOf(263, 466, 388, 387, 386, 385, 384, 398, 362)

// Iris centers - only present when the face landmarker's iris refinement is enabled (478
// points instead of 468, confirmed by the on-screen "478 FACE LANDMARK POINTS TRACKED" badge).
private const val RIGHT_IRIS_CENTER = 468
private const val LEFT_IRIS_CENTER = 473

// Each eyebrow tracked as two rows (upper edge + lower edge) rather than one center-line, so a
// filled polygon between them reproduces the brow's actual tapered silhouette instead of a
// uniform-width stroke that used to visibly float above/below the real eyebrow.
private val RIGHT_BROW_UPPER = intArrayOf(46, 53, 52, 65, 55)
private val RIGHT_BROW_LOWER = intArrayOf(70, 63, 105, 66, 107)
private val LEFT_BROW_UPPER = intArrayOf(276, 283, 282, 295, 285)
private val LEFT_BROW_LOWER = intArrayOf(300, 293, 334, 296, 336)

// Forehead/hairline boundary - the base the gele wrap sits on before extrapolating up over the
// crown (MediaPipe has no landmarks above the hairline, since it only tracks the face).
private val FOREHEAD_TOP_INDICES = intArrayOf(54, 103, 67, 109, 10, 338, 297, 332, 284)
private val FOREHEAD_TOP_INDEX_SET = FOREHEAD_TOP_INDICES.toSet()
private const val CHIN_INDEX = 152
private const val FOREHEAD_CENTER_INDEX = 10

// Widely-documented "tip of the nose" landmark in the 468-point face mesh, used only for the
// yaw estimate below (not for rendering).
private const val NOSE_TIP_INDEX = 1

// Google's canonical face model triangulation (mediapipe/modules/face_geometry/data/
// canonical_face_model.obj), the same triangle list MediaPipe's own face-effect samples use.
// Each triple is one triangle's three landmark indices. Triangles are always simple/convex on
// their own, unlike one big polygon traced around the face-oval boundary - which is what let
// the previous single-Path foundation fill self-intersect and stop rendering partway across a
// turned face (the 2D projection of the 3D mesh folds over itself near the far cheek/jaw at an
// angle). SKIN_TRIANGLE_INDICES excludes only triangles fully *inside* the eyes/eyebrows/lips
// (their own separate layers already draw on top), not merely touching them, so foundation still
// reaches right up to the eyelid/lip edge with no gap. Nostrils aren't excluded: the canonical
// mesh models the nose as continuous skin, not a hole, so there's no distinct "nostril interior"
// triangle subset to exclude in the first place.
private val SKIN_TRIANGLE_INDICES = intArrayOf(
    356, 389, 264, 127, 34, 162, 368, 264, 389, 139, 162, 34, 349, 451, 350, 120, 121, 231, 452, 350, 451, 232, 231, 121,
    357, 343, 350, 128, 121, 114, 277, 350, 343, 47, 114, 121, 350, 452, 357, 121, 128, 232, 453, 357, 452, 233, 232, 128,
    299, 333, 297, 69, 67, 104, 332, 297, 333, 103, 104, 67, 175, 152, 396, 175, 171, 152, 377, 396, 152, 148, 152, 171,
    280, 347, 330, 50, 101, 118, 348, 330, 347, 119, 118, 101, 9, 336, 151, 9, 151, 107, 337, 151, 336, 108, 107, 151,
    344, 278, 360, 115, 131, 48, 279, 360, 278, 49, 48, 131, 262, 431, 418, 32, 194, 211, 424, 418, 431, 204, 211, 194,
    322, 270, 410, 92, 186, 40, 409, 410, 270, 185, 40, 186, 347, 449, 348, 118, 119, 229, 450, 348, 449, 230, 229, 119,
    434, 432, 430, 214, 210, 212, 422, 430, 432, 202, 212, 210, 313, 314, 18, 83, 18, 84, 17, 18, 314, 17, 84, 18,
    259, 387, 260, 29, 30, 160, 388, 260, 387, 161, 160, 30, 286, 414, 384, 56, 157, 190, 398, 384, 414, 173, 190, 157,
    418, 424, 406, 194, 182, 204, 335, 406, 424, 106, 204, 182, 367, 416, 364, 138, 135, 192, 434, 364, 416, 214, 192, 135,
    391, 423, 327, 165, 98, 203, 358, 327, 423, 129, 203, 98, 298, 301, 284, 68, 54, 71, 251, 284, 301, 21, 71, 54,
    4, 275, 5, 4, 5, 45, 281, 5, 275, 51, 45, 5, 254, 373, 253, 24, 23, 144, 374, 253, 373, 145, 144, 23,
    280, 425, 411, 50, 187, 205, 427, 411, 425, 207, 205, 187, 421, 313, 200, 201, 200, 83, 18, 200, 313, 18, 83, 200,
    335, 321, 406, 106, 182, 91, 405, 406, 321, 181, 91, 182, 425, 266, 426, 205, 206, 36, 423, 426, 266, 203, 36, 206,
    369, 396, 400, 140, 176, 171, 377, 400, 396, 148, 171, 176, 391, 269, 322, 165, 92, 39, 270, 322, 269, 40, 39, 92,
    417, 465, 413, 193, 189, 245, 464, 413, 465, 244, 245, 189, 257, 258, 386, 27, 159, 28, 385, 386, 258, 158, 28, 159,
    260, 388, 467, 30, 247, 161, 466, 467, 388, 246, 161, 247, 248, 456, 419, 3, 196, 236, 399, 419, 456, 174, 236, 196,
    333, 298, 332, 104, 103, 68, 284, 332, 298, 54, 68, 103, 285, 8, 417, 55, 193, 8, 168, 417, 8, 168, 8, 193,
    340, 261, 346, 111, 117, 31, 448, 346, 261, 228, 31, 117, 285, 417, 441, 55, 221, 193, 413, 441, 417, 189, 193, 221,
    327, 460, 326, 98, 97, 240, 328, 326, 460, 99, 240, 97, 277, 355, 329, 47, 100, 126, 371, 329, 355, 142, 126, 100,
    309, 392, 438, 79, 218, 166, 439, 438, 392, 219, 166, 218, 381, 382, 256, 154, 26, 155, 341, 256, 382, 112, 155, 26,
    360, 279, 420, 131, 198, 49, 429, 420, 279, 209, 49, 198, 365, 364, 379, 136, 150, 135, 394, 379, 364, 169, 135, 150,
    355, 277, 437, 126, 217, 47, 343, 437, 277, 114, 47, 217, 443, 444, 282, 223, 52, 224, 283, 282, 444, 53, 224, 52,
    281, 275, 363, 51, 134, 45, 440, 363, 275, 220, 45, 134, 431, 262, 395, 211, 170, 32, 369, 395, 262, 140, 32, 170,
    337, 299, 338, 108, 109, 69, 297, 338, 299, 67, 69, 109, 335, 273, 321, 106, 91, 43, 375, 321, 273, 146, 43, 91,
    348, 450, 349, 119, 120, 230, 451, 349, 450, 231, 230, 120, 467, 359, 342, 247, 113, 130, 446, 342, 359, 226, 130, 113,
    250, 458, 462, 20, 242, 238, 461, 462, 458, 241, 238, 242, 276, 353, 300, 46, 70, 124, 383, 300, 353, 156, 124, 70,
    447, 264, 345, 227, 116, 34, 372, 345, 264, 143, 34, 116, 352, 345, 346, 123, 117, 116, 340, 346, 345, 111, 116, 117,
    1, 19, 274, 1, 44, 19, 354, 274, 19, 125, 19, 44, 248, 281, 456, 3, 236, 51, 363, 456, 281, 134, 51, 236,
    425, 426, 427, 205, 207, 206, 436, 427, 426, 216, 206, 207, 380, 381, 252, 153, 22, 154, 256, 252, 381, 26, 154, 22,
    391, 393, 269, 165, 39, 167, 267, 269, 393, 37, 167, 39, 199, 428, 200, 199, 200, 208, 421, 200, 428, 201, 208, 200,
    330, 329, 266, 101, 36, 100, 371, 266, 329, 142, 100, 36, 422, 432, 273, 202, 43, 212, 287, 273, 432, 57, 212, 43,
    290, 250, 328, 60, 99, 20, 462, 328, 250, 242, 20, 99, 258, 286, 385, 28, 158, 56, 384, 385, 286, 157, 56, 158,
    342, 446, 353, 113, 124, 226, 265, 353, 446, 35, 226, 124, 257, 386, 259, 27, 29, 159, 387, 259, 386, 160, 159, 29,
    430, 422, 431, 210, 211, 202, 424, 431, 422, 204, 202, 211, 445, 342, 276, 225, 46, 113, 353, 276, 342, 124, 113, 46,
    424, 422, 335, 204, 106, 202, 273, 335, 422, 43, 202, 106, 366, 447, 352, 137, 123, 227, 345, 352, 447, 116, 227, 123,
    371, 358, 266, 142, 36, 129, 423, 266, 358, 203, 129, 36, 327, 294, 460, 98, 240, 64, 455, 460, 294, 235, 64, 240,
    294, 331, 278, 64, 48, 102, 279, 278, 331, 49, 102, 48, 427, 436, 434, 207, 214, 216, 432, 434, 436, 212, 216, 214,
    394, 430, 395, 169, 170, 210, 431, 395, 430, 211, 210, 170, 395, 369, 378, 170, 149, 140, 400, 378, 369, 176, 140, 149,
    296, 334, 299, 66, 69, 105, 333, 299, 334, 104, 105, 69, 417, 168, 351, 193, 122, 168, 6, 351, 168, 6, 168, 122,
    280, 411, 352, 50, 123, 187, 376, 352, 411, 147, 187, 123, 330, 348, 329, 101, 100, 119, 349, 329, 348, 120, 119, 100,
    334, 293, 333, 105, 104, 63, 298, 333, 293, 68, 63, 104, 323, 454, 366, 93, 137, 234, 447, 366, 454, 227, 234, 137,
    429, 279, 358, 209, 129, 49, 331, 358, 279, 102, 49, 129, 8, 285, 9, 8, 9, 55, 336, 9, 285, 107, 55, 9,
    329, 349, 277, 100, 47, 120, 350, 277, 349, 121, 120, 47, 252, 253, 380, 22, 153, 23, 374, 380, 253, 145, 23, 153,
    351, 6, 419, 122, 196, 6, 197, 419, 6, 197, 6, 196, 397, 367, 365, 172, 136, 138, 364, 365, 367, 135, 138, 136,
    288, 435, 397, 58, 172, 215, 367, 397, 435, 138, 215, 172, 438, 439, 344, 218, 115, 219, 278, 344, 439, 48, 219, 115,
    5, 281, 195, 5, 195, 51, 248, 195, 281, 3, 51, 195, 273, 287, 375, 43, 146, 57, 291, 375, 287, 61, 57, 146,
    396, 428, 175, 171, 175, 208, 199, 175, 428, 199, 208, 175, 444, 445, 283, 224, 53, 225, 276, 283, 445, 46, 225, 53,
    254, 339, 373, 24, 144, 110, 390, 373, 339, 163, 110, 144, 346, 448, 347, 117, 118, 228, 449, 347, 448, 229, 228, 118,
    454, 356, 447, 234, 227, 127, 264, 447, 356, 34, 127, 227, 336, 296, 337, 107, 108, 66, 299, 337, 296, 69, 66, 108,
    151, 337, 10, 151, 10, 108, 338, 10, 337, 109, 108, 10, 278, 439, 294, 48, 64, 219, 455, 294, 439, 235, 219, 64,
    358, 371, 429, 129, 209, 142, 355, 429, 371, 126, 142, 209, 345, 372, 340, 116, 111, 143, 265, 340, 372, 35, 143, 111,
    352, 346, 280, 123, 50, 117, 347, 280, 346, 118, 117, 50, 295, 442, 282, 65, 52, 222, 443, 282, 442, 223, 222, 52,
    19, 94, 354, 19, 125, 94, 370, 354, 94, 141, 94, 125, 295, 285, 442, 65, 222, 55, 441, 442, 285, 221, 55, 222,
    419, 197, 248, 196, 3, 197, 195, 248, 197, 195, 197, 3, 359, 263, 255, 130, 25, 33, 249, 255, 263, 7, 33, 25,
    275, 274, 440, 45, 220, 44, 457, 440, 274, 237, 44, 220, 300, 383, 301, 70, 71, 156, 368, 301, 383, 139, 156, 71,
    417, 351, 465, 193, 245, 122, 412, 465, 351, 188, 122, 245, 466, 263, 467, 246, 247, 33, 359, 467, 263, 130, 33, 247,
    389, 251, 368, 162, 139, 21, 301, 368, 251, 71, 21, 139, 379, 394, 378, 150, 149, 169, 395, 378, 394, 170, 169, 149,
    351, 419, 412, 122, 188, 196, 399, 412, 419, 174, 196, 188, 426, 322, 436, 206, 216, 92, 410, 436, 322, 186, 92, 216,
    393, 326, 164, 167, 164, 97, 2, 164, 326, 2, 97, 164, 354, 370, 461, 125, 241, 141, 462, 461, 370, 242, 141, 241,
    0, 267, 164, 0, 164, 37, 393, 164, 267, 167, 37, 164, 293, 300, 298, 63, 68, 70, 301, 298, 300, 71, 70, 68,
    340, 265, 261, 111, 31, 35, 446, 261, 265, 226, 35, 31, 280, 330, 425, 50, 205, 101, 266, 425, 330, 36, 101, 205,
    423, 391, 426, 203, 206, 165, 322, 426, 391, 92, 165, 206, 429, 355, 420, 209, 198, 126, 437, 420, 355, 217, 126, 198,
    391, 327, 393, 165, 167, 98, 326, 393, 327, 97, 98, 167, 457, 438, 440, 237, 220, 218, 344, 440, 438, 115, 218, 220,
    382, 362, 341, 155, 112, 133, 463, 341, 362, 243, 133, 112, 457, 461, 459, 237, 239, 241, 458, 459, 461, 238, 241, 239,
    434, 430, 364, 214, 135, 210, 394, 364, 430, 169, 210, 135, 414, 463, 398, 190, 173, 243, 362, 398, 463, 133, 243, 173,
    262, 428, 369, 32, 140, 208, 396, 369, 428, 171, 208, 140, 457, 274, 461, 237, 241, 44, 354, 461, 274, 125, 44, 241,
    313, 406, 314, 83, 84, 182, 405, 314, 406, 181, 182, 84, 418, 406, 421, 194, 201, 182, 313, 421, 406, 83, 182, 201,
    366, 401, 323, 137, 93, 177, 361, 323, 401, 132, 177, 93, 410, 409, 287, 186, 57, 185, 291, 287, 409, 61, 185, 57,
    436, 410, 432, 216, 212, 186, 287, 432, 410, 57, 186, 212, 434, 416, 427, 214, 207, 192, 411, 427, 416, 187, 192, 207,
    264, 368, 372, 34, 143, 139, 383, 372, 368, 156, 139, 143, 457, 459, 438, 237, 218, 239, 309, 438, 459, 79, 239, 218,
    352, 376, 366, 123, 137, 147, 401, 366, 376, 177, 147, 137, 4, 1, 275, 4, 45, 1, 274, 275, 1, 44, 1, 45,
    428, 262, 421, 208, 201, 32, 418, 421, 262, 194, 32, 201, 327, 358, 294, 98, 64, 129, 331, 294, 358, 102, 129, 64,
    367, 435, 416, 138, 192, 215, 433, 416, 435, 213, 215, 192, 455, 439, 289, 235, 59, 219, 392, 289, 439, 166, 219, 59,
    328, 462, 326, 99, 97, 242, 370, 326, 462, 141, 242, 97, 326, 370, 2, 97, 2, 141, 94, 2, 370, 94, 141, 2,
    460, 455, 305, 240, 75, 235, 289, 305, 455, 59, 235, 75, 448, 339, 449, 228, 229, 110, 254, 449, 339, 24, 110, 229,
    261, 446, 255, 31, 25, 226, 359, 255, 446, 130, 226, 25, 449, 254, 450, 229, 230, 24, 253, 450, 254, 23, 24, 230,
    450, 253, 451, 230, 231, 23, 252, 451, 253, 22, 23, 231, 451, 252, 452, 231, 232, 22, 256, 452, 252, 26, 22, 232,
    256, 341, 452, 26, 232, 112, 453, 452, 341, 233, 112, 232, 413, 464, 414, 189, 190, 244, 463, 414, 464, 243, 244, 190,
    441, 413, 286, 221, 56, 189, 414, 286, 413, 190, 189, 56, 441, 286, 442, 221, 222, 56, 258, 442, 286, 28, 56, 222,
    442, 258, 443, 222, 223, 28, 257, 443, 258, 27, 28, 223, 444, 443, 259, 224, 29, 223, 257, 259, 443, 27, 223, 29,
    259, 260, 444, 29, 224, 30, 445, 444, 260, 225, 30, 224, 260, 467, 445, 30, 225, 247, 342, 445, 467, 113, 247, 225,
    250, 309, 458, 20, 238, 79, 459, 458, 309, 239, 79, 238, 290, 305, 392, 60, 166, 75, 289, 392, 305, 59, 75, 166,
    460, 305, 328, 240, 99, 75, 290, 328, 305, 60, 75, 99, 376, 433, 401, 147, 177, 213, 435, 401, 433, 215, 213, 177,
    250, 290, 309, 20, 79, 60, 392, 309, 290, 166, 60, 79, 411, 416, 376, 187, 147, 192, 433, 376, 416, 213, 192, 147,
    341, 463, 453, 112, 233, 243, 464, 453, 463, 244, 243, 233, 453, 464, 357, 233, 128, 244, 465, 357, 464, 245, 244, 128,
    412, 343, 465, 188, 245, 114, 357, 465, 343, 128, 114, 245, 437, 343, 399, 217, 174, 114, 412, 399, 343, 188, 114, 174,
    363, 440, 360, 134, 131, 220, 344, 360, 440, 115, 220, 131, 456, 420, 399, 236, 174, 198, 437, 399, 420, 217, 198, 174,
    456, 363, 420, 236, 198, 134, 360, 420, 363, 131, 134, 198, 361, 401, 288, 132, 58, 177, 435, 288, 401, 215, 177, 58,
    353, 265, 383, 124, 156, 35, 372, 383, 265, 143, 35, 156, 255, 249, 339, 25, 110, 7, 390, 339, 249, 163, 7, 110,
    261, 255, 448, 31, 228, 25, 339, 448, 255, 110, 25, 228
)

// 138 canonical triangles inside the exact outer-lip boundary that was already confirmed working
// before this fix - the closed-loop lip fill has the same self-intersection risk at a turn angle
// as the face oval did, so this exists as a triangle mesh for the same reason SKIN_TRIANGLE_INDICES
// does. (An earlier version of this list only required all 3 vertices to already be members of a
// 40-point lip-contour set, which badly under-covered the mouth - down to 18 triangles instead of
// this. Selecting by triangle-centroid-inside-the-verified-boundary instead fixed that.)
private val LIP_TRIANGLE_INDICES = intArrayOf(
    308, 415, 324, 78, 95, 191, 267, 0, 302, 37, 72, 0, 11, 302, 0, 11, 0, 72, 267, 302, 269, 37, 39, 72,
    303, 269, 302, 73, 72, 39, 269, 303, 270, 39, 40, 73, 304, 270, 303, 74, 73, 40, 304, 408, 270, 74, 40, 184,
    409, 270, 408, 185, 184, 40, 272, 310, 407, 42, 183, 80, 415, 407, 310, 191, 80, 183, 307, 375, 306, 77, 76, 146,
    291, 306, 375, 61, 146, 76, 320, 321, 307, 90, 77, 91, 375, 307, 321, 146, 91, 77, 405, 321, 404, 181, 180, 91,
    320, 404, 321, 90, 91, 180, 17, 314, 16, 17, 16, 84, 315, 16, 314, 85, 84, 16, 325, 292, 324, 96, 95, 62,
    308, 324, 292, 78, 62, 95, 306, 292, 307, 76, 77, 62, 325, 307, 292, 96, 62, 77, 302, 268, 303, 72, 73, 38,
    271, 303, 268, 41, 38, 73, 303, 271, 304, 73, 74, 41, 272, 304, 271, 42, 41, 74, 304, 272, 408, 74, 184, 42,
    407, 408, 272, 183, 42, 184, 319, 320, 325, 89, 96, 90, 307, 325, 320, 77, 90, 96, 404, 320, 403, 180, 179, 90,
    319, 403, 320, 89, 90, 179, 16, 315, 15, 16, 15, 85, 316, 15, 315, 86, 85, 15, 15, 316, 14, 15, 14, 86,
    317, 14, 316, 87, 86, 14, 402, 403, 318, 178, 88, 179, 319, 318, 403, 89, 179, 88, 324, 318, 325, 95, 96, 88,
    319, 325, 318, 89, 88, 96, 271, 311, 272, 41, 42, 81, 310, 272, 311, 80, 81, 42, 268, 312, 271, 38, 41, 82,
    311, 271, 312, 81, 82, 41, 407, 415, 292, 183, 62, 191, 308, 292, 415, 78, 191, 62, 11, 12, 302, 11, 72, 12,
    268, 302, 12, 38, 12, 72, 12, 13, 268, 12, 38, 13, 312, 268, 13, 82, 13, 38, 316, 403, 317, 86, 87, 179,
    402, 317, 403, 178, 179, 87, 315, 404, 316, 85, 86, 180, 403, 316, 404, 179, 180, 86, 314, 405, 315, 84, 85, 181,
    404, 315, 405, 180, 181, 85, 408, 407, 306, 184, 76, 183, 292, 306, 407, 62, 183, 76, 408, 306, 409, 184, 185, 76,
    291, 409, 306, 61, 76, 185, 14, 317, 13, 14, 13, 87, 312, 13, 317, 82, 87, 13, 317, 402, 312, 87, 82, 178,
    311, 312, 402, 81, 178, 82, 402, 318, 311, 178, 81, 88, 310, 311, 318, 80, 88, 81, 318, 324, 310, 88, 80, 95,
    415, 310, 324, 191, 95, 80
)

/**
 * A rough proxy for how far the head is turned away from facing the camera: how far off-center
 * the nose tip sits relative to the two temple points, as a fraction of half the face width. Not
 * a calibrated angle (a true yaw needs real 3D pose estimation from a transformation matrix,
 * which requires a face-geometry-capable model bundle this app doesn't currently load) - just
 * monotonic enough to flag "this is basically a profile shot". 0 is dead-on frontal; magnitude
 * approaches 1 as the nose tip approaches one temple (a true profile).
 */
fun estimateYawProxy(landmarks: FaceLandmarkResult): Float {
    fun point(idx: Int) = landmarks.points.getOrNull(idx)
    val noseTip = point(NOSE_TIP_INDEX) ?: return 0f
    val rightTemple = point(RIGHT_TEMPLE) ?: return 0f
    val leftTemple = point(LEFT_TEMPLE) ?: return 0f
    val faceMidX = (leftTemple.x + rightTemple.x) / 2f
    val halfFaceWidth = kotlin.math.abs(leftTemple.x - rightTemple.x) / 2f
    if (halfFaceWidth < 1e-4f) return 0f
    return (noseTip.x - faceMidX) / halfFaceWidth
}

// Tuned so a frontal photo stays well under this and a near-profile (~80-90deg) photo clearly
// exceeds it; a moderate ~30-45deg turn should still land below it, since that's the case the
// triangle-mesh rendering fix (not this warning) is meant to handle cleanly. Re-tune against real
// device photos at those three angles if the warning fires too eagerly or not eagerly enough.
const val STEEP_ANGLE_YAW_THRESHOLD = 0.6f

/** True once a photo is close enough to profile that most of one side of the face is
 *  foreshortened/invisible - triangle-mesh rendering can't invent skin that isn't there, so this
 *  is a UX safety net (prompt the user toward a better photo), not a replacement for that fix. */
fun isSteepAngle(landmarks: FaceLandmarkResult): Boolean =
    kotlin.math.abs(estimateYawProxy(landmarks)) > STEEP_ANGLE_YAW_THRESHOLD

@Composable
fun FaceCanvas(
    photo: Bitmap,
    landmarks: FaceLandmarkResult,
    foundation: MakeupLayer?,
    cheek: MakeupLayer?,
    lips: MakeupLayer?,
    eyeshadow: MakeupLayer? = null,
    eyeliner: MakeupLayer? = null,
    eyeColor: MakeupLayer? = null,
    lashStyle: LashStyle? = null,
    brow: MakeupLayer? = null,
    gele: MakeupLayer? = null,
    modifier: Modifier = Modifier
) {
    // Foundation/blush/brows/lips come from the feathered compositor (see MakeupCompositor); the
    // composed bitmap is recomputed off the main thread whenever a layer changes, and the
    // un-made-up photo shows meanwhile. Everything else still draws live on top below.
    val composed by produceState(initialValue = photo, photo, landmarks, foundation, cheek, lips, brow) {
        value = withContext(Dispatchers.Default) { composeMakeup(photo, landmarks, foundation, cheek, lips, brow) }
    }
    val imageBitmap = remember(composed) { composed.asImageBitmap() }

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

        drawMakeupLayers(
            landmarks = landmarks,
            offsetX = offsetX,
            offsetY = offsetY,
            drawW = drawW,
            drawH = drawH,
            gele = gele,
            foundation = null,
            brow = null,
            eyeshadow = eyeshadow,
            eyeliner = eyeliner,
            eyeColor = eyeColor,
            lashStyle = lashStyle,
            cheek = null,
            lips = null
        )
    }
}

/**
 * Shared with [renderMakeupBitmap] so the live preview and the exported/compared image are
 * pixel-for-pixel the same makeup, just drawn into two different destinations (a live Canvas
 * here, a plain Bitmap-backed one there).
 */
private fun DrawScope.drawMakeupLayers(
    landmarks: FaceLandmarkResult,
    offsetX: Float,
    offsetY: Float,
    drawW: Float,
    drawH: Float,
    gele: MakeupLayer?,
    foundation: MakeupLayer?,
    brow: MakeupLayer?,
    eyeshadow: MakeupLayer?,
    eyeliner: MakeupLayer?,
    eyeColor: MakeupLayer?,
    lashStyle: LashStyle?,
    cheek: MakeupLayer?,
    lips: MakeupLayer?
) {
    fun point(idx: Int): NormalizedLandmark? = landmarks.points.getOrNull(idx)
    fun toCanvasOffset(normX: Float, normY: Float) =
        Offset(offsetX + normX * drawW, offsetY + normY * drawH)
    // A distinct name (not a second toCanvasOffset overload) - a callable reference to an
    // overloaded local function is ambiguous even when only one overload fits the target type.
    fun pointOffset(p: NormalizedLandmark) = toCanvasOffset(p.x, p.y)

    /** Builds one Path containing every triangle in [triangleIndices] (flattened index triples)
     *  as its own moveTo/lineTo/lineTo/close subpath, via [vertexOffset] for each landmark index.
     *  Each triangle is individually simple/convex, so - unlike one big polygon traced around a
     *  region's boundary - the mesh can't fold over itself and break the fill at a turn angle.
     *  A single Path with many subpaths (rather than one drawPath call per triangle) also means
     *  one fill operation, not hundreds of overlapping semi-transparent ones: no double-blended
     *  seams between adjacent triangles, and far fewer draw calls per frame. Triangles missing a
     *  landmark (out-of-range index) are skipped rather than partially drawn. */
    fun buildTriangleMeshPath(triangleIndices: IntArray, vertexOffset: (Int) -> Offset?): Path {
        val path = Path()
        var i = 0
        while (i < triangleIndices.size) {
            val a = vertexOffset(triangleIndices[i])
            val b = vertexOffset(triangleIndices[i + 1])
            val c = vertexOffset(triangleIndices[i + 2])
            if (a != null && b != null && c != null) {
                path.moveTo(a.x, a.y)
                path.lineTo(b.x, b.y)
                path.lineTo(c.x, c.y)
                path.close()
            }
            i += 3
        }
        return path
    }

    /** A filled band between [upperRow] and [lowerRow] (e.g. a brow's top/bottom edges, or an
     *  eye contour and that same contour lifted toward the brow for eyeshadow) - traces
     *  upperRow forward then lowerRow backward so the polygon doesn't self-intersect. */
    fun bandPath(upperRow: List<Offset>, lowerRow: List<Offset>): Path {
        val path = Path()
        upperRow.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        lowerRow.asReversed().forEach { o -> path.lineTo(o.x, o.y) }
        path.close()
        return path
    }

    // Order matters: gele/foundation across broad regions first, then blush, brows, eye makeup,
    // then lips last so lip color always reads clearly on top.
    gele?.let { layer -> drawGeleWrap(layer, ::point, ::toCanvasOffset) }

    foundation?.let { layer ->
        // MediaPipe's face-oval contour tops out around the lower/mid forehead, well short of
        // the hairline, so filling exactly that outline always looked like it "stopped short" of
        // the forehead. Push just the top-boundary points (shared with FOREHEAD_TOP_INDICES)
        // further up - toward the hairline - while leaving the jaw/cheek/chin points untouched.
        val chin = point(CHIN_INDEX)
        val foreheadCenter = point(FOREHEAD_CENTER_INDEX)
        val faceHeightNorm = if (chin != null && foreheadCenter != null) {
            kotlin.math.abs(chin.y - foreheadCenter.y)
        } else 0f
        fun liftedVertexOffset(idx: Int): Offset? {
            val p = point(idx) ?: return null
            val liftedY = if (idx in FOREHEAD_TOP_INDEX_SET) p.y - faceHeightNorm * 0.18f else p.y
            return toCanvasOffset(p.x, liftedY)
        }
        val skinPath = buildTriangleMeshPath(SKIN_TRIANGLE_INDICES, ::liftedVertexOffset)
        // Two blend modes tried and rejected: Multiply darkens proportionally to what's
        // already underneath, compounding into near-black blotches on any shadow/contour in
        // the photo. Color re-hues while forcing the destination's exact luminance through,
        // which on a brightly-lit photo pushes most of the face toward one flat, washed-out
        // ash/light-orange tone regardless of which shade was picked - discarding the source
        // color's own saturation entirely. Plain alpha blending (no blendMode = SrcOver) just
        // linearly mixes the chosen shade with the real pixel underneath, so the photo's
        // original shading and the shade's own hue both still show through proportionally -
        // closer to a translucent tint than a repaint. Alpha is kept moderate for the same
        // reason: at high opacity a flat color starts looking painted-on regardless of blend mode.
        drawPath(
            path = skinPath,
            color = layer.color.copy(alpha = (0.18f + layer.intensity * 0.32f).coerceIn(0f, 1f))
        )
    }

    cheek?.let { layer ->
        for ((appleIdx, templeIdx) in listOf(RIGHT_CHEEK_APPLE to RIGHT_TEMPLE, LEFT_CHEEK_APPLE to LEFT_TEMPLE)) {
            val apple = point(appleIdx) ?: continue
            val temple = point(templeIdx) ?: continue
            val applePx = pointOffset(apple)
            val templePx = pointOffset(temple)
            val angleDeg = Math.toDegrees(
                atan2((templePx.y - applePx.y).toDouble(), (templePx.x - applePx.x).toDouble())
            ).toFloat()
            val majorAxis = minOf(drawW, drawH) * 0.20f
            val minorAxis = minOf(drawW, drawH) * 0.115f
            rotate(degrees = angleDeg, pivot = applePx) {
                drawOval(
                    color = layer.color.copy(alpha = (layer.intensity * 0.45f).coerceIn(0f, 1f)),
                    topLeft = Offset(applePx.x - majorAxis / 2f, applePx.y - minorAxis / 2f),
                    size = Size(majorAxis, minorAxis),
                    blendMode = BlendMode.Multiply
                )
            }
        }
    }

    brow?.let { layer ->
        // A flat 0.5-1.0 range read as a thick, solid marker-line slash over the real brow
        // (worse under Multiply, which compounds with the brow hair's own dark pixels) rather
        // than a subtle color/fill correction - softened so even full intensity stays a shade
        // change, not a redraw.
        val alpha = (0.3f + layer.intensity * 0.35f).coerceIn(0f, 1f)
        for ((upperRow, lowerRow) in listOf(RIGHT_BROW_UPPER to RIGHT_BROW_LOWER, LEFT_BROW_UPPER to LEFT_BROW_LOWER)) {
            val upperPts = upperRow.asList().mapNotNull { point(it) }.map(::pointOffset)
            val lowerPts = lowerRow.asList().mapNotNull { point(it) }.map(::pointOffset)
            if (upperPts.size < 2 || lowerPts.size < 2) continue
            drawPath(
                path = bandPath(upperPts, lowerPts),
                color = layer.color.copy(alpha = alpha),
                blendMode = BlendMode.Multiply
            )
        }
    }

    eyeshadow?.let { layer ->
        val alpha = (layer.intensity * 0.55f).coerceIn(0f, 1f)
        for (contour in listOf(RIGHT_EYE_UPPER_CONTOUR, LEFT_EYE_UPPER_CONTOUR)) {
            val lidPts = contour.asList().mapNotNull { point(it) }
            if (lidPts.size < 3) continue
            val eyeWidthNorm = kotlin.math.abs(lidPts.last().x - lidPts.first().x)
            val lift = eyeWidthNorm * 0.55f
            val lashLine = lidPts.map(::pointOffset)
            val liftedLine = lidPts.map { toCanvasOffset(it.x, it.y - lift) }
            drawPath(
                path = bandPath(liftedLine, lashLine),
                color = layer.color.copy(alpha = alpha),
                blendMode = BlendMode.Multiply
            )
        }
    }

    eyeliner?.let { layer ->
        // A liner needs to read as a crisp, solid line regardless of what's under it (shadow,
        // existing contouring in the photo) - BlendMode.Multiply on an already-dark lash-line
        // crease barely changes anything, which is why this could look like "nothing happened".
        // Normal (default/SrcOver) alpha blending always shows up as an actual line instead.
        //
        // There used to be an outer-corner "wing" flick here, extrapolated past the last
        // contour point - the rotation math for it was wrong for screen space (y grows
        // downward, not upward, so the intended upward flick rendered as a downward slash
        // instead, looking like a stray arrow rather than eyeliner). Removed rather than
        // patched blind - the traced line alone is the correct, verified-safe behavior.
        val strokeWidth = minOf(drawW, drawH) * 0.013f
        val lineColor = layer.color.copy(alpha = (0.6f + layer.intensity * 0.4f).coerceIn(0f, 1f))
        val strokeStyle = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        for (contour in listOf(RIGHT_EYE_UPPER_CONTOUR, LEFT_EYE_UPPER_CONTOUR)) {
            val lidPts = contour.asList().mapNotNull { point(it) }.map(::pointOffset)
            if (lidPts.size < 2) continue
            val path = Path()
            lidPts.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            drawPath(path = path, color = lineColor, style = strokeStyle)
        }
    }

    eyeColor?.let { layer ->
        // BlendMode.Color keeps the pupil's near-black luminance and the eye's natural
        // catchlight intact, only shifting the hue - so the iris looks recolored rather than
        // painted over with a flat opaque disc.
        val alpha = (0.4f + layer.intensity * 0.4f).coerceIn(0f, 1f)
        for ((irisIdx, outerIdx, innerIdx) in listOf(
            Triple(RIGHT_IRIS_CENTER, RIGHT_EYE_UPPER_CONTOUR[0], RIGHT_EYE_UPPER_CONTOUR[8]),
            Triple(LEFT_IRIS_CENTER, LEFT_EYE_UPPER_CONTOUR[0], LEFT_EYE_UPPER_CONTOUR[8])
        )) {
            val irisCenter = point(irisIdx) ?: continue
            val outer = point(outerIdx) ?: continue
            val inner = point(innerIdx) ?: continue
            val eyeWidthNorm = kotlin.math.abs(outer.x - inner.x)
            // A typical open-eye iris diameter is roughly 40-45% of the visible eye width.
            val radiusPx = eyeWidthNorm * drawW * 0.21f
            drawCircle(
                color = layer.color.copy(alpha = alpha),
                radius = radiusPx,
                center = pointOffset(irisCenter),
                blendMode = BlendMode.Color
            )
        }
    }

    lashStyle?.let { style -> drawLashes(style, ::point, ::toCanvasOffset, drawW, drawH) }

    lips?.let { layer ->
        // Same fold-over risk as the face oval had: LIP_OUTER_INDICES traces a closed loop
        // around a 3D-curved surface, which can self-intersect in 2D at a turn angle. Per-
        // triangle rendering (LIP_TRIANGLE_INDICES) fixes it the same way.
        drawPath(
            path = buildTriangleMeshPath(LIP_TRIANGLE_INDICES) { idx -> point(idx)?.let(::pointOffset) },
            color = layer.color.copy(alpha = layer.intensity.coerceIn(0f, 1f)),
            blendMode = BlendMode.Multiply
        )
        // A flat color fill looks the same whether the shade is matte or glossy, which isn't
        // how any of those actually look - glossy/shimmer finishes catch light. BlendMode.Screen
        // (lightens, the opposite of Multiply) with a soft white oval on the lower lip stands in
        // for that specular highlight; satin gets a much fainter version, matte gets none.
        val highlightAlpha = when (layer.finish) {
            ShadeFinish.GLOSSY -> 0.4f
            ShadeFinish.SHIMMER -> 0.28f
            ShadeFinish.SATIN -> 0.14f
            ShadeFinish.MATTE -> 0f
        }
        if (highlightAlpha > 0f) {
            val lowerLipCenter = point(17)
            if (lowerLipCenter != null) {
                val center = pointOffset(lowerLipCenter)
                val highlightWidth = minOf(drawW, drawH) * 0.05f
                val highlightHeight = highlightWidth * 0.55f
                drawOval(
                    color = Color.White.copy(alpha = highlightAlpha),
                    topLeft = Offset(center.x - highlightWidth / 2f, center.y - highlightHeight / 2f),
                    size = Size(highlightWidth, highlightHeight),
                    blendMode = BlendMode.Screen
                )
            }
        }
    }
}

/**
 * A procedural wrapped-fabric silhouette rather than a flat color band: extrapolates above the
 * tracked hairline (MediaPipe has no landmarks there) using the face's own height/width as a
 * scale reference, then draws a couple of fold lines so it reads as fabric, not a skin tint.
 */
private fun DrawScope.drawGeleWrap(
    layer: MakeupLayer,
    point: (Int) -> NormalizedLandmark?,
    toCanvasOffset: (Float, Float) -> Offset
) {
    val chin = point(CHIN_INDEX) ?: return
    val foreheadCenter = point(FOREHEAD_CENTER_INDEX) ?: return
    val rightTemple = point(RIGHT_TEMPLE) ?: return
    val leftTemple = point(LEFT_TEMPLE) ?: return
    val foreheadLine = FOREHEAD_TOP_INDICES.asList().mapNotNull { point(it) }
    if (foreheadLine.size < 3) return

    val faceHeightNorm = kotlin.math.abs(chin.y - foreheadCenter.y)
    val domeTopNormY = (foreheadCenter.y - faceHeightNorm * 0.85f).coerceAtLeast(-0.6f)
    val domeCenterNormX = foreheadCenter.x

    val rightAnchor = toCanvasOffset(rightTemple.x, rightTemple.y)
    val leftAnchor = toCanvasOffset(leftTemple.x, leftTemple.y)
    val domePeak = toCanvasOffset(domeCenterNormX, domeTopNormY)
    val domeRight = toCanvasOffset(rightTemple.x - faceHeightNorm * 0.08f, rightTemple.y - faceHeightNorm * 0.35f)
    val domeLeft = toCanvasOffset(leftTemple.x + faceHeightNorm * 0.08f, leftTemple.y - faceHeightNorm * 0.35f)

    val baseAlpha = (0.75f + layer.intensity * 0.25f).coerceIn(0f, 1f)
    val wrap = Path().apply {
        moveTo(rightAnchor.x, rightAnchor.y)
        // Across the forehead (the fabric's lower edge, resting on the hairline/brow line).
        foreheadLine.forEach { p ->
            val o = toCanvasOffset(p.x, p.y)
            lineTo(o.x, o.y)
        }
        lineTo(leftAnchor.x, leftAnchor.y)
        // Up and over the crown - extrapolated above the tracked face, so this is necessarily
        // approximate, but the curve keeps it proportional to this specific face's width/height.
        cubicTo(domeLeft.x, domeLeft.y, domePeak.x, domePeak.y, domePeak.x, domePeak.y)
        cubicTo(domePeak.x, domePeak.y, domeRight.x, domeRight.y, rightAnchor.x, rightAnchor.y)
        close()
    }
    drawPath(path = wrap, color = layer.color.copy(alpha = baseAlpha))

    // Fold lines: a darker sweep (multiply) plus a lighter sheen (screen-ish via low-alpha
    // white) so the wrap reads as layered fabric rather than a flat cutout.
    val foldColorDark = layer.color.copy(alpha = 0.35f)
    val foldHighlight = Color.White.copy(alpha = 0.18f)
    for (t in floatArrayOf(0.35f, 0.62f)) {
        val start = Offset(
            rightAnchor.x + (domePeak.x - rightAnchor.x) * t,
            rightAnchor.y + (domePeak.y - rightAnchor.y) * t
        )
        val end = Offset(
            leftAnchor.x + (domePeak.x - leftAnchor.x) * t,
            leftAnchor.y + (domePeak.y - leftAnchor.y) * t
        )
        val fold = Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(domePeak.x, domePeak.y + (start.y - domePeak.y) * 0.3f, end.x, end.y)
        }
        drawPath(path = fold, color = foldColorDark, style = Stroke(width = 3f), blendMode = BlendMode.Multiply)
        drawPath(
            path = fold,
            color = foldHighlight,
            style = Stroke(width = 1.5f)
        )
    }
}

/** Mascara reads as near-black/near-brown regardless of style, so [LashStyle] drives density,
 *  length and darkness rather than picking a color - short strokes fanning out from the lash
 *  line stand in for individual lashes better than a single flat line would. */
private fun DrawScope.drawLashes(
    style: LashStyle,
    point: (Int) -> NormalizedLandmark?,
    toCanvasOffset: (Float, Float) -> Offset,
    drawW: Float,
    drawH: Float
) {
    // Dramatic/Doll Eye's alpha used to reach 0.85-0.95 - at that opacity, a full-length stroke
    // planted right at the eye's sharp outer/inner corner (see the sin() taper below) read as a
    // stray dark line escaping the eye shape entirely ("drawings coming out of someone's eye"),
    // not a lash. Capped lower across the board so even the boldest style stays a lash fringe.
    val (tickCount, lengthFraction, alpha) = when (style) {
        LashStyle.NATURAL -> Triple(5, 0.04f, 0.45f)
        LashStyle.WISPY -> Triple(7, 0.055f, 0.55f)
        LashStyle.DRAMATIC -> Triple(9, 0.075f, 0.65f)
        LashStyle.DOLL_EYE -> Triple(9, 0.065f, 0.7f)
    }
    val color = Color(0xFF14100D).copy(alpha = alpha)
    val strokeWidth = minOf(drawW, drawH) * 0.006f

    for (contour in listOf(RIGHT_EYE_UPPER_CONTOUR, LEFT_EYE_UPPER_CONTOUR)) {
        val lidPts = contour.asList().mapNotNull { point(it) }
        if (lidPts.size < 3) continue
        val eyeWidthNorm = kotlin.math.abs(lidPts.last().x - lidPts.first().x)
        val lashLength = eyeWidthNorm * lengthFraction
        val step = (lidPts.size - 1).toFloat() / (tickCount - 1).coerceAtLeast(1)
        for (i in 0 until tickCount) {
            val pos = (i * step).coerceIn(0f, (lidPts.size - 1).toFloat())
            val lowIdx = pos.toInt().coerceIn(0, lidPts.size - 1)
            val highIdx = (lowIdx + 1).coerceAtMost(lidPts.size - 1)
            val frac = pos - lowIdx
            val a = lidPts[lowIdx]
            val b = lidPts[highIdx]
            val baseX = a.x + (b.x - a.x) * frac
            val baseY = a.y + (b.y - a.y) * frac
            // Fan the tick outward/upward from the lash line rather than straight up, echoing
            // natural lash curl - stronger fan toward the outer corner (start/end of the arc).
            val posFraction = pos / (lidPts.size - 1).toFloat()
            val fanBias = posFraction - 0.5f
            // A real lash fringe is shortest at both corners and fullest across the middle two
            // thirds of the lid - a uniform length instead put a full-length stroke right at the
            // eye's sharp corner point, which reads as a stray line escaping the eye shape rather
            // than a lash. sin() tapers smoothly to near-zero at both ends without going to zero
            // (a corner still gets a short lash, just not a long one).
            val taperedLength = lashLength * (0.35f + 0.65f * kotlin.math.sin(Math.PI.toFloat() * posFraction))
            val base = toCanvasOffset(baseX, baseY)
            val tip = toCanvasOffset(baseX + fanBias * taperedLength * 0.25f, baseY - taperedLength)
            drawLine(color = color, start = base, end = tip, strokeWidth = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/**
 * Renders the same makeup layers as [FaceCanvas], but onto a plain [Bitmap] at the photo's own
 * pixel size rather than into a live Compose Canvas - used by the before/after comparison and
 * by "Save Formulation" so the exported/compared image is pixel-identical to the live preview,
 * not a separate approximation of it.
 */
fun renderMakeupBitmap(
    photo: Bitmap,
    landmarks: FaceLandmarkResult,
    foundation: MakeupLayer?,
    cheek: MakeupLayer?,
    lips: MakeupLayer?,
    eyeshadow: MakeupLayer? = null,
    eyeliner: MakeupLayer? = null,
    eyeColor: MakeupLayer? = null,
    lashStyle: LashStyle? = null,
    brow: MakeupLayer? = null,
    gele: MakeupLayer? = null
): Bitmap {
    val base = composeMakeup(photo, landmarks, foundation, cheek, lips, brow)
    val output = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
    val androidCanvas = android.graphics.Canvas(output)
    val composeCanvas = androidx.compose.ui.graphics.Canvas(androidCanvas)
    val photoWidth = base.width.toFloat()
    val photoHeight = base.height.toFloat()

    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = composeCanvas,
        size = Size(photoWidth, photoHeight)
    ) {
        drawImage(base.asImageBitmap())
        drawMakeupLayers(
            landmarks = landmarks,
            offsetX = 0f,
            offsetY = 0f,
            drawW = photoWidth,
            drawH = photoHeight,
            gele = gele,
            foundation = null,
            brow = null,
            eyeshadow = eyeshadow,
            eyeliner = eyeliner,
            eyeColor = eyeColor,
            lashStyle = lashStyle,
            cheek = null,
            lips = null
        )
    }
    return output
}
