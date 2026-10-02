package io.github.patissiermongs.foldkey.engine

import android.view.KeyEvent

object KeyNames {
    fun code(keyCode: Int): String? = when (keyCode) {
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> "F${keyCode - KeyEvent.KEYCODE_F1 + 1}"
        KeyEvent.KEYCODE_MOVE_HOME -> "Home"
        KeyEvent.KEYCODE_MOVE_END -> "End"
        KeyEvent.KEYCODE_PAGE_UP -> "PgUp"
        KeyEvent.KEYCODE_PAGE_DOWN -> "PgDn"
        KeyEvent.KEYCODE_FORWARD_DEL -> "Del"
        KeyEvent.KEYCODE_INSERT -> "Ins"
        KeyEvent.KEYCODE_ESCAPE -> "Esc"
        KeyEvent.KEYCODE_TAB -> "Tab"
        KeyEvent.KEYCODE_ENTER -> "⏎"
        KeyEvent.KEYCODE_DEL -> "⌫"
        KeyEvent.KEYCODE_SPACE -> "␣"
        KeyEvent.KEYCODE_DPAD_LEFT -> "←"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
        KeyEvent.KEYCODE_DPAD_UP -> "↑"
        KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
        else -> null
    }

    fun chord(meta: Int, base: String): String {
        val sb = StringBuilder()
        if (meta and KeyEvent.META_ALT_ON != 0) sb.append("M-")
        if (meta and KeyEvent.META_CTRL_ON != 0) sb.append('^')
        if (meta and KeyEvent.META_SHIFT_ON != 0 && base.length > 1) sb.append('⇧')
        sb.append(if (meta and KeyEvent.META_CTRL_ON != 0 && base.length == 1) base.uppercase() else base)
        return sb.toString()
    }
}
