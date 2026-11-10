package io.github.patissiermongs.foldkey.ui

import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.layout.GeometrySpec
import io.github.patissiermongs.foldkey.layout.KeyboardGeometry
import io.github.patissiermongs.foldkey.layout.Layouts
import io.github.patissiermongs.foldkey.layout.SplitRow
import io.github.patissiermongs.foldkey.ui.SplitEditModel.Handle
import io.github.patissiermongs.foldkey.ui.SplitEditModel.Sizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitEditModelTest {
    private val px = 368f / 25.4f
    private val widthMm = 2184f / px
    private val code = SplitEditModel(Layouts.split, widthMm, 0.5f)
    private val general = SplitEditModel(Layouts.generalSplit, widthMm, 0.5f)
    private val start = Sizes(0.5f, 8.5f, 8.5f, 0.5f)

    private fun key(g: KeyboardGeometry, row: Int, c: Char) =
        g.keys.first { !it.ghost && it.row == row && (it.def.action as? KeyAction.Char)?.base == c }

    private fun geometry(layer: List<SplitRow>, s: Sizes) = KeyboardGeometry.split(
        layer,
        GeometrySpec(
            widthPx = 2184f,
            pxPerMmX = px,
            pxPerMmY = px,
            topPx = 0f,
            rowHeightMm = 9.5f,
            gapMm = 0.9f,
            sidePaddingMm = 0.5f,
            splitMarginLeftPx = s.leftSideMm * px,
            splitMarginRightPx = s.rightSideMm * px,
            bottomPaddingPx = 0f,
            maxUnitMm = 11.5f,
            splitUnitMm = s.leftUnitMm,
            splitUnitRightMm = s.rightUnitMm,
            ghostUnits = 1f,
            panelMinPx = 12f * px,
        ),
    )

    @Test
    fun theCodeHandlesSitOnTheDigitRow() {
        assertEquals(0, code.handleRow(start))
        assertEquals(0.5f, code.handleMm(start, Handle.LEFT_OUTER, 0), 0.001f)
        assertEquals(0.5f + 6.75f * 8.5f, code.handleMm(start, Handle.LEFT_INNER, 0), 0.001f)
        assertEquals(widthMm - 0.5f - 6.75f * 8.5f, code.handleMm(start, Handle.RIGHT_INNER, 0), 0.001f)
        assertEquals(widthMm - 0.5f, code.handleMm(start, Handle.RIGHT_OUTER, 0), 0.001f)
    }

    @Test
    fun theTextLayerHandlesSitOnItsWidestRow() {
        assertEquals(1, general.handleRow(start))
        assertEquals(0.5f + 5.5f * 8.5f, general.handleMm(start, Handle.LEFT_INNER, 1), 0.001f)
        assertEquals(widthMm - 0.5f - 5f * 8.5f, general.handleMm(start, Handle.RIGHT_INNER, 1), 0.001f)
    }

    @Test
    fun theHandlesSitOnTheEdgesOfTheDrawnKeys() {
        val s = Sizes(12f, 8.0f, 9.2f, 15f)
        val g = geometry(Layouts.split, s)
        val gapX = 0.45f
        val first = key(g, 0, '1')
        val lastLeft = key(g, 0, '6')
        val firstRight = key(g, 0, '7')
        val lastRight = g.keys.filter { !it.ghost && it.row == 0 }.maxByOrNull { it.face.right }!!
        assertEquals(code.handleMm(s, Handle.LEFT_INNER, 0), lastLeft.face.right / px + gapX, 0.01f)
        assertEquals(code.handleMm(s, Handle.RIGHT_INNER, 0), firstRight.face.left / px - gapX, 0.01f)
        assertEquals(code.handleMm(s, Handle.RIGHT_OUTER, 0), lastRight.face.right / px + gapX, 0.01f)
        assertEquals(12f + 0.75f * 8.0f, first.face.left / px - gapX, 0.01f)
        val gg = geometry(Layouts.generalSplit, s)
        val q = key(gg, 1, 'p')
        assertEquals(general.handleMm(s, Handle.RIGHT_OUTER, 1), q.face.right / px + gapX, 0.01f)
        assertEquals(general.handleMm(s, Handle.LEFT_INNER, 1), key(gg, 1, 't').face.right / px + gapX, 0.01f)
    }

    @Test
    fun draggingAHandleChangesOnlyItsOwnValue() {
        val targets = mapOf(
            Handle.LEFT_OUTER to 20f,
            Handle.LEFT_INNER to 70f,
            Handle.RIGHT_INNER to 100f,
            Handle.RIGHT_OUTER to widthMm - 18f,
        )
        for ((handle, x) in targets) {
            val next = code.drag(start, handle, x, 0)
            for (other in Handle.entries - handle) assertEquals("$handle moved $other", code.value(start, other), code.value(next, other), 0.0001f)
            assertTrue("$handle", code.value(next, handle) != code.value(start, handle))
        }
    }

    @Test
    fun anInnerHandleFollowsTheFingerToTheNearestTenthOfAMillimetre() {
        val left = code.drag(start, Handle.LEFT_INNER, 62f, 0)
        assertEquals(62f, code.handleMm(left, Handle.LEFT_INNER, 0), 0.05f * 6.75f)
        assertEquals((62f - 0.5f) / 6.75f, left.leftUnitMm, 0.05f)
        val right = code.drag(start, Handle.RIGHT_INNER, 95f, 0)
        assertEquals(95f, code.handleMm(right, Handle.RIGHT_INNER, 0), 0.05f * 6.75f)
        assertEquals((widthMm - 0.5f - 95f) / 6.75f, right.rightUnitMm, 0.05f)
        assertEquals(8.5f, left.rightUnitMm, 0f)
        assertEquals(8.5f, right.leftUnitMm, 0f)
    }

    @Test
    fun anInnerHandleUsesTheSpansOfTheTextLayerRow() {
        val left = general.drag(start, Handle.LEFT_INNER, 50f, 1)
        assertEquals(9.0f, left.leftUnitMm, 0.0001f)
        assertEquals(50f, general.handleMm(left, Handle.LEFT_INNER, 1), 0.01f)
        val right = general.drag(start, Handle.RIGHT_INNER, 100f, 1)
        assertEquals(10.0f, right.rightUnitMm, 0.0001f)
        assertEquals(100f, general.handleMm(right, Handle.RIGHT_INNER, 1), 0.3f)
    }

    @Test
    fun valuesStayInRangeAndTheCentreKeepsItsMinimumGap() {
        assertEquals(11.0f, code.drag(start, Handle.LEFT_INNER, 140f, 0).leftUnitMm, 0f)
        val wide = code.drag(start.copy(rightUnitMm = 11.0f), Handle.LEFT_INNER, 140f, 0)
        assertTrue(code.fits(wide))
        assertTrue(wide.leftUnitMm < 11f && wide.leftUnitMm > 8.5f)
        assertTrue(code.gapMm(wide) >= code.minGapMm(wide) - 0.001f)
        assertTrue(code.gapMm(wide) < code.minGapMm(wide) + 1f)
        val narrow = code.drag(start, Handle.LEFT_INNER, 10f, 0)
        assertEquals(4.0f, narrow.leftUnitMm, 0f)
        assertEquals(0f, code.drag(start, Handle.LEFT_OUTER, -5f, 0).leftSideMm, 0f)
        assertEquals(0f, code.drag(start, Handle.RIGHT_OUTER, widthMm + 5f, 0).rightSideMm, 0f)
        val far = code.drag(start, Handle.LEFT_OUTER, 120f, 0)
        assertTrue(code.fits(far))
        assertTrue(far.leftSideMm <= 40f)
    }

    @Test
    fun nudgeMovesTheSelectedValueByATenthAndStopsWhereTheLayoutFills() {
        assertEquals(8.6f, code.nudge(start, Handle.LEFT_INNER, 0.1f).leftUnitMm, 0.0001f)
        assertEquals(8.4f, code.nudge(start, Handle.RIGHT_INNER, -0.1f).rightUnitMm, 0.0001f)
        assertEquals(1.5f, code.nudge(start, Handle.RIGHT_OUTER, 1.0f).rightSideMm, 0.0001f)
        var s = start
        repeat(40) { s = code.nudge(s, Handle.LEFT_INNER, 0.1f) }
        assertTrue(code.fits(s))
        val full = code.nudge(s, Handle.LEFT_INNER, 0.1f)
        assertEquals(s.leftUnitMm, full.leftUnitMm, 0.0001f)
    }

    @Test
    fun aStartThatDoesNotFitIsLeftAlone() {
        val tooBig = Sizes(80f, 8.5f, 8.5f, 80f)
        assertTrue(!code.fits(tooBig))
        assertEquals(tooBig, code.drag(tooBig, Handle.LEFT_INNER, 90f, 0))
    }

    @Test
    fun theEditedSizesAreExactlyWhatTheGeometryDraws() {
        var s = start
        s = code.drag(s, Handle.LEFT_OUTER, 14f, 0)
        s = code.drag(s, Handle.LEFT_INNER, code.handleMm(s, Handle.LEFT_INNER, 0) + 5f, 0)
        s = code.drag(s, Handle.RIGHT_INNER, code.handleMm(s, Handle.RIGHT_INNER, 0) - 6f, 0)
        val g = geometry(Layouts.split, s)
        assertEquals(s.leftUnitMm, g.leftUnitPx / px, 0.001f)
        assertEquals(s.rightUnitMm, g.rightUnitPx / px, 0.001f)
        assertEquals(code.gapMm(s), (key(g, 0, '7').face.left - key(g, 0, '6').face.right) / px - 0.9f, 0.01f)
    }
}
