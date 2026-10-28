package io.github.patissiermongs.foldkey.ui

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTest {
    private fun config(keyboard: Int, hidden: Int = Configuration.HARDKEYBOARDHIDDEN_NO) = Configuration().apply {
        this.keyboard = keyboard
        hardKeyboardHidden = hidden
    }

    @Test
    fun onlyAnExposedQwertyKeyboardCountsAsAttached() {
        assertTrue(Overlay.hardwareKeyboard(config(Configuration.KEYBOARD_QWERTY)))
        assertTrue(Overlay.hardwareKeyboard(config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_UNDEFINED)))
        assertFalse(Overlay.hardwareKeyboard(config(Configuration.KEYBOARD_QWERTY, Configuration.HARDKEYBOARDHIDDEN_YES)))
        assertFalse(Overlay.hardwareKeyboard(config(Configuration.KEYBOARD_NOKEYS)))
        assertFalse(Overlay.hardwareKeyboard(config(Configuration.KEYBOARD_12KEY)))
        assertFalse(Overlay.hardwareKeyboard(config(Configuration.KEYBOARD_UNDEFINED)))
    }

    @Test
    fun chipUntilSummonedThenTheDockOrHoverChoice() {
        val o = Overlay()
        val attached = config(Configuration.KEYBOARD_QWERTY)
        val none = config(Configuration.KEYBOARD_NOKEYS)
        assertEquals(Presentation.CHIP, o.presentation(attached, hover = false))
        assertEquals(Presentation.CHIP, o.presentation(attached, hover = true))
        assertEquals(Presentation.DOCKED, o.presentation(none, hover = false))
        assertEquals(Presentation.HOVER, o.presentation(none, hover = true))
        o.summoned = true
        o.collapsed = true
        assertEquals(Presentation.DOCKED, o.presentation(attached, hover = false))
        assertEquals(Presentation.COLLAPSED, o.presentation(attached, hover = true))
        o.reset()
        assertEquals(Presentation.CHIP, o.presentation(attached, hover = true))
        assertEquals(Presentation.HOVER, o.presentation(none, hover = true))
    }
}
