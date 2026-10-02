package io.github.patissiermongs.foldkey.ui

import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.input.ThumbZones.Zone
import io.github.patissiermongs.foldkey.input.TypingCalibration
import io.github.patissiermongs.foldkey.input.TypingPrompts
import io.github.patissiermongs.foldkey.layout.GeometrySpec
import io.github.patissiermongs.foldkey.layout.Key
import io.github.patissiermongs.foldkey.layout.KeyboardGeometry
import io.github.patissiermongs.foldkey.layout.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

class TypingSessionTest {
    private val px = 368f / 25.4f
    private val width = 2184f

    private fun geometry(layer: Layer, p: TypingCalibration.Placement): KeyboardGeometry {
        val spec = GeometrySpec(
            widthPx = width,
            pxPerMmX = px,
            pxPerMmY = px,
            topPx = 0f,
            rowHeightMm = p.rowMm,
            gapMm = 0.9f,
            sidePaddingMm = 0.5f,
            splitMarginLeftPx = p.leftMm * px,
            splitMarginRightPx = p.rightMm * px,
            bottomPaddingPx = 0f,
            maxUnitMm = 11.5f,
            splitUnitMm = p.unitMm,
            ghostUnits = 1f,
            liftPx = p.liftMm * px,
        )
        return if (layer == Layer.GENERAL) KeyboardGeometry.split(Layouts.generalSplit, spec)
        else KeyboardGeometry.split(Layouts.split, spec, Layouts.pad)
    }

    private fun session(code: Float = 4.3f, general: Float = 6.1f, row: Float = 8.8f): TypingSession {
        val anchors = TypingCalibration.anchors(Zone(13.7f, 47.5f, 6.2f, 50.3f), Zone(17.4f, 52.2f, 6.2f, 50.3f))
        val plan = TypingSession.Plan(
            layers = listOf(Layer.CODE, Layer.GENERAL),
            start = mapOf(Layer.CODE to code, Layer.GENERAL to general),
            startRowMm = row,
            anchors = mapOf(Layer.CODE to anchors, Layer.GENERAL to anchors),
            widthPx = width,
            pxPerMmX = px,
            pxPerMmY = px,
            maxHeightMm = 75f,
        )
        return TypingSession(plan) { layer, p -> geometry(layer, p) }
    }

    private class Typist(val sx: Float, val sy: Float, val ox: Float, val oy: Float, seed: Long) {
        val random = Random(seed)
        var taps = 0
    }

    private fun typeLayer(s: TypingSession, t: Typist) {
        val layer = s.layer
        while (!s.finished && s.layer == layer) {
            val c = s.expected!!
            val g = s.geometry
            val key = g.keys.first { k ->
                !k.ghost && if (c == ' ') k.def.action == KeyAction.Space else (k.def.action as? KeyAction.Char)?.base == c
            }
            val x = key.face.centerX + (t.ox + t.sx * t.random.nextGaussian().toFloat()) * px
            val y = key.face.centerY + (t.oy + t.sy * t.random.nextGaussian().toFloat()) * px
            s.tap(x.coerceIn(1f, width - 1f), y.coerceIn(1f, g.heightPx - 1f))
            t.taps++
            check(t.taps < 5000)
        }
    }

    @Test
    fun smallKeysGrowUntilTheTypingSpreadFits() {
        val s = session()
        val t = Typist(1.4f, 1.6f, 0.6f, 1.0f, 11)
        typeLayer(s, t)
        val r = s.results.single()
        assertEquals(Layer.CODE, r.layer)
        assertTrue("${r.rounds.size} rounds", r.rounds.size in 2..3)
        assertEquals(4.3f, r.rounds.first().unitMm, 0.01f)
        assertTrue("first round ${r.rounds.first().hitRate}", r.rounds.first().hitRate < 0.9f)
        assertTrue("unit ${r.unitMm}", r.unitMm!! in 5.2f..6.6f)
        assertEquals(8.8f, r.rowMm!!, 0.01f)
        val last = r.rounds.last()
        assertTrue("last ${last.hitRate} first ${r.rounds.first().hitRate}", last.hitRate > r.rounds.first().hitRate + 0.05f)
        val estimate = TypingCalibration.hitRate(last.samples, r.analysis!!, r.unitMm!!, r.rowMm!!)
        assertTrue("estimate $estimate", estimate >= 0.9f)
    }

    @Test
    fun calibrationSeedsOffsetsAndKeepsOneRowHeightForBothLayers() {
        val s = session()
        val t = Typist(1.0f, 2.6f, 0.6f, 1.0f, 12)
        typeLayer(s, t)
        typeLayer(s, t)
        assertTrue(s.finished)
        val c = s.calibration()
        val code = c.placements.getValue(Layer.CODE)
        val general = c.placements.getValue(Layer.GENERAL)
        assertEquals(code.rowMm, general.rowMm, 1e-4f)
        assertEquals(code.liftMm, general.liftMm, 1e-4f)
        assertTrue("row ${code.rowMm}", code.rowMm >= 9.5f)
        val model = c.offsets.getValue(Layer.CODE)
        val seeded = (0 until model.zones).filter { model.samples(it) > 0 }
        assertTrue(seeded.size >= 10)
        for (z in seeded) {
            assertEquals(20, model.samples(z))
            val (dx, dy) = model.meanMm(z)
            assertEquals("zone $z x", 0.6f, dx, 0.6f)
            assertEquals("zone $z y", 1.0f, dy, 1.2f)
        }
        assertEquals(0.6f, seeded.map { model.meanMm(it).first }.average().toFloat(), 0.25f)
        assertEquals(1.0f, seeded.map { model.meanMm(it).second }.average().toFloat(), 0.4f)
    }

