package io.github.patissiermongs.foldkey.ime

import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import io.github.patissiermongs.foldkey.engine.Editor
import io.github.patissiermongs.foldkey.engine.Modifiers

class InputConnectionEditor(
    private val context: Context,
    private val connection: () -> InputConnection?,
) : Editor {

    override fun commitText(text: String) {
        connection()?.commitText(text, 1)
    }

    override fun setComposingText(text: String) {
        connection()?.setComposingText(text, 1)
    }

    override fun finishComposingText() {
        connection()?.finishComposingText()
    }

    override fun sendKey(keyCode: Int, meta: Int) {
        val ic = connection() ?: return
        val down = SystemClock.uptimeMillis()
        val held = MODIFIER_KEYS.filter { (meta and it.second) == it.second }
        var state = 0
        for ((code, bits) in held) {
            state = state or bits
            ic.sendKeyEvent(event(down, down, KeyEvent.ACTION_DOWN, code, state))
        }
        ic.sendKeyEvent(event(down, down, KeyEvent.ACTION_DOWN, keyCode, meta))
        val up = SystemClock.uptimeMillis()
        ic.sendKeyEvent(event(down, up, KeyEvent.ACTION_UP, keyCode, meta))
        for ((code, bits) in held.asReversed()) {
            state = state and bits.inv()
            ic.sendKeyEvent(event(down, up, KeyEvent.ACTION_UP, code, state))
        }
    }

    override fun beginBatch() {
        connection()?.beginBatchEdit()
    }

    override fun endBatch() {
        connection()?.endBatchEdit()
    }

    override fun performEditorAction(action: Int) {
        connection()?.performEditorAction(action)
    }

    override fun paste(raw: Boolean) {
        val ic = connection() ?: return
        if (!raw) {
            ic.performContextMenuAction(android.R.id.paste)
            return
        }
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return
        if (text.isNotEmpty()) ic.commitText(text, 1)
    }

    override fun selectAll() {
        connection()?.performContextMenuAction(android.R.id.selectAll)
    }

    override fun copy() {
        connection()?.performContextMenuAction(android.R.id.copy)
    }

    companion object {
        private val MODIFIER_KEYS = listOf(
            KeyEvent.KEYCODE_CTRL_LEFT to Modifiers.CTRL_META,
            KeyEvent.KEYCODE_ALT_LEFT to Modifiers.ALT_META,
            KeyEvent.KEYCODE_SHIFT_LEFT to Modifiers.SHIFT_META,
        )
    }

    private fun event(downTime: Long, eventTime: Long, action: Int, keyCode: Int, meta: Int) = KeyEvent(
        downTime,
        eventTime,
        action,
        keyCode,
        0,
        meta,
        KeyCharacterMap.VIRTUAL_KEYBOARD,
        0,
        KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
    )
}
