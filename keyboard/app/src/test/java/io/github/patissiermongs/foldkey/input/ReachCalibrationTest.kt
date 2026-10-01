package io.github.patissiermongs.foldkey.input

import io.github.patissiermongs.foldkey.input.ReachCalibration.Bands
import io.github.patissiermongs.foldkey.input.ReachCalibration.Sample
import io.github.patissiermongs.foldkey.input.ReachCalibration.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReachCalibrationTest {
    private val bands = Bands(rows = 5, rowHeightMm = 9.5f, liftMm = 0f)

    private fun arc(side: Side, reachForRow: (Int) -> Float, rows: IntRange = 0..4): List<Sample> =
        rows.flatMap { r ->
            val y = (4 - r) * 9.5f + 4.75f
            val reach = reachForRow(r)
            listOf(reach - 3f to y - 2f, reach to y, reach - 1f to y + 2f).map { (d, yy) ->
                Sample(if (side == Side.LEFT) d else 150f - d, yy)
            }
        }

    @Test
    fun rowsAreCountedFromTheTopAboveTheLift() {
        val lifted = Bands(rows = 5, rowHeightMm = 10f, liftMm = 5f)
        assertNull(lifted.row(4f))
        assertEquals(4, lifted.row(6f))
        assertEquals(0, lifted.row(54f))
        assertNull(lifted.row(56f))
    }

    @Test
    fun reachIsTheSmallestRowMaximumAndNeedsEveryRow() {
        val samples = arc(Side.LEFT, { r -> 70f - r * 2f })
        assertEquals(62f, ReachCalibration.strokeReach(samples, Side.LEFT, 150f, bands)!!, 0.001f)
        assertNull(ReachCalibration.strokeReach(arc(Side.LEFT, { 60f }, 1..4), Side.LEFT, 150f, bands))
    }

    @Test
    fun rightThumbReachIsMeasuredFromTheRightEdge() {
        val samples = arc(Side.RIGHT, { 64f })
        assertEquals(64f, ReachCalibration.strokeReach(samples, Side.RIGHT, 150f, bands)!!, 0.001f)
        assertEquals(86f, samples[1].xMm, 0.001f)
    }

    @Test
    fun threeStrokesMustAgree() {
        val ok = ReachCalibration.summarize(listOf(60f, 63f, 61f))!!
        assertEquals(61f, ok.medianMm, 0.001f)
        assertTrue(ok.consistent)
        val bad = ReachCalibration.summarize(listOf(52f, 61f, 63f))!!
        assertEquals(11f, bad.spreadMm, 0.001f)
        assertFalse(bad.consistent)
        assertNull(ReachCalibration.summarize(emptyList()))
    }

    @Test
    fun keyWidthFitsBothHalvesAndIsClamped() {
        assertEquals(8.5f, ReachCalibration.unitMm(57.9f, 64.3f, 0.5f, 6.75f, 7.5f, 7f, 11f), 0.001f)
        assertEquals(9.0f, ReachCalibration.unitMm(70f, 68.1f, 0.5f, 6.75f, 7.5f, 7f, 11f), 0.001f)
        assertEquals(7f, ReachCalibration.unitMm(30f, 30f, 0.5f, 6.75f, 7.5f, 7f, 11f), 0.001f)
        assertEquals(11f, ReachCalibration.unitMm(120f, 120f, 0.5f, 6.75f, 7.5f, 7f, 11f), 0.001f)
    }
}