    @Test
    fun bothLayersGetTheTallerRowOfTheTwo() {
        val s = session()
        typeLayer(s, Typist(0.8f, 3.2f, 0f, 0f, 15))
        typeLayer(s, Typist(0.8f, 0.9f, 0f, 0f, 16))
        val (code, general) = s.results
        assertTrue("code row ${code.rowMm}", code.rowMm!! >= 9.5f)
        assertEquals(8.8f, general.rowMm!!, 0.01f)
        val c = s.calibration()
        assertEquals(code.rowMm!!, c.placements.getValue(Layer.CODE).rowMm, 1e-4f)
        assertEquals(code.rowMm!!, c.placements.getValue(Layer.GENERAL).rowMm, 1e-4f)
    }

    @Test
    fun skippedLayerIsLeftAsItWas() {
        val s = session()
        s.skipLayer()
        assertEquals(Layer.GENERAL, s.layer)
        typeLayer(s, Typist(0.8f, 0.9f, 0f, 0f, 13))
        val c = s.calibration()
        assertNull(c.placements[Layer.CODE])
        assertTrue(Layer.GENERAL in c.placements)
        assertNull(s.results.first().unitMm)
    }

    @Test
    fun accurateTypingKeepsTheStartingSize() {
        val s = session(code = 7f)
        typeLayer(s, Typist(0.5f, 0.6f, 0f, 0f, 14))
        val r = s.results.single()
        assertEquals(1, r.rounds.size)
        assertEquals(7f, r.unitMm!!, 0.01f)
    }

    @Test
    fun hangulPromptsComposeAsTheyAreTyped() {
        val s = session()
        s.skipLayer()
        while (s.line.none { it.code >= 0xAC00 }) {
            val c = s.expected!!
            val key = s.geometry.keys.first { k ->
                !k.ghost && if (c == ' ') k.def.action == KeyAction.Space else (k.def.action as? KeyAction.Char)?.base == c
            }
            s.tap(key.face.centerX, key.face.centerY)
        }
        val word = s.line.substringBefore(' ')
        for (c in TypingPrompts.keys(word)!!) {
            val key = s.geometry.keys.first { k ->
                !k.ghost && if (c == ' ') k.def.action == KeyAction.Space else (k.def.action as? KeyAction.Char)?.base == c
            }
            assertEquals(TypingSession.Outcome.HIT, s.tap(key.face.centerX, key.face.centerY))
        }
        assertEquals(word, s.typed)
    }

    private fun target(s: TypingSession, c: Char): Key = s.geometry.keys.first { k ->
        !k.ghost && if (c == ' ') k.def.action == KeyAction.Space else (k.def.action as? KeyAction.Char)?.base == c
    }

    @Test
    fun aMissKeepsThePromptAndIsStillMeasured() {
        val s = session()
        val c = s.expected!!
        val key = target(s, c)
        val other = s.geometry.keys.filter { k ->
            !k.ghost && k.face.centerY == key.face.centerY && (k.def.action as? KeyAction.Char)?.let { it.base != c } == true
        }.minBy { abs(it.face.centerX - key.face.centerX) }
        assertEquals(TypingSession.Outcome.MISS, s.tap(other.face.centerX, other.face.centerY))
        assertEquals(c, s.expected)
        assertEquals(0, s.charIndex)
        assertEquals(1, s.round.taps)
        assertEquals(0, s.round.hits)
        assertEquals((other.face.centerX - key.face.centerX) / px, s.round.samples.single().dxMm, 0.01f)
        assertEquals(TypingSession.Outcome.HIT, s.tap(key.face.centerX, key.face.centerY))
        assertEquals(2, s.round.samples.size)
    }

    @Test
    fun laterRoundsJudgeTapsWithTheCorrectionsOfTheRoundBefore() {
        val s = session()
        val pattern = floatArrayOf(-0.5f, 0.5f, 1.5f, 2.5f, 3.5f)
        var i = 0
        var outcome = TypingSession.Outcome.IGNORED
        while (s.roundNumber == 1 && s.layer == Layer.CODE) {
            val c = s.expected!!
            val key = target(s, c)
            val dx = if (c == ' ') 0f else pattern[i++ % pattern.size]
            outcome = s.tap(key.face.centerX + dx * px, key.face.centerY)
            check(i < 3000)
        }
        assertEquals(TypingSession.Outcome.ROUND, outcome)
        assertTrue("unit ${s.round.unitMm}", s.round.unitMm >= 5.2f)
        while (true) {
            val c = s.expected!!
            val key = target(s, c)
            val x = key.face.centerX + 3.5f * px
            val raw = s.geometry.keyAt(x, key.face.centerY)
            if (c != ' ' && raw != null && !raw.ghost && raw !== key) {
                assertEquals(TypingSession.Outcome.HIT, s.tap(x, key.face.centerY))
                break
            }
            assertEquals(TypingSession.Outcome.HIT, s.tap(key.face.centerX, key.face.centerY))
        }
    }

    @Test
    fun tapsAboveTheKeyboardAreIgnored() {
        val s = session()
        assertEquals(TypingSession.Outcome.IGNORED, s.tap(500f, -20f))
        assertEquals(0, s.round.taps)
        assertTrue(s.round.samples.isEmpty())
    }
}
