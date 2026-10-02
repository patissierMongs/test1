package io.github.patissiermongs.foldkey.layout

import android.view.KeyEvent
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlin.math.abs
import kotlin.math.roundToInt
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

    private fun checkCoverage(defs: List<KeyDef>, name: String, arrows: Boolean = true) {
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
        val reachable = actions + (defs + Layouts.pad.flatten()).flatMap { listOfNotNull(it.up, it.down) }
        val navigation = listOf(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
        )
        for (code in listOf(KeyEvent.KEYCODE_FORWARD_DEL) + (KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12)) {
            assertTrue("$name keycode $code", KeyAction.Code(code) in reachable)
        }
        for (code in navigation) assertEquals("$name keycode $code", arrows, KeyAction.Code(code) in reachable)
    }

    private val splitDefs = Layouts.split.flatMap { it.left.keys + it.right.keys }

    private fun base(k: Key): Char? = if (k.ghost) null else (k.def.action as? KeyAction.Char)?.base

    private fun slotLeft(k: Key, pxPerMm: Float) = k.face.left - 0.45f * pxPerMm

    private fun slotRight(k: Key, pxPerMm: Float) = k.face.right + 0.45f * pxPerMm

    @Test
    fun everyLayoutReachesTheSameKeySet() {
        checkCoverage(defsOf(Layouts.full), "full")
        checkCoverage(splitDefs, "split")
        checkCoverage(defsOf(Layouts.compact), "compact", arrows = false)
    }

    @Test
    fun fnLayerHasNoSymbolsLeftOnTheLetterKeys() {
        for ((name, defs) in listOf("full" to defsOf(Layouts.full), "split" to splitDefs, "compact" to defsOf(Layouts.compact))) {
            assertTrue(name, defs.none { it.action is KeyAction.Text })
            assertEquals(name, listOf(KeyAction.EscCtrl), defs.filter { it.fnLabel != null }.map { it.action })
        }
    }

    @Test
    fun numpadIsSevenEightNineOnTopWithOperatorsAndEnter() {
        val labels = Layouts.pad.map { row -> row.joinToString("") { it.label ?: "" } }
        assertEquals(listOf("789/⌫", "456*(", "123-)", "0.=+⏎"), labels)
        val texts = Layouts.pad.flatten().mapNotNull { (it.action as? KeyAction.Text)?.text }
        assertEquals("789/456*(123-)0.=+".map { it.toString() }, texts)
        assertTrue(Layouts.pad.flatten().none { it.action is KeyAction.Char })
        val backspace = Layouts.pad[0][4]
        assertEquals(KeyAction.Backspace, backspace.action)
        assertEquals(KeyAction.Code(KeyEvent.KEYCODE_FORWARD_DEL), backspace.up)
        assertEquals(KeyAction.Enter, Layouts.pad[3][4].action)
    }

    @Test
    fun splitHalvesKeepTheAnsiRowStagger() {
        val pxPerMm = 368f / 25.4f
        for (width in listOf(1968, 2184)) {
            val g = KeyboardGeometry.split(Layouts.split, spec(width, 368f, ghost = 1f))
            val u = g.unitPx
            val side = 0.5f * pxPerMm
            fun left(c: Char) = slotLeft(g.keys.first { base(it) == c }, pxPerMm)
            for ((c, ansi) in listOf('`' to 0f, '1' to 1f, 'q' to 1.5f, 'a' to 1.75f, 'z' to 2.25f, '6' to 6f, 't' to 5.5f, 'g' to 5.75f, 'b' to 6.25f)) {
                assertEquals("$width '$c'", side + ansi * u, left(c), 0.5f)
            }
            val gap = left('7') - (side + 7f * u)
            for ((c, ansi) in listOf('7' to 7f, 'y' to 6.5f, 'h' to 6.75f, 'n' to 7.25f, '=' to 12f, ']' to 12.5f, '\'' to 11.75f, '/' to 11.25f)) {
                assertEquals("$width '$c'", side + ansi * u + gap, left(c), 0.5f)
            }
            for (row in 0 until 4) {
                val visible = g.keys.filter { it.row == row && !it.ghost }.sortedBy { it.face.left }
                val split = visible.zipWithNext().maxBy { (a, b) -> b.face.left - a.face.right }
                assertEquals("$width row $row", gap, slotLeft(split.second, pxPerMm) - slotRight(split.first, pxPerMm), 0.5f)
            }
            assertEquals(width - side, g.keys.filter { it.row in 1..4 }.maxOf { slotRight(it, pxPerMm) }, 0.5f)
        }
    }

    @Test
    fun splitKeepsHhkbPlacesWithNarrowRightEdgeKeys() {
        val pxPerMm = 368f / 25.4f
        val g = KeyboardGeometry.split(Layouts.split, spec(1968, 368f, ghost = 1f))
        val u = g.unitPx
        fun width(k: Key) = slotRight(k, pxPerMm) - slotLeft(k, pxPerMm)
        val backspace = g.keys.single { it.def.action == KeyAction.Backspace }
        val enter = g.keys.single { it.def.action == KeyAction.Enter }
        val shifts = g.keys.filter { it.def.action == KeyAction.Mod(Modifier.SHIFT) }.sortedBy { it.face.left }
        assertEquals(1, backspace.row)
        assertEquals(2, enter.row)
        assertEquals(1f * u, width(backspace), 0.5f)
        assertEquals(1.75f * u, width(enter), 0.5f)
        assertEquals(listOf(3, 3), shifts.map { it.row })
        assertEquals(2.25f * u, width(shifts[0]), 0.5f)
        assertEquals(2.25f * u, width(shifts[1]), 0.5f)
        val z = g.keys.first { base(it) == 'z' }
        assertEquals(slotRight(shifts[0], pxPerMm), slotLeft(z, pxPerMm), 0.5f)
        val backslash = g.keys.single { base(it) == '\\' }
        val equals = g.keys.first { base(it) == '=' }
        assertEquals(0, backslash.row)
        assertEquals(slotRight(equals, pxPerMm), slotLeft(backslash, pxPerMm), 0.5f)
        assertEquals(0.5f * u, 1968f - 0.5f * pxPerMm - slotRight(backslash, pxPerMm), 0.5f)
        assertEquals(1968f, backslash.touch.right, 0.01f)
        assertEquals(enter.face.right, backspace.face.right, 0.5f)
        assertEquals(shifts[1].face.right, enter.face.right, 0.5f)
        assertEquals(1968f, backspace.touch.right, 0.01f)
    }

    @Test
    fun fold7PortraitSplitKeysAreAbout8_2mm() {
        val pxPerMm = 368f / 25.4f
        val g = KeyboardGeometry.split(Layouts.split, spec(1968, 368f, ghost = 1f).copy(panelMinPx = 12f * pxPerMm))
        assertEquals(8.17f, g.unitPx / pxPerMm, 0.01f)
        assertTrue(g.panel.isEmpty())
        val ghosts = g.keys.filter { it.ghost }
        assertEquals(8, ghosts.size)
        assertTrue(ghosts.all { abs(it.touch.width / pxPerMm - 8.17f) < 0.02f })
    }

    @Test
    fun fold7LandscapeSplitGetsOnePanelBoxPerRowBetweenHalfWidthGhostKeys() {
        val pxPerMm = 368f / 25.4f
        val g = KeyboardGeometry.split(Layouts.split, spec(2184, 368f, ghost = 1f).copy(panelMinPx = 12f * pxPerMm))
        assertEquals(8.5f, g.unitPx / pxPerMm, 0.001f)
        assertEquals(Layouts.split.size, g.panel.size)
        g.panel.forEachIndexed { r, box ->
            assertEquals("row $r", if (r < 4) 17.99f else 26.49f, box.width / pxPerMm, 0.01f)
            assertEquals(r * 9.5f, box.top / pxPerMm, 0.01f)
            assertEquals(9.5f, box.height / pxPerMm, 0.01f)
        }
        val ghosts = g.keys.filter { it.ghost }
        assertEquals(8, ghosts.size)
        assertTrue(ghosts.all { abs(it.touch.width / pxPerMm - 4.25f) < 0.05f })
        for (box in g.panel) {
            assertTrue(g.keys.none { it.touch.left < box.right && it.touch.right > box.left && it.touch.top < box.bottom && it.touch.bottom > box.top })
        }
        val noPanel = KeyboardGeometry.split(Layouts.split, spec(2184, 368f, ghost = 1f).copy(panelMinPx = 18.5f * pxPerMm))
        assertTrue(noPanel.panel.isEmpty())
    }

    @Test
    fun liftAddsHeightBelowTheHalvesAndExtendsTheBottomRowTouchArea() {
        val pxPerMm = 368f / 25.4f
        val base = KeyboardGeometry.split(Layouts.split, spec(2184, 368f))
        val lifted = KeyboardGeometry.split(Layouts.split, spec(2184, 368f).copy(liftPx = 5f * pxPerMm))
        assertEquals(base.heightPx + 5f * pxPerMm, lifted.heightPx, 0.01f)
        for ((a, b) in base.keys.zip(lifted.keys)) {
            assertEquals(a.face, b.face)
            val extra = if (a.row == Layouts.split.lastIndex) 5f * pxPerMm else 0f
            assertEquals(a.touch.bottom + extra, b.touch.bottom, 0.01f)
        }
    }

    @Test
    fun rowWidthsAreConsistent() {
        Layouts.full.forEach { assertEquals(15f, it.units, 1e-4f) }
        Layouts.compact.forEach { assertEquals(10f, it.units, 1e-4f) }
        assertEquals(listOf(14f, 14.5f, 14.5f, 14.5f, 14.5f), Layouts.split.map { it.left.units + it.right.units })
        assertEquals(listOf(7f, 6.5f, 6.75f, 7.25f, 6.5f), Layouts.split.map { it.left.units })
        assertEquals(14.5f, Layouts.splitUnits, 1e-4f)
        assertEquals(7.25f, Layouts.splitLeftUnits, 1e-4f)
        assertEquals(8f, Layouts.splitRightUnits, 1e-4f)
    }

    @Test
    fun splitKeepsDubeolsikConsonantsLeftAndVowelsRightExceptYu() {
        val left = Layouts.split.flatMap { it.left.keys }.mapNotNull { (it.action as? KeyAction.Char)?.base }.filter { it in 'a'..'z' }
        val right = Layouts.split.flatMap { it.right.keys }.mapNotNull { (it.action as? KeyAction.Char)?.base }.filter { it in 'a'..'z' }
        assertEquals("qwertasdfgzxcvb".toSet(), left.toSet())
        assertEquals("yuiophjklnm".toSet(), right.toSet())
        for (c in left - 'b') assertTrue(io.github.patissiermongs.foldkey.hangul.Jamo.isConsonant(io.github.patissiermongs.foldkey.hangul.Dubeolsik.jamo(c, false)!!))
        assertEquals('ㅠ', io.github.patissiermongs.foldkey.hangul.Dubeolsik.jamo('b', false))
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
    fun fold7SplitHalfSpansFromTheScreenEdges() {
        val pxPerMm = 368f / 25.4f
        for ((width, unitMm, spans) in listOf(Triple(1968, 8.5f, 59.75f to 65.87f), Triple(2184, 8.5f, 62.13f to 68.5f), Triple(2184, 7f, 51.25f to 56.5f))) {
            val (left, right) = spans
            val g = KeyboardGeometry.split(Layouts.split, spec(width, 368f, splitUnitMm = unitMm, ghost = 1f))
            val leftDefs = Layouts.split.flatMap { it.left.keys }
            val visible = g.keys.filter { !it.ghost }
            val leftHalf = visible.filter { k -> leftDefs.any { it === k.def } }
            val rightHalf = visible.filter { k -> leftDefs.none { it === k.def } }
            assertEquals("$width $unitMm left", left, slotRight(leftHalf.maxBy { it.face.right }, pxPerMm) / pxPerMm, 0.02f)
            assertEquals("$width $unitMm right", right, (width - slotLeft(rightHalf.minBy { it.face.left }, pxPerMm)) / pxPerMm, 0.02f)
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
            KeyboardGeometry.full(Layouts.full, spec(1968, 368f), Layouts.pad),
            KeyboardGeometry.full(Layouts.compact, spec(1080, 422f), Layouts.pad),
        )
        for (g in layouts) {
            for (fn in listOf(false, true)) {
                val keys = g.layer(fn)
                val padLeft = keys.filter { it.pad }.minOfOrNull { it.touch.left } ?: Float.MAX_VALUE
                var y = 1f
                while (y < g.heightPx - 1f) {
                    val row = keys.first { y >= it.touch.top && y < it.touch.bottom }.row
                    val visibleEnd = keys.filter { it.row == row && !it.pad }.maxOf { it.touch.right }
                    var x = 0.5f
                    while (x < keys.maxOf { it.touch.right } - 0.5f) {
                        val hits = keys.count { it.touch.contains(x, y) }
                        val empty = fn && row < 4 && x >= visibleEnd && x < padLeft
                        assertEquals("fn=$fn ($x,$y)", if (empty) 0 else 1, hits)
                        x += 7f
                    }
                    y += 11f
                }
            }
        }
    }

    private fun checkPad(g: KeyboardGeometry, rows: Int, pxPerMm: Float) {
        val pad = g.fnKeys.filter { it.pad }
        assertEquals(20, pad.size)
        assertEquals(listOf("789/⌫", "456*(", "123-)", "0.=+⏎"), (0 until 4).map { r -> pad.filter { it.row == r }.sortedBy { it.face.left }.joinToString("") { it.def.label ?: "" } })
        val columns = pad.groupBy { (it.face.left * 10).roundToInt() }
        assertEquals(5, columns.size)
        assertTrue(columns.values.all { col -> col.map { it.row }.sorted() == listOf(0, 1, 2, 3) })
        assertTrue(pad.all { abs((slotRight(it, pxPerMm) - slotLeft(it, pxPerMm)) - g.unitPx) < 0.5f })
        val end = g.keys.filter { !it.ghost }.maxOf { slotRight(it, pxPerMm) }
        assertEquals(end, pad.maxOf { slotRight(it, pxPerMm) }, 0.5f)
        assertEquals(end - 5f * g.unitPx, pad.minOf { slotLeft(it, pxPerMm) }, 0.5f)
        val hText = g.keys.first { base(it) == Layouts.PAD_ANCHOR }
        val others = g.fnKeys.filter { !it.pad }
        assertTrue(others.all { k -> g.keys.any { it === k } })
        assertTrue(others.none { it.ghost && it.row < 4 })
        assertTrue(others.filter { it.row < 4 }.all { slotRight(it, pxPerMm) <= slotLeft(hText, pxPerMm) + 0.5f })
        for (r in 4 until rows) assertEquals(g.keys.filter { it.row == r && !it.ghost }.toSet(), others.filter { it.row == r && !it.ghost }.toSet())
        val leftOfPad = g.keys.filter { it.row < 4 && !it.ghost && slotRight(it, pxPerMm) <= slotLeft(hText, pxPerMm) + 0.5f }
        assertTrue(leftOfPad.isNotEmpty())
        assertTrue(leftOfPad.all { k -> others.any { it === k } })
    }

    @Test
    fun fnSwapsTheRightPartOfTheTopFourRowsForAStraightNumpad() {
        val pxPerMm = 368f / 25.4f
        checkPad(KeyboardGeometry.split(Layouts.split, spec(1968, 368f, ghost = 1f), Layouts.pad), Layouts.split.size, pxPerMm)
        checkPad(KeyboardGeometry.split(Layouts.split, spec(2184, 368f, ghost = 1f).copy(panelMinPx = 12f * pxPerMm), Layouts.pad), Layouts.split.size, pxPerMm)
        checkPad(KeyboardGeometry.full(Layouts.full, spec(1968, 368f), Layouts.pad), Layouts.full.size, pxPerMm)
        val cover = KeyboardGeometry.full(Layouts.compact, spec(1080, 422f), Layouts.pad)
        checkPad(cover, Layouts.compact.size, 422f / 25.4f)
        val coverH = cover.keys.first { base(it) == Layouts.PAD_ANCHOR }
        assertEquals(coverH.face.left, cover.fnKeys.filter { it.pad }.minOf { it.face.left }, 0.5f)
    }

    @Test
    fun splitNumpadSitsAtTheRightEdgeWithLetterSizedKeys() {
        val pxPerMm = 368f / 25.4f
        val g = KeyboardGeometry.split(Layouts.split, spec(1968, 368f, ghost = 1f), Layouts.pad)
        val seven = g.fnKeys.first { it.pad && it.def.label == "7" }
        assertEquals(8.17f, (slotRight(seven, pxPerMm) - slotLeft(seven, pxPerMm)) / pxPerMm, 0.02f)
        assertEquals(41.36f, (1968f - slotLeft(seven, pxPerMm)) / pxPerMm, 0.05f)
        assertEquals(37.27f, (1968f - seven.face.centerX) / pxPerMm, 0.05f)
        val y = g.keys.first { base(it) == 'y' }
        val ghostY = g.keys.first { it.ghost && (it.def.action as? KeyAction.Char)?.base == 'y' }
        assertEquals(ghostY, g.keyAt(ghostY.touch.centerX, ghostY.touch.centerY))
        assertNull(g.keyAt(ghostY.touch.centerX, ghostY.touch.centerY, fn = true))
        assertNull(g.keyAt(y.touch.centerX, y.touch.centerY, fn = true))
        assertEquals(y, g.keyAt(y.touch.centerX, y.touch.centerY))
        val four = g.fnKeys.first { it.pad && it.def.label == "4" }
        assertEquals(four, g.keyAt(four.touch.centerX, four.touch.centerY, fn = true))
        val backspace = g.fnKeys.first { it.pad && it.def.action == KeyAction.Backspace }
        assertEquals(backspace, g.keyAt(1967f, 1f, fn = true))
        assertEquals('\\', base(g.keyAt(1967f, 1f)!!))
    }

    @Test
    fun splitGhostKeysDuplicateTheKeysAcrossTheGap() {
        val g = KeyboardGeometry.split(Layouts.split, spec(2184, 368f, ghost = 1f))
        val ghosts = g.keys.filter { it.ghost }.mapNotNull { (it.def.action as? KeyAction.Char)?.base }.toSet()
        assertEquals("67tyghbn".toSet(), ghosts)
        for ((l, r) in listOf('6' to '7', 't' to 'y', 'g' to 'h', 'b' to 'n')) {
            val leftKey = g.keys.first { base(it) == l }
            val rightKey = g.keys.first { base(it) == r }
            val ghostR = g.keys.first { it.ghost && (it.def.action as? KeyAction.Char)?.base == r }
            val ghostL = g.keys.first { it.ghost && (it.def.action as? KeyAction.Char)?.base == l }
            assertEquals(leftKey.touch.right, ghostR.touch.left, 0.01f)
            assertEquals(rightKey.touch.left, ghostL.touch.right, 0.01f)
            assertEquals(ghostR, g.keyAt(ghostR.touch.centerX, ghostR.touch.centerY))
            assertEquals(null, g.keyAt((ghostR.touch.right + ghostL.touch.left) / 2f, ghostR.touch.centerY))
        }
    }

    private val generalSplitDefs = Layouts.generalSplit.flatMap { it.left.keys + it.right.keys }

    private val generalLayouts = listOf("general" to defsOf(Layouts.general), "general split" to generalSplitDefs)

    @Test
    fun everyLayoutHasOneLayerKey() {
        val all = listOf("full" to defsOf(Layouts.full), "split" to splitDefs, "compact" to defsOf(Layouts.compact)) + generalLayouts
        for ((name, defs) in all) assertEquals(name, 1, defs.count { it.action == Layouts.LAYER_TOGGLE })
    }

    @Test
    fun generalLayerHasLettersDigitsAndProsePunctuationButNoCodeKeys() {
        for ((name, defs) in generalLayouts) {
            val chars = defs.mapNotNull { (it.action as? KeyAction.Char)?.base }
            assertEquals(name, (('a'..'z') + ('0'..'9') + listOf(',', '.')).sorted(), chars.sorted())
            val reachable = defs.flatMap { listOfNotNull(it.action, it.up, it.down) }
                .filterIsInstance<KeyAction.Char>().flatMap { listOf(it.base, it.shifted) }.toSet()
            for (c in "!@#$%^&*()?" + Layouts.PROSE_SYMBOLS) assertTrue("$name '$c'", c in reachable)
            val actions = defs.map { it.action }
            val needed = listOf(KeyAction.Enter, KeyAction.Backspace, KeyAction.Space, KeyAction.Lang, KeyAction.Mod(Modifier.SHIFT))
            for (a in needed) assertTrue("$name $a", a in actions)
            assertEquals(name, if (name == "general split") 2 else 1, actions.count { it == KeyAction.Space })
            val code = listOf(KeyAction.EscCtrl, KeyAction.Mod(Modifier.CTRL), KeyAction.Mod(Modifier.ALT), KeyAction.Mod(Modifier.FN))
            assertTrue(name, actions.none { it in code || it is KeyAction.Code || it is KeyAction.Text })
            assertTrue(name, defs.none { it.fnLabel != null || it.up != null })
            val digits = defs.filter { (it.action as? KeyAction.Char)?.base in '0'..'9' }
            assertEquals(name, "1234567890".toList(), digits.map { (it.action as KeyAction.Char).base })
            assertEquals(name, Layouts.PROSE_SYMBOLS.map { it.toString() }, digits.map { it.downLabel })
            assertEquals(name, Layouts.PROSE_SYMBOLS.toList(), digits.map { (it.down as KeyAction.Char).base })
            val comma = defs.single { (it.action as? KeyAction.Char)?.base == ',' }
            val period = defs.single { (it.action as? KeyAction.Char)?.base == '.' }
            assertEquals(KeyAction.Char(',', '!'), comma.action)
            assertEquals(KeyAction.Char('.', '?'), period.action)
            assertEquals(comma.action, comma.down)
            assertEquals(period.action, period.down)
        }
    }

    @Test
    fun generalRowsFollowThePhoneLayout() {
        assertEquals(listOf(10f, 10f, 9f, 10f, 10f), Layouts.general.map { it.span })
        assertEquals(listOf(10f, 10f, 9.5f, 10f, 10f), Layouts.generalSplit.map { it.left.span + it.right.span })
        assertEquals(listOf(5f, 5f, 5.5f, 5.5f, 5f), Layouts.generalSplit.map { it.left.span })
        assertEquals(10f, Layouts.generalSplitUnits, 1e-4f)
        assertEquals(5.5f, Layouts.generalSplitLeftUnits, 1e-4f)
        assertEquals(5f, Layouts.generalSplitRightUnits, 1e-4f)
        assertEquals(5, Layouts.rows(LayoutKind.COMPACT, io.github.patissiermongs.foldkey.engine.Layer.GENERAL))
        assertEquals(6, Layouts.rows(LayoutKind.COMPACT))
    }

    @Test
    fun generalSplitPutsEveryConsonantLeftAndEveryVowelRight() {
        val left = Layouts.generalSplit.flatMap { it.left.keys }.mapNotNull { (it.action as? KeyAction.Char)?.base }.filter { it in 'a'..'z' }
        val right = Layouts.generalSplit.flatMap { it.right.keys }.mapNotNull { (it.action as? KeyAction.Char)?.base }.filter { it in 'a'..'z' }
        assertEquals("qwertasdfgzxcv".toSet(), left.toSet())
        assertEquals("yuiophjklbnm".toSet(), right.toSet())
        for (c in left) assertTrue(io.github.patissiermongs.foldkey.hangul.Jamo.isConsonant(io.github.patissiermongs.foldkey.hangul.Dubeolsik.jamo(c, false)!!))
        for (c in right) assertTrue(io.github.patissiermongs.foldkey.hangul.Jamo.isVowel(io.github.patissiermongs.foldkey.hangul.Dubeolsik.jamo(c, false)!!))
    }

    @Test
    fun generalSplitKeysGrowOnlyWithinTheCodeHalves() {
        assertEquals(9.23f, Layouts.generalSplitUnitMm(7f), 0.01f)
        assertEquals(9.6f, Layouts.generalSplitUnitMm(8.5f), 1e-4f)
        assertEquals(11f, Layouts.generalSplitUnitMm(11f), 1e-4f)
        for (u in listOf(7f, 7.2f, 7.5f, 8.5f, 9.6f, 10f, 11f)) {
            val g = Layouts.generalSplitUnitMm(u)
            assertTrue("$u", g >= u)
            assertTrue("$u", g * Layouts.generalSplitLeftUnits <= u * Layouts.splitLeftUnits + 1e-3f)
            assertTrue("$u", g * Layouts.generalSplitRightUnits <= u * Layouts.splitRightUnits + 1e-3f)
        }
    }

    @Test
    fun generalSplitKeysShrinkBackWhenTheHalvesAreInset() {
        assertEquals(9.23f, Layouts.generalSplitUnitMm(7f, 0f), 0.01f)
        assertEquals(7f, Layouts.generalSplitUnitMm(7f, 13.5f), 1e-4f)
        assertEquals(8.75f, Layouts.generalSplitUnitMm(8.5f, 13.5f), 0.01f)
        assertEquals(11f, Layouts.generalSplitUnitMm(11f, 13.5f), 1e-4f)
        for (inset in listOf(0f, 6.5f, 13.5f, 24.5f)) for (u in listOf(7f, 7.5f, 8.5f, 9.6f, 11f)) {
            val g = Layouts.generalSplitUnitMm(u, inset)
            assertTrue("$u $inset", g >= u)
            if (g > u + 1e-4f) {
                assertTrue("$u $inset", inset + g * Layouts.generalSplitLeftUnits <= u * Layouts.splitLeftUnits + 1e-3f)
                assertTrue("$u $inset", inset + g * Layouts.generalSplitRightUnits <= u * Layouts.splitRightUnits + 1e-3f)
            }
        }
    }

    @Test
    fun fold7GeneralSplitMarginKeepsTheHalvesOffTheSideEdges() {
        val pxPerMm = 368f / 25.4f
        val leftDefs = Layouts.generalSplit.flatMap { it.left.keys }
        for (width in listOf(2184, 1968)) {
            val g = KeyboardGeometry.split(
                Layouts.generalSplit,
                spec(width, 368f, splitUnitMm = Layouts.generalSplitUnitMm(7f, 13.5f), ghost = 1f)
                    .copy(splitMarginLeftPx = 14f * pxPerMm, splitMarginRightPx = 14f * pxPerMm, panelMinPx = 12f * pxPerMm),
            )
            assertEquals(7f, g.unitPx / pxPerMm, 0.01f)
            val visible = g.keys.filter { !it.ghost }
            val leftHalf = visible.filter { k -> leftDefs.any { it === k.def } }
            val rightHalf = visible.filter { k -> leftDefs.none { it === k.def } }
            assertEquals("$width", 14f, slotLeft(leftHalf.minBy { it.face.left }, pxPerMm) / pxPerMm, 0.02f)
            assertEquals("$width", 14f, (width - slotRight(rightHalf.maxBy { it.face.right }, pxPerMm)) / pxPerMm, 0.02f)
            assertEquals("$width", 52.5f, slotRight(leftHalf.maxBy { it.face.right }, pxPerMm) / pxPerMm, 0.02f)
            assertEquals("$width", 49f, (width - slotLeft(rightHalf.minBy { it.face.left }, pxPerMm)) / pxPerMm, 0.02f)
            for (r in Layouts.generalSplit.indices) {
                assertEquals("$width row $r", 0f, visible.filter { it.row == r }.minOf { it.touch.left }, 0.01f)
                assertEquals("$width row $r", width.toFloat(), visible.filter { it.row == r }.maxOf { it.touch.right }, 0.01f)
            }
            assertEquals("$width", Layouts.generalSplit.size, g.panel.size)
        }
        val portrait = KeyboardGeometry.split(
            Layouts.generalSplit,
            spec(1968, 368f, splitUnitMm = Layouts.generalSplitUnitMm(8.5f, 13.5f), ghost = 1f)
                .copy(splitMarginLeftPx = 14f * pxPerMm, splitMarginRightPx = 14f * pxPerMm, panelMinPx = 12f * pxPerMm),
        )
        assertEquals(8.75f, portrait.unitPx / pxPerMm, 0.01f)
        assertEquals(14f, slotLeft(portrait.keys.first { base(it) == 'q' }, pxPerMm) / pxPerMm, 0.02f)
        assertTrue(portrait.panel.isEmpty())
    }

    @Test
    fun sideDistancesComeFirstAndKeysShrinkToFit() {
        val pxPerMm = 368f / 25.4f
        val g = KeyboardGeometry.split(
            Layouts.generalSplit,
            spec(1968, 368f, splitUnitMm = 11f, ghost = 1f).copy(splitMarginLeftPx = 14f * pxPerMm, splitMarginRightPx = 14f * pxPerMm),
        )
        assertEquals((1968f / pxPerMm - 28f) / 12f, g.unitPx / pxPerMm, 0.01f)
        assertEquals(14f, slotLeft(g.keys.first { base(it) == 'q' }, pxPerMm) / pxPerMm, 0.02f)
        val code = KeyboardGeometry.split(
            Layouts.split,
            spec(2184, 368f, splitUnitMm = 4.3f, ghost = 1f).copy(splitMarginLeftPx = 13.7f * pxPerMm, splitMarginRightPx = 17.4f * pxPerMm),
            Layouts.pad,
        )
        assertEquals(4.3f, code.unitPx / pxPerMm, 0.01f)
        val visible = code.keys.filter { !it.ghost }
        val leftDefs = Layouts.split.flatMap { it.left.keys }
        val leftHalf = visible.filter { k -> leftDefs.any { it === k.def } }
        val rightHalf = visible.filter { k -> leftDefs.none { it === k.def } }
        assertEquals(13.7f, slotLeft(leftHalf.minBy { it.face.left }, pxPerMm) / pxPerMm, 0.02f)
        assertEquals(17.4f, (2184 - slotRight(rightHalf.maxBy { it.face.right }, pxPerMm)) / pxPerMm, 0.02f)
        assertEquals(13.7f + 7.25f * 4.3f, slotRight(leftHalf.maxBy { it.face.right }, pxPerMm) / pxPerMm, 0.02f)
        assertEquals(17.4f + 8f * 4.3f, (2184 - slotLeft(rightHalf.minBy { it.face.left }, pxPerMm)) / pxPerMm, 0.02f)
        for (r in Layouts.split.indices) {
            assertEquals(0f, visible.filter { it.row == r }.minOf { it.touch.left }, 0.01f)
            assertEquals(2184f, visible.filter { it.row == r }.maxOf { it.touch.right }, 0.01f)
        }
        assertEquals(8.75f, Layouts.generalSplitUnitMm(8.5f, 13.5f, 0f), 0.01f)
        assertEquals(9.6f, Layouts.generalSplitUnitMm(8.5f, 0f, -5f), 1e-4f)
    }

    @Test
    fun generalSplitFollowsThePhoneStagger() {
        val pxPerMm = 368f / 25.4f
        for (width in listOf(1968, 2184)) {
            val g = KeyboardGeometry.split(Layouts.generalSplit, spec(width, 368f, splitUnitMm = Layouts.generalSplitUnitMm(7f), ghost = 1f))
            val u = g.unitPx
            assertEquals(9.23f, u / pxPerMm, 0.01f)
            val side = 0.5f * pxPerMm
            fun left(c: Char) = slotLeft(g.keys.first { base(it) == c }, pxPerMm)
            for ((c, at) in listOf('1' to 0f, 'q' to 0f, 'a' to 0.5f, 'z' to 1.5f, '5' to 4f, 't' to 4f, 'g' to 4.5f, 'v' to 4.5f)) {
                assertEquals("$width '$c'", side + at * u, left(c), 0.5f)
            }
            for ((c, ref, d) in listOf(Triple('7', '6', 1f), Triple('u', 'y', 1f), Triple('h', 'y', 0.5f), Triple('j', 'y', 1.5f), Triple('b', 'y', 0.5f), Triple('n', 'y', 1.5f))) {
                assertEquals("$width '$c'", left(ref) + d * u, left(c), 0.5f)
            }
            assertEquals(left('6'), left('y'), 0.5f)
            assertEquals(left('h'), left('b'), 0.5f)
            val a = g.keys.first { base(it) == 'a' }
            assertEquals(0f, a.touch.left, 0.01f)
            val l = g.keys.first { base(it) == 'l' }
            assertEquals(width.toFloat(), l.touch.right, 0.01f)
            assertEquals(0.5f * u, width - side - slotRight(l, pxPerMm), 0.5f)
            val ghosts = g.keys.filter { it.ghost }.mapNotNull { (it.def.action as? KeyAction.Char)?.base }.toSet()
            assertEquals("56tyghvb".toSet(), ghosts)
        }
    }

    @Test
    fun fold7GeneralSplitHalvesStayInsideTheCodeHalves() {
        val pxPerMm = 368f / 25.4f
        for ((width, codeMm, spans) in listOf(
            Triple(2184, 7f, 51.25f to 46.64f),
            Triple(2184, 8.5f, 53.3f to 48.5f),
            Triple(1968, 8.5f, 53.3f to 48.5f),
        )) {
            val (left, right) = spans
            val g = KeyboardGeometry.split(Layouts.generalSplit, spec(width, 368f, splitUnitMm = Layouts.generalSplitUnitMm(codeMm), ghost = 1f))
            val leftDefs = Layouts.generalSplit.flatMap { it.left.keys }
            val visible = g.keys.filter { !it.ghost }
            val leftHalf = visible.filter { k -> leftDefs.any { it === k.def } }
            val rightHalf = visible.filter { k -> leftDefs.none { it === k.def } }
            assertEquals("$width $codeMm left", left, slotRight(leftHalf.maxBy { it.face.right }, pxPerMm) / pxPerMm, 0.02f)
            assertEquals("$width $codeMm right", right, (width - slotLeft(rightHalf.minBy { it.face.left }, pxPerMm)) / pxPerMm, 0.02f)
        }
    }

    @Test
    fun fold7GeneralSplitLandscapeShowsTheCenterPanel() {
        val pxPerMm = 368f / 25.4f
        val g = KeyboardGeometry.split(
            Layouts.generalSplit,
            spec(2184, 368f, splitUnitMm = Layouts.generalSplitUnitMm(8.5f), ghost = 1f).copy(panelMinPx = 12f * pxPerMm),
        )
        assertEquals(Layouts.generalSplit.size, g.panel.size)
        assertTrue(g.panel.all { it.width >= 12f * pxPerMm })
        for (box in g.panel) {
            assertTrue(g.keys.none { it.touch.left < box.right && it.touch.right > box.left && it.touch.top < box.bottom && it.touch.bottom > box.top })
        }
    }

    @Test
    fun generalFullCentersTheHomeRowAndCapsTheKeyWidth() {
        val pxPerMm = 368f / 25.4f
        val g = KeyboardGeometry.full(Layouts.general, spec(1968, 368f))
        assertEquals(11.5f, g.unitPx / pxPerMm, 0.01f)
        val q = g.keys.first { base(it) == 'q' }
        val a = g.keys.first { base(it) == 'a' }
        val p = g.keys.first { base(it) == 'p' }
        val l = g.keys.first { base(it) == 'l' }
        assertEquals(slotLeft(q, pxPerMm) + 0.5f * g.unitPx, slotLeft(a, pxPerMm), 0.5f)
        assertEquals(slotRight(p, pxPerMm) - 0.5f * g.unitPx, slotRight(l, pxPerMm), 0.5f)
        assertEquals(1968f - slotRight(p, pxPerMm), slotLeft(q, pxPerMm), 0.5f)
        val cover = KeyboardGeometry.full(Layouts.general, spec(1080, 422f))
        assertEquals(6.40f, cover.unitPx / (422f / 25.4f), 0.02f)
    }

    @Test
    fun generalFullTouchAreasTileEachRowWithoutOverlapOrHoles() {
        for (g in listOf(KeyboardGeometry.full(Layouts.general, spec(1968, 368f)), KeyboardGeometry.full(Layouts.general, spec(1080, 422f)))) {
            assertEquals(g.keys, g.layer(true))
            var y = 1f
            while (y < g.heightPx - 1f) {
                var x = 0.5f
                while (x < g.keys.maxOf { it.touch.right } - 0.5f) {
                    assertEquals("($x,$y)", 1, g.keys.count { it.touch.contains(x, y) })
                    x += 7f
                }
                y += 11f
            }
        }
    }
}
