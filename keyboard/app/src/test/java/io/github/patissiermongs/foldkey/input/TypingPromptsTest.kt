package io.github.patissiermongs.foldkey.input

import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.layout.Layouts
import io.github.patissiermongs.foldkey.layout.SplitRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingPromptsTest {
    private fun chars(rows: List<SplitRow>, left: Boolean) =
        rows.flatMap { (if (left) it.left else it.right).keys }.mapNotNull { (it.action as? KeyAction.Char)?.base }.toSet()

    @Test
    fun everyPromptIsTypedWithUnshiftedKeysOfItsLayer() {
        val code = chars(Layouts.split, true) + chars(Layouts.split, false)
        for (line in TypingPrompts.pool(Layer.CODE)) {
            assertTrue(line, TypingPrompts.typeable(line, Layer.CODE))
            assertTrue(line, TypingPrompts.keys(line)!!.all { it == ' ' || it in code })
        }
        val general = chars(Layouts.generalSplit, true) + chars(Layouts.generalSplit, false)
        for (line in TypingPrompts.pool(Layer.GENERAL)) {
            assertTrue(line, TypingPrompts.typeable(line, Layer.GENERAL))
            assertTrue(line, TypingPrompts.keys(line)!!.all { it == ' ' || it in general })
        }
    }

    @Test
    fun hangulIsSplitIntoTwoSetKeys() {
        assertEquals("ghldml".toList(), TypingPrompts.keys("회의"))
        assertEquals("ekfr".toList(), TypingPrompts.keys("닭"))
        assertEquals("rkqt".toList(), TypingPrompts.keys("값"))
        assertEquals("dkssud".toList(), TypingPrompts.keys("안녕"))
        assertNull(TypingPrompts.keys("까"))
        assertNull(TypingPrompts.keys("Git"))
    }

    @Test
    fun roundsAreLongEnoughAndStartOnDifferentLines() {
        for (layer in Layer.entries) {
            val starts = (0 until 3).map { TypingPrompts.round(layer, it, 60) }
            for (lines in starts) {
                assertTrue(lines.sumOf { line -> TypingPrompts.keys(line)!!.count { it != ' ' } } >= 60)
            }
            assertNotEquals(starts[0].first(), starts[1].first())
            assertNotEquals(starts[1].first(), starts[2].first())
        }
    }

    @Test
    fun roundsReachBothHalvesWidely() {
        for ((layer, rows) in listOf(Layer.CODE to Layouts.split, Layer.GENERAL to Layouts.generalSplit)) {
            val left = chars(rows, true)
            val right = chars(rows, false)
            for (r in 0 until 3) {
                val keys = TypingPrompts.round(layer, r, 60).flatMap { TypingPrompts.keys(it)!! }.toSet()
                assertTrue("$layer round $r left ${keys.intersect(left)}", keys.intersect(left).size >= 10)
                assertTrue("$layer round $r right ${keys.intersect(right)}", keys.intersect(right).size >= 10)
            }
        }
    }
}
