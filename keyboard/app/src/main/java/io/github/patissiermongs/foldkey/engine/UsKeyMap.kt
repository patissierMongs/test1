package io.github.patissiermongs.foldkey.engine

import android.view.KeyEvent

data class KeyStroke(val keyCode: Int, val shift: Boolean)

object UsKeyMap {
    private const val UNSHIFTED = "`1234567890-=[]\\;',./"
    private const val SHIFTED = "~!@#$%^&*()_+{}|:\"<>?"
    private val CODES = intArrayOf(
        KeyEvent.KEYCODE_GRAVE,
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5,
        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_0,
        KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS,
        KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_BACKSLASH,
        KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE,
        KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SLASH,
    )

    fun strokeFor(c: Char): KeyStroke? {
        if (c in 'a'..'z') return KeyStroke(KeyEvent.KEYCODE_A + (c - 'a'), false)
        if (c in 'A'..'Z') return KeyStroke(KeyEvent.KEYCODE_A + (c - 'A'), true)
        if (c == ' ') return KeyStroke(KeyEvent.KEYCODE_SPACE, false)
        val u = UNSHIFTED.indexOf(c)
        if (u >= 0) return KeyStroke(CODES[u], false)
        val s = SHIFTED.indexOf(c)
        if (s >= 0) return KeyStroke(CODES[s], true)
        return null
    }

    fun shiftedOf(c: Char): Char {
        if (c in 'a'..'z') return c.uppercaseChar()
        val u = UNSHIFTED.indexOf(c)
        return if (u >= 0) SHIFTED[u] else c
    }
}
