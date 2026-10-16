package com.mirror.app.ui.editor

import androidx.compose.ui.graphics.toArgb
import com.mirror.app.domain.model.ShadeFinish
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One makeup layer for [MakeupCompositor]: an ARGB color (alpha ignored), strength 0..1 and finish. */
class CompositorLayer(val argb: Int, val intensity: Float, val finish: ShadeFinish = ShadeFinish.MATTE)

/**
 * Feathered, mask-based compositing for foundation, blush, brows and lips.
 *
 * Replaces the earlier approach of painting flat translucent paths straight onto the photo, which
 * tested badly on real faces (see the git history for the before/after): hard mask-like edges,
 * foundation bleeding onto hair/hats via a forehead lift, oversized lips tinting the teeth, and
 * foundation that repainted the skin instead of shifting its tone.
 *
 * Pure Kotlin over ARGB pixel arrays (no android.graphics) so it runs in plain JVM unit tests
 * against real photos; [composeMakeup] is the Bitmap wrapper the UI uses.
 *
 * [points] holds normalized landmarks interleaved as x0,y0,x1,y1,... for a [width]x[height] image.
 */
object MakeupCompositor {

    // Standard MediaPipe Face Mesh outlines.
    private val OVAL = intArrayOf(
        10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365, 379, 378, 400, 377,
        152, 148, 176, 149, 150, 136, 172, 58, 132, 93, 234, 127, 162, 21, 54, 103, 67, 109
    )
    private val LIPS_OUTER = intArrayOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291, 375, 321, 405, 314, 17, 84, 181, 91, 146)
    private val LIPS_INNER = intArrayOf(78, 191, 80, 81, 82, 13, 312, 311, 310, 415, 308, 324, 318, 402, 317, 14, 87, 178, 88, 95)
    private val RIGHT_EYE = intArrayOf(33, 246, 161, 160, 159, 158, 157, 173, 133, 155, 154, 153, 145, 144, 163, 7)
    private val LEFT_EYE = intArrayOf(362, 398, 384, 385, 386, 387, 388, 466, 263, 249, 390, 373, 374, 380, 381, 382)
    private val RIGHT_BROW = intArrayOf(46, 53, 52, 65, 55, 107, 66, 105, 63, 70)
    private val LEFT_BROW = intArrayOf(276, 283, 282, 295, 285, 336, 296, 334, 293, 300)

    // Cheek apple + temple on each side: their vector gives the cheekbone angle for blush.
    private val BLUSH_SIDES = listOf(50 to 234, 280 to 454)

    /** Returns a new pixel array with the layers applied; [pixels] is not modified. Layers that are
     *  null are skipped, and with no layers the result is an unchanged copy. */
    fun compose(
        pixels: IntArray,
        width: Int,
        height: Int,
        points: FloatArray,
        foundation: CompositorLayer? = null,
        cheek: CompositorLayer? = null,
        brow: CompositorLayer? = null,
        lips: CompositorLayer? = null
    ): IntArray {
        val out = pixels.copyOf()
        if (points.size < 2 * 468 || (foundation == null && cheek == null && brow == null && lips == null)) return out

        val ctx = Ctx(width, height, points)
        val ovalSoft by lazy { ctx.faceMask(shrink = true) }

        if (foundation != null) applyFoundation(out, ctx, foundation)
        if (cheek != null) applyBlush(out, ctx, cheek, ovalSoft)
        if (brow != null) applyBrows(out, ctx, brow)
        if (lips != null) applyLips(out, ctx, lips)
        return out
    }

    private class Ctx(val w: Int, val h: Int, val pts: FloatArray) {
        fun x(i: Int) = pts[2 * i] * w
        fun y(i: Int) = pts[2 * i + 1] * h

        /** Face width in pixels - the scale every feature size is derived from. */
        val faceWidth: Float = run {
            var lo = 1f; var hi = 0f
            for (i in 0 until pts.size / 2) { lo = min(lo, pts[2 * i]); hi = max(hi, pts[2 * i]) }
            (hi - lo) * w
        }

        fun polygon(indices: IntArray, mask: FloatArray, value: Float, scaleX: Float = 1f, scaleY: Float = 1f) {
            var cx = 0f; var cy = 0f
            for (i in indices) { cx += x(i); cy += y(i) }
            cx /= indices.size; cy /= indices.size
            val xs = FloatArray(indices.size) { cx + (x(indices[it]) - cx) * scaleX }
            val ys = FloatArray(indices.size) { cy + (y(indices[it]) - cy) * scaleY }
            fillPolygon(mask, w, h, xs, ys, value)
        }

        /** Soft face-oval mask, kept off the hairline (no forehead lift) and inset from the edge. */
        fun faceMask(shrink: Boolean): FloatArray {
            val mask = FloatArray(w * h)
            if (shrink) polygon(OVAL, mask, 1f, 0.95f, 0.97f) else polygon(OVAL, mask, 1f)
            boxBlur(mask, w, h, max(2, (faceWidth * 0.035f).toInt()))
            return mask
        }
    }

    private fun applyFoundation(px: IntArray, ctx: Ctx, layer: CompositorLayer) {
        val w = ctx.w; val h = ctx.h
        val mask = FloatArray(w * h)
        ctx.polygon(OVAL, mask, 1f, 0.95f, 0.97f)
        // Keep foundation off the eyes, brows and lips - they have their own layers (or none).
        ctx.polygon(RIGHT_EYE, mask, 0f); ctx.polygon(LEFT_EYE, mask, 0f)
        ctx.polygon(RIGHT_BROW, mask, 0f); ctx.polygon(LEFT_BROW, mask, 0f)
        ctx.polygon(LIPS_OUTER, mask, 0f)

        val gain = toneGain(px, mask, layer.argb) ?: return
        boxBlur(mask, w, h, max(2, (ctx.faceWidth * 0.035f).toInt()))
        val strength = 0.25f + 0.6f * layer.intensity.coerceIn(0f, 1f)
        for (i in px.indices) {
            val a = mask[i] * strength
            if (a <= 0.002f) continue
            val p = px[i]
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            px[i] = mix(p, (r * gain[0]).coerceAtMost(255f), (g * gain[1]).coerceAtMost(255f), (b * gain[2]).coerceAtMost(255f), a)
        }
    }

    /** Per-channel gain that moves the face's current typical skin color to the shade. Scaling the
     *  real pixels (rather than painting the shade over them) keeps the photo's shading, texture
     *  and highlights, so it reads as a tone shift and not a flat mask. Null if no skin sampled. */
    private fun toneGain(px: IntArray, mask: FloatArray, shadeArgb: Int): FloatArray? {
        val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
        val step = max(1, px.size / 6000)
        var i = 0
        while (i < px.size) {
            if (mask[i] > 0.9f) {
                val p = px[i]
                rs += (p shr 16) and 0xFF; gs += (p shr 8) and 0xFF; bs += p and 0xFF
            }
            i += step
        }
        if (rs.size < 30) return null
        fun median(l: ArrayList<Int>): Float { l.sort(); return l[l.size / 2].toFloat() }
        val med = floatArrayOf(median(rs), median(gs), median(bs))
        val target = floatArrayOf(
            ((shadeArgb shr 16) and 0xFF).toFloat(), ((shadeArgb shr 8) and 0xFF).toFloat(), (shadeArgb and 0xFF).toFloat()
        )
        return FloatArray(3) { (target[it] / max(med[it], 1f)).coerceIn(0.4f, 2.5f) }
    }

    private fun applyBlush(px: IntArray, ctx: Ctx, layer: CompositorLayer, faceSoft: FloatArray) {
        val w = ctx.w; val h = ctx.h
        val mask = FloatArray(w * h)
        for ((apple, temple) in BLUSH_SIDES) {
            val ax = ctx.x(apple); val ay = ctx.y(apple)
            val angle = atan2(ctx.y(temple) - ay, ctx.x(temple) - ax)
            fillEllipse(mask, w, h, ax, ay, ctx.faceWidth * 0.17f, ctx.faceWidth * 0.09f, angle)
        }
        boxBlur(mask, w, h, max(2, (ctx.faceWidth * 0.07f).toInt()))
        val k = layer.intensity.coerceIn(0f, 1f) * 0.6f
        multiplyTint(px, mask, layer.argb, k, faceSoft)
    }

    private fun applyBrows(px: IntArray, ctx: Ctx, layer: CompositorLayer) {
        val mask = FloatArray(ctx.w * ctx.h)
        ctx.polygon(RIGHT_BROW, mask, 1f); ctx.polygon(LEFT_BROW, mask, 1f)
        boxBlur(mask, ctx.w, ctx.h, 1)
        multiplyTint(px, mask, layer.argb, 0.2f + 0.3f * layer.intensity.coerceIn(0f, 1f), null)
    }

    private fun applyLips(px: IntArray, ctx: Ctx, layer: CompositorLayer) {
        val w = ctx.w; val h = ctx.h
        val mask = FloatArray(w * h)
        ctx.polygon(LIPS_OUTER, mask, 1f)
        ctx.polygon(LIPS_INNER, mask, 0f) // the open mouth (teeth/tongue) is not lip
        boxBlur(mask, w, h, max(1, (ctx.faceWidth * 0.006f).toInt()))
        val i = layer.intensity.coerceIn(0f, 1f)
        val tr = ((layer.argb shr 16) and 0xFF).toFloat(); val tg = ((layer.argb shr 8) and 0xFF).toFloat(); val tb = (layer.argb and 0xFF).toFloat()
        for (n in px.indices) {
            val a = mask[n] * i
            if (a <= 0.002f) continue
            val p = px[n]
            // Blend toward the shade but keep 55% of the real lip, so lines and texture survive.
            val r = ((p shr 16) and 0xFF) * 0.55f + tr * 0.45f
            val g = ((p shr 8) and 0xFF) * 0.55f + tg * 0.45f
            val b = (p and 0xFF) * 0.55f + tb * 0.45f
            px[n] = mix(p, r, g, b, a)
        }

        val highlight = when (layer.finish) {
            ShadeFinish.GLOSSY -> 0.4f
            ShadeFinish.SHIMMER -> 0.28f
            ShadeFinish.SATIN -> 0.14f
            ShadeFinish.MATTE -> 0f
        }
        if (highlight > 0f) {
            // A soft specular streak on the lower lip, clipped to the lip itself.
            val mouthW = abs(ctx.x(291) - ctx.x(61))
            val cx = (ctx.x(14) + ctx.x(17)) / 2f
            val cy = (ctx.y(14) + ctx.y(17)) / 2f
            val spec = FloatArray(w * h)
            fillEllipse(spec, w, h, cx, cy, mouthW * 0.16f, max(1.5f, abs(ctx.y(17) - ctx.y(14)) * 0.22f), 0f)
            boxBlur(spec, w, h, max(1, (mouthW * 0.03f).toInt()))
            for (n in px.indices) {
                val s = spec[n] * mask[n] * highlight
                if (s <= 0.002f) continue
                val p = px[n]
                px[n] = mix(p, 255f, 255f, 255f, s)
            }
        }
    }

    /** out = px * (1-k*m) + (px * color/255) * (k*m): a multiply tint that keeps the photo's detail. */
    private fun multiplyTint(px: IntArray, mask: FloatArray, argb: Int, k: Float, clip: FloatArray?) {
        val cr = ((argb shr 16) and 0xFF) / 255f; val cg = ((argb shr 8) and 0xFF) / 255f; val cb = (argb and 0xFF) / 255f
        for (i in px.indices) {
            val a = mask[i] * k * (clip?.get(i) ?: 1f)
            if (a <= 0.002f) continue
            val p = px[i]
            val r = ((p shr 16) and 0xFF).toFloat(); val g = ((p shr 8) and 0xFF).toFloat(); val b = (p and 0xFF).toFloat()
            px[i] = mix(p, r * cr, g * cg, b * cb, a)
        }
    }

    private fun mix(p: Int, r: Float, g: Float, b: Float, a: Float): Int {
        val or = (((p shr 16) and 0xFF) * (1 - a) + r * a).toInt().coerceIn(0, 255)
        val og = (((p shr 8) and 0xFF) * (1 - a) + g * a).toInt().coerceIn(0, 255)
        val ob = ((p and 0xFF) * (1 - a) + b * a).toInt().coerceIn(0, 255)
        return (p and 0xFF000000.toInt()) or (or shl 16) or (og shl 8) or ob
    }

    // ---- rasterisation helpers (visible for tests) ----

    /** Even-odd scanline polygon fill of constant [value] into [mask]. */
    internal fun fillPolygon(mask: FloatArray, w: Int, h: Int, xs: FloatArray, ys: FloatArray, value: Float) {
        val n = xs.size
        if (n < 3) return
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (y in ys) { minY = min(minY, y); maxY = max(maxY, y) }
        val yStart = max(0, kotlin.math.ceil(minY - 0.5f).toInt())
        val yEnd = min(h - 1, kotlin.math.floor(maxY - 0.5f).toInt())
        val cross = FloatArray(n)
        for (row in yStart..yEnd) {
            val sy = row + 0.5f
            var count = 0
            for (i in 0 until n) {
                val j = (i + 1) % n
                val y0 = ys[i]; val y1 = ys[j]
                if ((y0 <= sy && y1 > sy) || (y1 <= sy && y0 > sy)) {
                    cross[count++] = xs[i] + (sy - y0) / (y1 - y0) * (xs[j] - xs[i])
                }
            }
            java.util.Arrays.sort(cross, 0, count)
            var k = 0
            while (k + 1 < count) {
                val xa = max(0, kotlin.math.ceil(cross[k] - 0.5f).toInt())
                val xb = min(w - 1, kotlin.math.floor(cross[k + 1] - 0.5f).toInt())
                for (x in xa..xb) mask[row * w + x] = value
                k += 2
            }
        }
    }

    /** Filled ellipse (semi-axes [rx],[ry], rotated by [angle] radians) written as 1.0. */
    internal fun fillEllipse(mask: FloatArray, w: Int, h: Int, cx: Float, cy: Float, rx: Float, ry: Float, angle: Float) {
        val c = cos(angle); val s = sin(angle)
        val reach = max(rx, ry)
        val x0 = max(0, (cx - reach).toInt()); val x1 = min(w - 1, (cx + reach).toInt() + 1)
        val y0 = max(0, (cy - reach).toInt()); val y1 = min(h - 1, (cy + reach).toInt() + 1)
        for (y in y0..y1) for (x in x0..x1) {
            val dx = x - cx; val dy = y - cy
            val u = (dx * c + dy * s) / rx
            val v = (-dx * s + dy * c) / ry
            if (u * u + v * v <= 1f) mask[y * w + x] = 1f
        }
    }

    /** Approximate Gaussian blur: three separable box-blur passes of [radius], edges clamped. */
    internal fun boxBlur(a: FloatArray, w: Int, h: Int, radius: Int) {
        if (radius < 1) return
        val tmp = FloatArray(max(w, h))
        repeat(3) {
            for (y in 0 until h) {
                val base = y * w
                blurLine(a, base, 1, w, radius, tmp)
            }
            for (x in 0 until w) blurLine(a, x, w, h, radius, tmp)
        }
    }

    private fun blurLine(a: FloatArray, start: Int, stride: Int, len: Int, r: Int, tmp: FloatArray) {
        val window = 2 * r + 1
        var sum = 0f
        for (k in -r..r) sum += a[start + k.coerceIn(0, len - 1) * stride]
        for (i in 0 until len) {
            tmp[i] = sum / window
            sum += a[start + (i + r + 1).coerceAtMost(len - 1) * stride] - a[start + (i - r).coerceAtLeast(0) * stride]
        }
        for (i in 0 until len) a[start + i * stride] = tmp[i]
    }
}

