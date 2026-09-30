package io.github.patissiermongs.foldkey.layout

import android.view.KeyEvent
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {
    private val symbols = "`-=[]\\;',./"

    private fun spec(widthPx: Int, ppi: Float, splitUnitMm: Float = 8.5f, ghost: Float = 0f) = GeometrySpec(
        widthPx = widthPx.toFloat(),
        pxPerMmX = ppi / 25.4f,
        pxPerMmY = ppi / 25.4f,
        topPx = 0f,
        rowHeightMm = 9.5f,
        gapMm = 0.9f,
        sidePaddingMm = 0.5f,
        bottomPaddingPx = 0f,
        maxUnitMm = 11.5f,
        splitUnitMm = splitUnitMm,
        ghostUnits = ghost,
    )

    private fun defsOf(rows: List<RowDef>) = rows.flatMap { it.keys }

    private fun checkCoverage(defs: List<KeyDef>, name: String) {
        val chars = defs.mapNotNull { (it.action as? KeyAction.Char)?.base }
        for (c in ('a'..'z') + ('0'..'9') + symbols.toList()) {
            assertEquals("$name: '$c'", 1, chars.count { it == c })
        }
        assertEquals("$name char keys", 26 + 10 + symbols.length, chars.size)
        val actions = defs.map { it.action }
        assertTrue(name, KeyAction.EscCtrl in actions)
        assertTrue(name, KeyAction.Enter in actions)
        assertTrue(name, KeyAction.Backspace in actions)
        assertTrue(name, KeyAction.Space in actions)
        assertTrue(name, KeyAction.Lang in actions)
        assertTrue(name, KeyAction.Code(KeyEvent.KEYCODE_TAB) in actions)
        for (m in Modifier.entries) assertTrue("$name $m", KeyAction.Mod(m) in actions)
        val reachable = actions + defs.mapNotNull { it.fn } + defs.mapNotNull { it.up } + defs.mapNotNull { it.down }
        for (code in listOf(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_FORWARD_DEL,
        ) + (KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12)) {
            assertTrue("$name keycode $code", KeyAction.Code(code) in reachable)
        }
    }

    @Test
    fun everyLayoutReachesTheSameKeySet() {
        checkCoverage(defsOf(Layouts.full), "full")
        checkCoverage(Layouts.split.flatMap { it.left.keys + it.right.keys }, "split")
        checkCoverage(defsOf(Layouts.compact), "compact")
    }

    @Test
    fun rowWidthsAreConsistent() {
        Layouts.full.forEach { assertEquals(15f, it.units, 1e-4f) }
        Layouts.compact.forEach { assertEquals(10f, it.units, 1e-4f) }
        assertTrue(Layouts.split.all { it.left.units <= 6.75f && it.right.units <= 7.5f })
    }

    @Test
    fun splitPutsEveryDubeolsikVowelOnTheRightHalf() {
        val left = Layouts.split.flatMap { it.left.keys }.mapNotNull { (it.action as? KeyAction.Char)?.base }.filter { it in 'a'..'z' }
        val right = Layouts.split.flatMap { it.right.keys }.mapNotNull { (it.action as? KeyAction.Char)?.base }.filter { it in 'a'..'z' }
        assertEquals("qwertasdfgzxcv".toSet(), left.toSet())
        assertEquals("yuiophjklbnm".toSet(), right.toSet())
        for (c in left) assertTrue(io.github.patissiermongs.foldkey.hangul.Jamo.isConsonant(io.github.patissiermongs.foldkey.hangul.Dubeolsik.jamo(c, false)!!))
        for (c in right) assertTrue(io.github.patissiermongs.foldkey.hangul.Jamo.isVowel(io.github.patissiermongs.foldkey.hangul.Dubeolsik.jamo(c, false)!!))
    }

    @Test
    fun fold7InnerPortraitFullLayoutPitch() {
        val g = KeyboardGeometry.full(Layouts.full, spec(1968, 368f))
        val mm = g.unitPx / (368f / 25.4f)
        assertEquals(8.99f, mm, 0.02f)
    }

    @Test
    fun fold7InnerLandscapeFullLayoutPitch() {
        val g = KeyboardGeometry.full(Layouts.full, spec(2184, 368f))
        assertEquals(9.98f, g.unitPx / (368f / 25.4f), 0.02f)
    }

    @Test
    fun fold7SplitHalvesStayNearThumbReach() {
        val pxPerMm = 368f / 25.4f
        for (width in listOf(1968, 2184)) {
            val g = KeyboardGeometry.split(Layouts.split, spec(width, 368f, ghost = 1f))
            val unitMm = g.unitPx / pxPerMm
            assertTrue("unit $unitMm", unitMm >= 8.2f && unitMm <= 8.5f + 1e-3f)
            val visible = g.keys.filter { !it.ghost }
            val leftHalf = visible.filter { it.face.centerX < width / 2f }
            val rightHalf = visible.filter { it.face.centerX >= width / 2f }
            val leftSpan = leftHalf.maxOf { it.face.right } / pxPerMm
            val rightSpan = (width - rightHalf.minOf { it.face.left }) / pxPerMm
            assertTrue("left $leftSpan", leftSpan <= 58.5f)
            assertTrue("right $rightSpan", rightSpan <= 64.5f)
        }
    }

    @Test
    fun coverScreenUsesTenColumnPitch() {
        val g = KeyboardGeometry.full(Layouts.compact, spec(1080, 422f))
        assertEquals(6.40f, g.unitPx / (422f / 25.4f), 0.02f)
    }

    @Test
    fun touchAreasTileEachRowWithoutOverlapOrHoles() {
        val layouts = listOf(
            KeyboardGeometry.full(Layouts.full, spec(1968, 368f)),
            KeyboardGeometry.full(Layouts.compact, spec(1080, 422f)),
        )
        for (g in layouts) {
            var y = 1f
            while (y < g.heightPx - 1f) {
                var x = 0.5f
                while (x < g.keys.maxOf { it.touch.right } - 0.5f) {
                    val hits = g.keys.count { it.touch.contains(x, y) }
                    assertEquals("($x,$y)", 1, hits)
                    x += 7f
                }
                y += 11f
            }
        }
    }

    @Test
    fun splitGhostKeysDuplicateTheKeysAcrossTheGap() {
        val g = KeyboardGeometry.split(Layouts.split, spec(2184, 368f, ghost = 1f))
        val ghosts = g.keys.filter { it.ghost }.mapNotNull { (it.def.action as? KeyAction.Char)?.base }.toSet()
        assertEquals("56tygvhb".toSet(), ghosts)
        val t = g.keys.first { !it.ghost && (it.def.action as? KeyAction.Char)?.base == 't' }
        val ghostY = g.keys.first { it.ghost && (it.def.action as? KeyAction.Char)?.base == 'y' }
        assertTrue(ghostY.touch.left >= t.touch.right - 1f)
        assertNotNull(g.keyAt(ghostY.touch.centerX, ghostY.touch.centerY))
        val middle = (g.keys.filter { !it.ghost }.filter { it.face.centerX < 1092f }.maxOf { it.face.right } +
            g.keys.filter { !it.ghost }.filter { it.face.centerX >= 1092f }.minOf { it.face.left }) / 2f
        assertEquals(null, g.keyAt(middle, ghostY.touch.centerY))
    }
}
