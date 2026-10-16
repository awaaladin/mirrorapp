package com.mirror.app.ui.editor

import com.mirror.app.domain.model.ShadeFinish
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class MakeupCompositorTest {

    @Test
    fun fillPolygonFillsOnlyInside() {
        val mask = FloatArray(10 * 10)
        MakeupCompositor.fillPolygon(mask, 10, 10, floatArrayOf(2f, 8f, 8f, 2f), floatArrayOf(2f, 2f, 8f, 8f), 1f)
        assertEquals(1f, mask[5 * 10 + 5], 0f)
        assertEquals(0f, mask[0], 0f)
        assertEquals(0f, mask[9 * 10 + 9], 0f)
    }

    @Test
    fun boxBlurPreservesTotalMassAwayFromEdges() {
        val a = FloatArray(41 * 41)
        a[20 * 41 + 20] = 100f
        MakeupCompositor.boxBlur(a, 41, 41, 3)
        assertEquals(100f, a.sum(), 0.5f)
        assertTrue(a[20 * 41 + 20] < 100f)
    }

    @Test
    fun noLayersReturnsUnchangedCopy() {
        val px = IntArray(16) { 0xFF336699.toInt() }
        val out = MakeupCompositor.compose(px, 4, 4, FloatArray(2 * 478))
        assertArrayEquals(px, out)
        assertTrue(out !== px)
    }

    /** Runs the real compositor on real faces exported from MediaPipe, writing out_<name>.rgb.
     *  Set MIRROR_FIXTURE_DIR to a folder holding <name>.rgb (raw RGB face crop) + <name>.meta ("w h") + <name>.json (normalized
     *  landmarks, flattened x,y). Skipped when unset - the photos are real people, so they are not
     *  checked into the repo. */
    @Test
    fun rendersRealFaceFixtures() {
        val dir = System.getenv("MIRROR_FIXTURE_DIR")
        assumeTrue("MIRROR_FIXTURE_DIR not set", dir != null)
        val names = File(dir!!).listFiles { f -> f.extension == "json" }!!.map { it.nameWithoutExtension }
        assertTrue(names.isNotEmpty())
        for (name in names) {
            val (w, h) = File(dir, "$name.meta").readText().trim().split(" ").map { it.toInt() }
            val raw = File(dir, "$name.rgb").readBytes()
            val px = IntArray(w * h) {
                0xFF000000.toInt() or ((raw[3 * it].toInt() and 0xFF) shl 16) or ((raw[3 * it + 1].toInt() and 0xFF) shl 8) or (raw[3 * it + 2].toInt() and 0xFF)
            }
            val pts = File(dir, "$name.json").readText().trim('[', ']', ' ', '\n').split(",").map { it.trim().toFloat() }.toFloatArray()

            val warm = CompositorLayer(0xFFC68863.toInt(), 0.5f, ShadeFinish.SATIN)
            val out = MakeupCompositor.compose(
                px, w, h, pts,
                foundation = warm,
                cheek = CompositorLayer(0xFFC1552E.toInt(), 0.5f),
                brow = CompositorLayer(0xFF3B2418.toInt(), 0.5f),
                lips = CompositorLayer(0xFF7B3B2E.toInt(), 0.5f, ShadeFinish.GLOSSY)
            )
            assertTrue("$name: makeup changed nothing", !px.contentEquals(out))
            // Corners are far outside the face: they must be untouched (no bleed onto background).
            for (c in listOf(0, w - 1, (h - 1) * w, h * w - 1)) assertEquals(px[c], out[c])

            val bytes = ByteArray(out.size * 3)
            for (i in out.indices) {
                bytes[3 * i] = (out[i] shr 16).toByte(); bytes[3 * i + 1] = (out[i] shr 8).toByte(); bytes[3 * i + 2] = out[i].toByte()
            }
            File(dir, "out_$name.rgb").writeBytes(bytes)
        }
    }
}
