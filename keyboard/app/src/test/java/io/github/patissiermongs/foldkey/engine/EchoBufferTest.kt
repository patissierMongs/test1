package io.github.patissiermongs.foldkey.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EchoBufferTest {
    private fun shown(b: EchoBuffer) = b.items.joinToString("|") { if (it.token) "<${it.display}>" else it.display }

    @Test
    fun textAndTokensKeepOrderAndRepeatedTokensCollapse() {
        val b = EchoBuffer()
        b.text("git co")
        b.token("Tab")
        b.text("main")
        b.token("←")
        b.token("←")
        b.token("←")
        assertEquals("git co|<Tab>|main|<←×3>", shown(b))
    }

    @Test
    fun backspaceRemovesOneCodePointOrRecordsAToken() {
        val b = EchoBuffer()
        b.backspace()
        assertEquals("", shown(b))
        b.text("a한😀")
        b.backspace()
        assertEquals("a한", shown(b))
        b.backspace()
        b.backspace()
        assertEquals("", shown(b))
        b.text("x")
        b.token("^C")
        b.backspace()
        assertEquals("x|<^C>|<⌫>", shown(b))
    }

    @Test
    fun newlineStartsAFreshLine() {
        val b = EchoBuffer()
        b.text("echo one")
        b.text("first\nsecond")
        assertEquals("second", shown(b))
    }

    @Test
    fun oldestCodePointsAreDroppedPastTheLimit() {
        val b = EchoBuffer(limit = 6)
        b.token("Esc")
        b.text("😀abcd")
        assertEquals("😀abcd", shown(b))
        b.text("ef")
        assertEquals("abcdef", shown(b))
        b.text("g")
        assertEquals("bcdefg", shown(b))
    }
}
