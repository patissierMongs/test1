package io.github.patissiermongs.foldkey.engine

import android.view.KeyEvent

class ModifierRelay {
    class Held(val keyCode: Int, val scanCode: Int, val deviceId: Int, val source: Int, val downTime: Long)

    private val held = LinkedHashMap<Int, Held>()
    private val owned = HashSet<Long>()

    val meta: Int get() = held.values.fold(0) { bits, key -> bits or metaOf(key.keyCode) }

    fun isHeld(scanCode: Int): Boolean = scanCode in held

    fun press(key: Held) {
        held[key.scanCode] = key
    }

    fun release(scanCode: Int) {
        held.remove(scanCode)
    }

    fun own(keyCode: Int, scanCode: Int) {
        owned.add(id(keyCode, scanCode))
    }

    fun disown(keyCode: Int, scanCode: Int): Boolean = owned.remove(id(keyCode, scanCode))

    fun releaseAll(): List<Pair<Held, Int>> {
        val out = ArrayList<Pair<Held, Int>>()
        for (scanCode in held.keys.reversed()) {
            val key = held.remove(scanCode) ?: continue
            out.add(key to meta)
        }
        owned.clear()
        return out
    }

    companion object {
        private val MODIFIERS = mapOf(
            29 to KeyEvent.KEYCODE_CTRL_LEFT,
            97 to KeyEvent.KEYCODE_CTRL_RIGHT,
            56 to KeyEvent.KEYCODE_ALT_LEFT,
            100 to KeyEvent.KEYCODE_ALT_RIGHT,
            125 to KeyEvent.KEYCODE_META_LEFT,
            126 to KeyEvent.KEYCODE_META_RIGHT,
        )

        fun modifierFor(keyCode: Int, scanCode: Int): Int? =
            if (keyCode == KeyEvent.KEYCODE_UNKNOWN) MODIFIERS[scanCode] else null

        fun metaOf(keyCode: Int): Int = when (keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            KeyEvent.KEYCODE_CTRL_RIGHT -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_RIGHT_ON
            KeyEvent.KEYCODE_ALT_LEFT -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            KeyEvent.KEYCODE_ALT_RIGHT -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON
            KeyEvent.KEYCODE_META_LEFT -> KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
            KeyEvent.KEYCODE_META_RIGHT -> KeyEvent.META_META_ON or KeyEvent.META_META_RIGHT_ON
            else -> 0
        }

        private fun id(keyCode: Int, scanCode: Int): Long = (keyCode.toLong() shl 32) or (scanCode.toLong() and 0xffffffffL)
    }
}