/** Longest side the compositor works at: plenty for a phone screen, and keeps per-slider-drag
 *  recomposition fast (a 12MP photo would be ~100x the pixels). */
private const val COMPOSE_MAX_SIDE = 1200

/**
 * Applies foundation, blush, brows and lips to [photo] (already cropped to the face, with
 * [landmarks] normalized to it) and returns the result, scaled down to at most
 * [COMPOSE_MAX_SIDE] on its longest side. Returns [photo] itself when there is nothing to apply.
 * Call off the main thread.
 */
fun composeMakeup(
    photo: android.graphics.Bitmap,
    landmarks: com.mirror.app.domain.model.FaceLandmarkResult,
    foundation: MakeupLayer?,
    cheek: MakeupLayer?,
    lips: MakeupLayer?,
    brow: MakeupLayer?
): android.graphics.Bitmap {
    if (foundation == null && cheek == null && lips == null && brow == null) return photo
    val scale = minOf(1f, COMPOSE_MAX_SIDE.toFloat() / maxOf(photo.width, photo.height))
    val work = if (scale < 1f) {
        android.graphics.Bitmap.createScaledBitmap(photo, (photo.width * scale).toInt().coerceAtLeast(1), (photo.height * scale).toInt().coerceAtLeast(1), true)
    } else photo
    val w = work.width; val h = work.height
    val pixels = IntArray(w * h)
    work.getPixels(pixels, 0, w, 0, 0, w, h)
    val pts = FloatArray(landmarks.points.size * 2)
    landmarks.points.forEachIndexed { i, p -> pts[2 * i] = p.x; pts[2 * i + 1] = p.y }

    fun layer(l: MakeupLayer?) = l?.let { CompositorLayer(it.color.toArgb(), it.intensity, it.finish) }
    val result = MakeupCompositor.compose(pixels, w, h, pts, layer(foundation), layer(cheek), layer(brow), layer(lips))

    val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    out.setPixels(result, 0, w, 0, 0, w, h)
    return out
}
