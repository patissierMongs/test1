package io.github.patissiermongs.foldkey.engine

import android.view.KeyEvent

class ExpectedSelection {
    var start = -1
        private set
    var end = -1
        private set
    private var composeStart = -1

    val known: Boolean get() = start >= 0 && end >= 0

    fun reset(selStart: Int, selEnd: Int, candidatesStart: Int = -1) {
        if (selStart < 0 || selEnd < 0) {
            forget()
            return
        }
        start = selStart
        end = selEnd
        composeStart = candidatesStart
    }

    fun forget() {
        start = -1
        end = -1
        composeStart = -1
    }

    fun commit(length: Int) {
        if (!known) return
        val base = if (composeStart >= 0) composeStart else minOf(start, end)
        start = base + length
        end = start
        composeStart = -1
    }

    fun compose(length: Int) {
        if (!known) return
        if (composeStart < 0) composeStart = minOf(start, end)
        start = composeStart + length
        end = start
    }

    fun finishComposing() {
        composeStart = -1
    }

    fun deleteBefore() {
        if (!known) return
        if (start != end) {
            start = minOf(start, end)
        } else if (start > 0) {
            start--
        }
        end = start
        composeStart = -1
    }

    fun isBelated(oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int): Boolean {
        if (!known) return false
        if (start == newStart && end == newEnd) return false
        if (start == oldStart && end == oldEnd) return false
        return newStart == newEnd &&
            (newStart - oldStart).toLong() * (start - newStart) >= 0 &&
            (newEnd - oldEnd).toLong() * (end - newEnd) >= 0
    }
}

class TrackingEditor(private val inner: Editor) : Editor {
    val expected = ExpectedSelection()

    override fun commitText(text: String) {
        inner.commitText(text)
        expected.commit(text.length)
    }

    override fun setComposingText(text: String) {
        inner.setComposingText(text)
        expected.compose(text.length)
    }

    override fun finishComposingText() {
        inner.finishComposingText()
        expected.finishComposing()
    }

    override fun sendKey(keyCode: Int, meta: Int) {
        inner.sendKey(keyCode, meta)
        if (keyCode == KeyEvent.KEYCODE_DEL && meta == 0) expected.deleteBefore() else expected.forget()
    }

    override fun performEditorAction(action: Int) {
        inner.performEditorAction(action)
        expected.forget()
    }

    override fun paste(raw: Boolean) {
        inner.paste(raw)
        expected.forget()
    }

    override fun selectAll() {
        inner.selectAll()
        expected.forget()
    }

    override fun copy() {
        inner.copy()
        expected.forget()
    }

    override fun beginBatch() = inner.beginBatch()

    override fun endBatch() = inner.endBatch()
}
