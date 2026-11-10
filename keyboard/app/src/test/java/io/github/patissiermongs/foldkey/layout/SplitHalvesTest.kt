package io.github.patissiermongs.foldkey.layout

import io.github.patissiermongs.foldkey.engine.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitHalvesTest {
    private val px = 368f / 25.4f
    private val gapX = 0.45f

    private fun spec(
        left: Float,
        right: Float,
        sideLeft: Float = 0.5f,
        sideRight: Float = 0.5f,
        widthPx: Int = 2184,
        panel: Boolean = true,
    ) = GeometrySpec(
        widthPx = widthPx.toFloat(),
        pxPerMmX = px,
        pxPerMmY = px,
        topPx = 0f,
        rowHeightMm = 9.5f,
        gapMm = 0.9f,
        sidePaddingMm = 0.5f,
        splitMarginLeftPx = sideLeft * px,
        splitMarginRightPx = sideRight * px,
        bottomPaddingPx = 0f,
        maxUnitMm = 11.5f,
        splitUnitMm = left,
        splitUnitRightMm = right,
        ghostUnits = 1f,
        panelMinPx = if (panel) 12f * px else 0f,
    )

    private fun key(g: KeyboardGeometry, row: Int, c: Char) =
        g.keys.first { !it.ghost && it.row == row && (it.def.action as? KeyAction.Char)?.base == c }

    @Test
    fun equalHalvesDrawTheSameKeysAsTheCommonWidth() {
        val halves = KeyboardGeometry.split(Layouts.split, spec(8.5f, 8.5f), Layouts.pad)
        val common = KeyboardGeometry.split(Layouts.split, spec(8.5f, Float.NaN), Layouts.pad)
        assertEquals(common.keys.size, halves.keys.size)
        for ((a, b) in common.keys.zip(halves.keys)) {
            assertEquals(a.face.left, b.face.left, 0.01f)
            assertEquals(a.face.right, b.face.right, 0.01f)
            assertEquals(a.touch.left, b.touch.left, 0.01f)
            assertEquals(a.touch.right, b.touch.right, 0.01f)
        }
        assertEquals(common.panel.size, halves.panel.size)
        assertEquals(common.unitPx, halves.unitPx, 0.01f)
    }

    @Test
    fun eachHalfUsesItsOwnKeyWidth() {
        val g = KeyboardGeometry.split(Layouts.split, spec(8.0f, 9.2f))
        assertEquals(8.0f, g.leftUnitPx / px, 0.001f)
        assertEquals(9.2f, g.rightUnitPx / px, 0.001f)
        assertEquals(8.0f, minOf(g.leftUnitPx, g.rightUnitPx) / px, 0.001f)
        assertEquals(8.0f - 0.9f, key(g, 1, 'q').face.width / px, 0.01f)
        assertEquals(8.0f - 0.9f, key(g, 1, 't').face.width / px, 0.01f)
        assertEquals(9.2f - 0.9f, key(g, 1, 'y').face.width / px, 0.01f)
        assertEquals(9.2f - 0.9f, key(g, 1, 'o').face.width / px, 0.01f)
        assertEquals(1.25f * 9.2f - 0.9f, key(g, 1, 'p').face.width / px, 0.01f)
    }

    @Test
    fun bothOutsideEdgesStayWhereTheyAreSet() {
        val g = KeyboardGeometry.split(Layouts.split, spec(8.0f, 9.2f, sideLeft = 12f, sideRight = 15f))
        val row = g.keys.filter { !it.ghost && it.row == 0 }
        assertEquals(12f, (row.minOf { it.face.left } - gapX * px) / px, 0.01f)
        assertEquals(2184f / px - 15f, (row.maxOf { it.face.right } + gapX * px) / px, 0.01f)
    }

    @Test
    fun theCentreGapIsWhatTheHalvesLeaveOver() {
        val s = spec(8.0f, 9.2f, sideLeft = 12f, sideRight = 15f)
        val g = KeyboardGeometry.split(Layouts.split, s)
        val total = 6.75f * 8.0f + 6.75f * 9.2f
        val expected = 2184f / px - 12f - 15f - total
        val seen = (key(g, 0, '7').face.left - key(g, 0, '6').face.right) / px - 0.9f
        assertEquals(expected, seen, 0.01f)
    }

    @Test
    fun anExplicitWidthIsKeptWhereTheCommonWidthIsCutByTheScreen() {
        val cut = KeyboardGeometry.split(Layouts.split, spec(8.5f, Float.NaN, sideLeft = 14f, sideRight = 14f))
        assertEquals(7.92f, cut.unitPx / px, 0.01f)
        val kept = KeyboardGeometry.split(Layouts.split, spec(8.5f, 8.5f, sideLeft = 14f, sideRight = 14f))
        assertEquals(8.5f, kept.leftUnitPx / px, 0.001f)
        assertEquals(8.5f, kept.rightUnitPx / px, 0.001f)
    }

    @Test
    fun theCommonWidthStopsGrowingAtTheScreenCapWhateverTheSettingSays() {
        fun unit(mm: Float, left: Float, right: Float) =
            KeyboardGeometry.split(Layouts.split, spec(mm, Float.NaN, sideLeft = left, sideRight = right)).unitPx / px
        assertEquals(9.0f, unit(9.0f, 0.5f, 0.5f), 0.001f)
        assertEquals(9.66f, unit(10.0f, 0.5f, 0.5f), 0.01f)
        assertEquals(unit(10.0f, 0.5f, 0.5f), unit(11.0f, 0.5f, 0.5f), 0.0001f)
        assertEquals(7.72f, unit(8.5f, 13.7f, 17.4f), 0.01f)
        assertEquals(unit(8.5f, 13.7f, 17.4f), unit(11.0f, 13.7f, 17.4f), 0.0001f)
        assertEquals(7.92f, unit(11.0f, 14f, 14f), 0.01f)
    }

    @Test
    fun halvesThatDoNotFitAreScaledTogetherAndKeepAMinimumGap() {
        val g = KeyboardGeometry.split(Layouts.split, spec(11f, 8f, sideLeft = 14f, sideRight = 14f))
        assertEquals(11f / 8f, g.leftUnitPx / g.rightUnitPx, 0.0001f)
        assertTrue(g.leftUnitPx / px < 11f)
        val avail = 2184f - 28f * px
        val total = 6.75f * g.leftUnitPx + 6.75f * g.rightUnitPx
        assertEquals(KeyboardGeometry.MIN_CENTER_UNITS * (g.leftUnitPx + g.rightUnitPx) / 2f, avail - total, 0.5f)
    }

    @Test
    fun eachBlankBesideTheClipboardIsOneKeyOfItsOwnHalf() {
        val g = KeyboardGeometry.split(Layouts.split, spec(7.0f, 9.0f))
        assertEquals(Layouts.split.size, g.panel.size)
        for ((row, pair) in listOf('6' to '7', 't' to 'y', 'g' to 'h', 'v' to 'b').withIndex()) {
            val left = key(g, row, pair.first)
            val right = key(g, row, pair.second)
            val box = g.panel[row]
            assertEquals(box.left, left.touch.right, 0.01f)
            assertEquals(box.right, right.touch.left, 0.01f)
            assertEquals(7.0f, (box.left - (left.face.right + gapX * px)) / px, 0.05f)
            assertEquals(9.0f, ((right.face.left - gapX * px) - box.right) / px, 0.05f)
            assertEquals(left, g.keyAt(box.left - 2f, left.face.centerY))
            assertEquals(right, g.keyAt(box.right + 2f, right.face.centerY))
            assertNull(g.keyAt(box.centerX, left.face.centerY))
        }
    }

    @Test
    fun withoutAClipboardTheGhostKeysFollowTheirHalf() {
        val g = KeyboardGeometry.split(Layouts.split, spec(7.0f, 9.0f, panel = false))
        assertTrue(g.panel.isEmpty())
        val ghosts = g.keys.filter { it.ghost && it.row == 1 }
        assertEquals(2, ghosts.size)
        val nearLeft = ghosts.minByOrNull { it.face.left }!!
        val nearRight = ghosts.maxByOrNull { it.face.left }!!
        assertEquals(7.0f, nearLeft.face.width / px, 0.05f)
        assertEquals(9.0f, nearRight.face.width / px, 0.05f)
    }

    @Test
    fun theNumpadStillReplacesTheRightHalfWithHalves() {
        val g = KeyboardGeometry.split(Layouts.split, spec(8.0f, 9.2f), Layouts.pad)
        val pad = g.fnKeys.filter { it.pad }
        assertEquals(Layouts.pad.size, pad.size)
        val end = g.keys.filter { !it.ghost }.maxOf { it.face.right + gapX * px }
        assertEquals(end, pad.maxOf { it.face.right + gapX * px }, 0.5f)
    }
}
