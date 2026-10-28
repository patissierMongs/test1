package io.github.patissiermongs.foldkey.input

import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.layout.GeometrySpec
import io.github.patissiermongs.foldkey.layout.KeyboardGeometry
import io.github.patissiermongs.foldkey.layout.Layouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.sqrt

class PositionAdviceTest {
    private val leftKeys = Layouts.split.flatMap { it.left.keys }.mapNotNull { (it.action as? KeyAction.Char)?.base }.toSet()

    private fun heat(perKey: Int, left: Pair<Float, Float>, right: Pair<Float, Float>, spread: Float, seed: Long): TapHeat {
        val random = Random(seed)
        val heat = TapHeat()
        for (c in "asdfgqwert") repeat(perKey) {
            heat.add(c, left.first + spread * random.nextGaussian().toFloat(), left.second + spread * random.nextGaussian().toFloat(), 9f, 9f)
        }
        for (c in "hjkluiop") repeat(perKey) {
            heat.add(c, right.first + spread * random.nextGaussian().toFloat(), right.second + spread * random.nextGaussian().toFloat(), 9f, 9f)
        }
        return heat
    }

    @Test
    fun eachHalfMovesTowardItsThumbAndBothMoveTowardTheMeanRow() {
        val a = PositionAdvice.advise(heat(40, 0.8f to 0.7f, -0.5f to 0.5f, 1.3f, 1), leftKeys)
        assertEquals(400, a.left!!.taps)
        assertEquals(320, a.right!!.taps)
        assertEquals(0.8f, a.leftMm, 0.15f)
        assertEquals(-0.5f, a.rightMm, 0.15f)
        assertEquals(-0.6f, a.raiseMm, 0.15f)
        assertEquals(a.leftMm * 10f, Math.round(a.leftMm * 10f).toFloat(), 1e-4f)
        assertTrue(a.moves)
    }

    @Test
    fun smallOrUncertainOffsetsAndTooFewTapsMoveNothing() {
        val small = PositionAdvice.advise(heat(40, 0.15f to -0.2f, 0.2f to 0.1f, 0.5f, 2), leftKeys)
        assertTrue(small.right!!.xMm in 0.1f..0.29f)
        assertEquals(0f, small.leftMm, 0f)
        assertEquals(0f, small.rightMm, 0f)
        assertEquals(0f, small.raiseMm, 0f)
        assertFalse(small.moves)
        val few = PositionAdvice.advise(heat(10, 1.5f to 1.5f, -1.5f to 1.5f, 0.5f, 3), leftKeys)
        assertTrue(few.left!!.taps < PositionAdvice.MIN_TAPS)
        assertEquals(0f, few.leftMm, 0f)
        assertEquals(0f, few.rightMm, 0f)
        assertEquals(-1.5f, few.raiseMm, 0.1f)
    }

    private fun spread(sd: Float): TapHeat {
        val heat = TapHeat()
        for (c in "asdfgqwert") repeat(16) { i -> heat.add(c, 0.6f + if (i % 2 == 0) sd else -sd, 0f, 9f, 9f) }
        for (c in "hjkluiop") repeat(20) { heat.add(c, 0f, 0f, 9f, 9f) }
        return heat
    }

    @Test
    fun theSameMeanMovesOnlyWhenTheScatterLeavesItClear() {
        val noisy = PositionAdvice.advise(spread(4f), leftKeys)
        assertEquals(0.6f, noisy.left!!.xMm, 1e-4f)
        assertEquals(4f / sqrt(160f), noisy.left!!.seXMm, 1e-3f)
        assertEquals(0f, noisy.leftMm, 0f)
        val steady = PositionAdvice.advise(spread(1f), leftKeys)
        assertEquals(0.6f, steady.leftMm, 1e-4f)
        assertEquals(0f, steady.rightMm, 0f)
        assertEquals(0f, steady.raiseMm, 0f)
    }

    @Test
    fun suggestionsAgreeAcrossSeedsWhenTheOffsetIsReal() {
        val moves = (1L..40L).map { PositionAdvice.advise(heat(20, 0.6f to 0f, 0f to 0f, 1.5f, 100 + it), leftKeys).leftMm }
        assertTrue("$moves", moves.count { it in 0.4f..0.8f } >= 36)
    }

    private val px = 368f / 25.4f

    private fun geometry(leftMm: Float, rightMm: Float) = KeyboardGeometry.split(
        Layouts.split,
        GeometrySpec(
            widthPx = 2184f, pxPerMmX = px, pxPerMmY = px, topPx = 0f, rowHeightMm = 9f, gapMm = 0.9f, sidePaddingMm = 0.5f,
            splitMarginLeftPx = leftMm * px, splitMarginRightPx = rightMm * px, bottomPaddingPx = 0f, maxUnitMm = 11.5f,
            splitUnitMm = 7f, ghostUnits = 1f,
        ),
        Layouts.pad,
    )

    @Test
    fun movedHalvesTakeTheirLearnedOffsetsAlong() {
        val before = geometry(10f, 12f)
        val after = geometry(10.8f, 12.5f)
        val model = OffsetModel(before.zoneCount)
        for (z in 0 until model.zones) model.seed(z, 0.8f, 0.6f, 20)
        model.seed(3, 0f, 0f, 0)
        PositionAdvice.shiftOffsets(model, before, after, px, px, raiseMm = -0.6f)
        val leftDefs = Layouts.split.flatMap { it.left.keys }
        val zones = before.keys.filter { !it.ghost }.groupBy { it.zone }
            .mapValues { (_, keys) -> keys.map { k -> leftDefs.any { it === k.def } }.distinct() }
        assertTrue(zones.getValue(16) == listOf(true) && zones.getValue(19) == listOf(false))
        for ((zone, sides) in zones) {
            if (zone == 3) continue
            val (dx, dy) = model.meanMm(zone)
            when (sides) {
                listOf(true) -> assertEquals("zone $zone", 0f, dx, 0.02f)
                listOf(false) -> assertEquals("zone $zone", 1.3f, dx, 0.02f)
            }
            assertEquals("zone $zone", 0f, dy, 1e-4f)
        }
        assertEquals(0, model.samples(3))
        assertEquals(0f to 0f, model.meanMm(3))
    }
}
