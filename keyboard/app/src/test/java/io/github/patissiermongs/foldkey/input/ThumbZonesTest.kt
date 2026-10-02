package io.github.patissiermongs.foldkey.input

import io.github.patissiermongs.foldkey.input.ThumbZones.Sample
import io.github.patissiermongs.foldkey.input.ThumbZones.Side
import io.github.patissiermongs.foldkey.input.ThumbZones.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbZonesTest {
    private fun scribble(x0: Float, x1: Float, y0: Float, y1: Float, side: Side = Side.LEFT, widthMm: Float = 150f): List<Sample> {
        val points = ArrayList<Sample>()
        var y = y0
        var forward = true
        while (y <= y1 + 1e-3f) {
            val (a, b) = if (forward) x0 to x1 else x1 to x0
            for (d in listOf(a, b)) points.add(Sample(if (side == Side.LEFT) d else widthMm - d, y))
            forward = !forward
            y += 0.25f
        }
        return points
    }

    @Test
    fun scribbleGivesTheEdgesOfTheArea() {
        val zone = ThumbZones.strokeZone(scribble(14f, 48f, 5f, 50f), Side.LEFT, 150f)!!
        assertEquals(14f, zone.nearMm, 0.5f)
        assertEquals(48f, zone.farMm, 0.5f)
        assertEquals(5f, zone.bottomMm, 0.5f)
        assertEquals(50f, zone.topMm, 0.5f)
    }

    @Test
    fun rightThumbIsMeasuredFromTheRightEdge() {
        val zone = ThumbZones.strokeZone(scribble(17f, 52f, 8f, 46f, Side.RIGHT), Side.RIGHT, 150f)!!
        assertEquals(17f, zone.nearMm, 0.5f)
        assertEquals(52f, zone.farMm, 0.5f)
    }

    @Test
    fun aSingleExcursionDoesNotMoveTheEdges() {
        val points = scribble(14f, 48f, 5f, 50f).toMutableList()
        points.addAll(listOf(Sample(48f, 30f), Sample(2f, 30f), Sample(48f, 30.2f)))
        val zone = ThumbZones.strokeZone(points, Side.LEFT, 150f)!!
        assertEquals(14f, zone.nearMm, 0.5f)
    }

    @Test
    fun linesAndTapsAreNotAnArea() {
        assertNull(ThumbZones.strokeZone(emptyList(), Side.LEFT, 150f))
        assertNull(ThumbZones.strokeZone(listOf(Sample(20f, 20f)), Side.LEFT, 150f))
        assertNull(ThumbZones.strokeZone(listOf(Sample(10f, 20f), Sample(60f, 20f)), Side.LEFT, 150f))
        assertNull(ThumbZones.strokeZone(scribble(20f, 25f, 5f, 50f), Side.LEFT, 150f))
        assertNull(ThumbZones.strokeZone(scribble(10f, 50f, 20f, 30f), Side.LEFT, 150f))
    }

    @Test
    fun threeStrokesMustAgreeOnEveryEdge() {
        val ok = ThumbZones.summarize(listOf(Zone(13f, 47f, 5f, 50f), Zone(14f, 48f, 6f, 52f), Zone(15f, 46f, 4f, 49f)))!!
        assertEquals(Zone(14f, 47f, 5f, 50f), ok.zone)
        assertTrue(ok.consistent)
        val bad = ThumbZones.summarize(listOf(Zone(13f, 47f, 5f, 50f), Zone(14f, 48f, 6f, 52f), Zone(14f, 48f, 6f, 63f)))!!
        assertEquals(13f, bad.spreadMm, 0.001f)
        assertFalse(bad.consistent)
        assertNull(ThumbZones.summarize(emptyList()))
    }

    @Test
    fun fitPutsBothLayersInsideTheDrawnAreas() {
        val left = Zone(13.7f, 47.5f, 4f, 54f)
        val right = Zone(17.4f, 52.2f, 6f, 50f)
        val fit = ThumbZones.fit(left, right, 5, 7.25f to 8f, 5.5f to 5f)
        assertEquals(13.7f, fit.leftMm, 1e-4f)
        assertEquals(17.4f, fit.rightMm, 1e-4f)
        assertEquals(4.3f, fit.codeUnitMm, 1e-4f)
        assertEquals(6.1f, fit.generalUnitMm, 1e-4f)
        assertEquals(6f, fit.liftMm, 1e-4f)
        assertEquals(8.8f, fit.rowHeightMm, 1e-4f)
        assertTrue(fit.leftMm + 7.25f * fit.codeUnitMm <= left.farMm)
        assertTrue(fit.rightMm + 8f * fit.codeUnitMm <= right.farMm)
        assertTrue(fit.leftMm + 5.5f * fit.generalUnitMm <= left.farMm)
        assertTrue(fit.liftMm + 5 * fit.rowHeightMm <= 50f)
    }

    @Test
    fun fitKeepsTechnicalBounds() {
        val narrow = ThumbZones.fit(Zone(10f, 20f, 0f, 16f), Zone(10f, 20f, 0f, 16f), 5, 7.25f to 8f, 5.5f to 5f)
        assertEquals(ThumbZones.UNIT_MIN_MM, narrow.codeUnitMm, 1e-4f)
        assertEquals(ThumbZones.ROW_MIN_MM, narrow.rowHeightMm, 1e-4f)
        val wide = ThumbZones.fit(Zone(60f, 200f, 50f, 200f), Zone(1f, 200f, 0f, 190f), 5, 7.25f to 8f, 5.5f to 5f)
        assertEquals(ThumbZones.UNIT_MAX_MM, wide.generalUnitMm, 1e-4f)
        assertEquals(ThumbZones.ROW_MAX_MM, wide.rowHeightMm, 1e-4f)
        assertEquals(ThumbZones.LIFT_MAX_MM, wide.liftMm, 1e-4f)
        assertEquals(ThumbZones.SIDE_MAX_MM, wide.leftMm, 1e-4f)
        assertNotNull(wide)
    }
}
