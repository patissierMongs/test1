package io.github.patissiermongs.foldkey.engine

import android.view.KeyEvent

class LaggingApp : Editor {
    val text = StringBuilder()
    var cursor = 0
        private set
    private var composeStart = -1
    private var composeEnd = -1
    private var depth = 0
    private var key = 0
    private var reported = Report(0, 0, -1, -1, 0)
    private val pending = ArrayDeque<Report>()

    private data class Report(val old: Int, val new: Int, val candStart: Int, val candEnd: Int, val key: Int)

    val composing: String get() = if (composeStart < 0) "" else text.substring(composeStart, composeEnd)

    override fun commitText(text: String) {
        replaceTarget(text)
        clearComposing()
        changed()
    }

    override fun setComposingText(text: String) {
        val start = replaceTarget(text)
        composeStart = start
        composeEnd = start + text.length
        changed()
    }

    override fun finishComposingText() {
        clearComposing()
        changed()
    }

    override fun sendKey(keyCode: Int, meta: Int) {
        if (keyCode == KeyEvent.KEYCODE_DEL && meta == 0) appDelete()
    }

    override fun sendBareKey(keyCode: Int) = Unit

    override fun performEditorAction(action: Int) = Unit

    override fun paste(raw: Boolean) = Unit

    override fun selectAll() = Unit

    override fun copy() = Unit

    override fun cut() = Unit

    override fun beginBatch() {
        depth++
    }

    override fun endBatch() {
        depth--
        changed()
    }

    fun appType(c: Char) {
        text.insert(cursor, c)
        if (composeStart >= cursor) {
            composeStart++
            composeEnd++
        }
        cursor++
        changed()
    }

    fun appDelete() {
        if (cursor == 0) return
        cursor--
        text.deleteCharAt(cursor)
        if (composeStart >= 0) {
            if (cursor < composeStart) composeStart--
            if (cursor < composeEnd) composeEnd--
            if (composeStart >= composeEnd) clearComposing()
        }
        changed()
    }

    fun appMoveCursor(to: Int) {
        cursor = to
        changed()
    }

    fun nextKey() {
        key++
    }

    fun deliver(engine: KeyboardEngine, lag: Int = 0) {
        while (pending.isNotEmpty() && pending.first().key <= key - lag) {
            val r = pending.removeFirst()
            engine.selectionChanged(r.old, r.old, r.new, r.new, r.candStart, r.candEnd)
        }
    }

    private fun replaceTarget(value: String): Int {
        val start = if (composeStart >= 0) composeStart else cursor
        val end = if (composeStart >= 0) composeEnd else cursor
        text.replace(start, end, value)
        cursor = start + value.length
        return start
    }

    private fun clearComposing() {
        composeStart = -1
        composeEnd = -1
    }

    private fun changed() {
        if (depth > 0) return
        if (cursor == reported.new && composeStart == reported.candStart && composeEnd == reported.candEnd) return
        reported = Report(reported.new, cursor, composeStart, composeEnd, key)
        pending.addLast(reported)
    }
}
