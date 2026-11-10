package io.github.patissiermongs.foldkey.engine

import android.view.KeyEvent

class HardwareKeys(private val engine: KeyboardEngine) {
    var active = false
    var passThrough = false
    private val consumed = HashSet<Int>()
    private var rightAltAlone = false

    fun down(keyCode: Int, meta: Int, repeat: Int, unicode: Int): Boolean {
        if (keyCode != KeyEvent.KEYCODE_ALT_RIGHT) rightAltAlone = false
        if (repeat > 0) {
            if (keyCode !in consumed) return pass(keyCode)
            if (keyCode !in TOGGLE_KEYS && !compose(keyCode, meta)) {
                engine.hardwareResend(keyCode, resendMeta(meta), resendText(meta, unicode))
            }
            return true
        }
        consumed.remove(keyCode)
        if (!active || passThrough) return false
        if (keyCode in TOGGLE_KEYS) {
            engine.toggleLang()
            return consume(keyCode)
        }
        if (keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            rightAltAlone = meta and OTHER_MODIFIERS == 0
            return false
        }
        if (keyCode in MODIFIER_KEYS) return false
        if (compose(keyCode, meta)) return consume(keyCode)
        val escape = keyCode == KeyEvent.KEYCODE_ESCAPE ||
            (keyCode == KeyEvent.KEYCODE_LEFT_BRACKET && meta and KeyEvent.META_CTRL_ON != 0)
        if (engine.isComposing && engine.context.raw && typing(keyCode, unicode)) {
            engine.hardwareResend(keyCode, resendMeta(meta), resendText(meta, unicode))
            if (escape) engine.hardwareEscape()
            return consume(keyCode)
        }
        pass(keyCode)
        if (escape) engine.hardwareEscape()
        return false
    }

    private fun pass(keyCode: Int): Boolean {
        if (active && !passThrough && keyCode !in MODIFIER_KEYS && keyCode != KeyEvent.KEYCODE_ALT_RIGHT) engine.hardwarePass()
        return false
    }

    fun up(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            val toggle = rightAltAlone && active && !passThrough
            rightAltAlone = false
            if (toggle) engine.toggleLang()
            return false
        }
        return consumed.remove(keyCode)
    }

    private fun consume(keyCode: Int): Boolean {
        consumed.add(keyCode)
        return true
    }

    private fun compose(keyCode: Int, meta: Int): Boolean {
        if (meta and COMMAND_META != 0 || engine.lang != Lang.HANGUL) return false
        if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            return engine.hardwareLetter('a' + (keyCode - KeyEvent.KEYCODE_A), meta and KeyEvent.META_SHIFT_ON != 0)
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) return engine.hardwareBackspace()
        return false
    }

    private fun typing(keyCode: Int, unicode: Int): Boolean =
        unicode > 0 || keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z || keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ||
            keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 || keyCode in TYPING_KEYS

    private fun resendMeta(meta: Int): Int {
        var out = 0
        if (meta and KeyEvent.META_SHIFT_ON != 0) out = out or Modifiers.SHIFT_META
        if (meta and KeyEvent.META_CTRL_ON != 0) out = out or Modifiers.CTRL_META
        if (meta and KeyEvent.META_ALT_ON != 0) out = out or Modifiers.ALT_META
        return out
    }

    private fun resendText(meta: Int, unicode: Int): String? {
        if (meta and COMMAND_META != 0 || unicode <= 0 || unicode == DELETE) return null
        if (unicode < ' '.code) {
            val plain = meta and KeyEvent.META_SHIFT_ON == 0 && (unicode == '\n'.code || unicode == '\t'.code)
            if (!plain) return null
        }
        return String(Character.toChars(unicode))
    }

    companion object {
        private const val DELETE = 0x7f
        val TOGGLE_KEYS = setOf(KeyEvent.KEYCODE_KANA, KeyEvent.KEYCODE_LANGUAGE_SWITCH)
        private const val COMMAND_META =
            KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK or KeyEvent.META_META_MASK or KeyEvent.META_FUNCTION_ON
        private const val OTHER_MODIFIERS =
            KeyEvent.META_SHIFT_MASK or KeyEvent.META_CTRL_MASK or KeyEvent.META_META_MASK or KeyEvent.META_FUNCTION_ON or KeyEvent.META_SYM_ON
        private val TYPING_KEYS = setOf(
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_TAB,
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_INSERT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_GRAVE, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_LEFT_BRACKET,
            KeyEvent.KEYCODE_RIGHT_BRACKET, KeyEvent.KEYCODE_BACKSLASH, KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE,
            KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD,
        )
        private val MODIFIER_KEYS = setOf(
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT,
            KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_FUNCTION,
            KeyEvent.KEYCODE_CAPS_LOCK, KeyEvent.KEYCODE_NUM_LOCK, KeyEvent.KEYCODE_SCROLL_LOCK,
        )
    }
}
