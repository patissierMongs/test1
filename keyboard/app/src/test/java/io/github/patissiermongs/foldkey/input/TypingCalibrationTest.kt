package io.github.patissiermongs.foldkey.input

import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.input.ThumbZones.Side
import io.github.patissiermongs.foldkey.input.ThumbZones.Zone
import io.github.patissiermongs.foldkey.input.TypingCalibration.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class TypingCalibrationTest {
    private fun taps(side: Side, n: Int, ox: Float, oy: Float, sx: Float, sy: Float, seed: Long): List<Sample> {
        val random = Random(seed)
        val zones = if (side == Side.LEFT) listOf(0, 1, 4, 5, 8, 9, 12, 13) else listOf(2, 3, 6, 7, 10, 11, 14, 15)
        return (0 until n).map {
            Sample(side, zones[it % zones.size], ox + sx * random.nextGaussian().toFloat(), oy + sy * random.nextGaussian().toFloat())
        }
    }

    @Test
    fun spreadAfterOffsetsGivesTheKeySizeForNinetyFivePercent() {
        val samples = taps(Side.LEFT, 400, 0.8f, 1.2f, 1.5f, 2.0f, 1) + taps(Side.RIGHT, 400, -0.5f, 0.6f, 1.0f, 1.2f, 2)
        val a = TypingCalibration.analyze(samples)!!
        assertEquals(1.5f, a.spread.getValue(Side.LEFT).xMm, 0.12f)
        assertEquals(2.0f, a.spread.getValue(Side.LEFT).yMm, 0.15f)
        assertEquals(1.0f, a.spread.getValue(Side.RIGHT).xMm, 0.1f)
        assertEquals(0.8f, a.sideOffsets.getValue(Side.LEFT).first, 0.2f)
        assertEquals(0.6f, a.sideOffsets.getValue(Side.RIGHT).second, 0.15f)
        assertEquals(0.8f, a.offset(4, Side.LEFT).first, 0.4f)
        assertEquals(2f * 1.96f * 1.5f, a.pitchMm, 0.4f)
        assertEquals(2f * 1.96f * 2.0f, a.rowMm, 0.6f)
        val inside = TypingCalibration.hitRate(samples.filter { it.side == Side.LEFT }, a, 2f * 1.96f * 1.5f, 100f)
        assertEquals(0.95f, inside, 0.03f)
    }

    @Test
    fun spreadIsNotUnderestimatedWhenEachZoneHasFewTaps() {
        val estimates = (0 until 300).map { seed ->
            TypingCalibration.analyze(taps(Side.LEFT, 40, 0.6f, 1.0f, 1.4f, 1.6f, 100L + seed))!!.spread.getValue(Side.LEFT)
        }
        assertEquals(1.4f, estimates.map { it.xMm }.average().toFloat(), 0.04f)
        assertEquals(1.6f, estimates.map { it.yMm }.average().toFloat(), 0.045f)
    }

    @Test
    fun grossSlipsAreDroppedBeforeTheSpreadIsTaken() {
        val clean = taps(Side.LEFT, 200, 0f, 0f, 1.2f, 1.2f, 3) + taps(Side.RIGHT, 200, 0f, 0f, 1.2f, 1.2f, 4)
        val slips = List(6) { Sample(Side.LEFT, 1, 18f, 0f) }
        val a = TypingCalibration.analyze(clean + slips)!!
        assertEquals(6, a.dropped)
        assertEquals(1.2f, a.spread.getValue(Side.LEFT).xMm, 0.15f)
    }

    @Test
    fun aThumbWithTooFewTapsIsLeftOut() {
        val a = TypingCalibration.analyze(taps(Side.LEFT, 40, 0f, 0f, 1f, 1f, 5) + taps(Side.RIGHT, 5, 0f, 0f, 3f, 3f, 6))!!
        assertTrue(Side.RIGHT !in a.spread)
        assertNull(TypingCalibration.analyze(taps(Side.LEFT, 5, 0f, 0f, 1f, 1f, 7)))
    }

    @Test
    fun keysOnlyGrowAndStayInRange() {
        assertEquals(5.9f, TypingCalibration.grow(4.3f, 5.88f, 4f, 11f), 1e-4f)
        assertEquals(6.0f, TypingCalibration.grow(6.0f, 5.88f, 4f, 11f), 1e-4f)
        assertEquals(11f, TypingCalibration.grow(4.3f, 12.4f, 4f, 11f), 1e-4f)
    }

    private val frame = TypingCalibration.Frame(150f, 75f, 0.5f, 1f)
    private val anchors = TypingCalibration.anchors(Zone(13.7f, 47.5f, 6.2f, 50.3f), Zone(17.4f, 52.2f, 6.2f, 50.3f))

    @Test
    fun halvesAreCentredOnTheThumbAreas() {
        assertEquals(30.6f, anchors.leftCenterMm, 1e-3f)
        assertEquals(34.8f, anchors.rightCenterMm, 1e-3f)
        val p = TypingCalibration.place(anchors, frame, Layer.CODE, 6f, 9f)
        assertEquals(30.6f - 7.25f * 3f, p.leftMm, 0.051f)
        assertEquals(34.8f - 8f * 3f, p.rightMm, 0.051f)
        assertEquals(28.25f - 5 * 4.5f, p.liftMm, 0.051f)
        val g = TypingCalibration.place(anchors, frame, Layer.GENERAL, 7f, 9f)
        assertEquals(30.6f - 5.5f * 3.5f, g.leftMm, 0.051f)
        assertEquals(34.8f - 5f * 3.5f, g.rightMm, 0.051f)
    }

    @Test
    fun halvesMoveOutwardBeforeKeysWouldShrink() {
        val inward = TypingCalibration.Anchors(45f, 45f, 28f)
        val p = TypingCalibration.place(inward, frame, Layer.CODE, 8f, 9f)
        val used = p.leftMm + p.rightMm + (14.5f + 2f) * 8f
        assertEquals(150f, used, 0.15f)
        assertTrue(p.leftMm < 45f - 7.25f * 4f)
        assertTrue(p.rightMm < 45f - 8f * 4f)
        val wide = TypingCalibration.place(anchors, frame, Layer.CODE, 10f, 9f)
        assertEquals(0.5f, wide.leftMm, 1e-4f)
        assertEquals(0.5f, wide.rightMm, 1e-4f)
    }

    @Test
    fun rowsStayUnderTheHeightCap() {
        val high = TypingCalibration.Anchors(30f, 30f, 60f)
        val p = TypingCalibration.place(high, frame, Layer.CODE, 6f, 14f)
        assertEquals(2f * (75f - 60f) / 5f, p.rowMm, 0.01f)
        assertTrue(p.liftMm + 5 * p.rowMm <= 75.05f)
    }
}
