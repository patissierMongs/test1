package io.github.patissiermongs.foldkey.engine

enum class Reconciled { ECHO, MOVED }

class TrackingEditor(private val inner: Editor) : Editor {
    private val segments = ArrayDeque<Segment>().apply { add(Segment(null)) }

    fun restart(selStart: Int, selEnd: Int) {
        segments.clear()
        segments.add(if (selStart >= 0 && selEnd >= 0) Segment(0, Snapshot(selStart, selEnd, -1, -1)) else Segment(null))
    }

    fun yieldKey() {
        val last = segments.last()
        if (last.origin != null || last.states.isNotEmpty()) segments.add(Segment(null))
    }

    fun reconcile(selStart: Int, selEnd: Int, candStart: Int, candEnd: Int): Reconciled {
        val report = Snapshot.of(selStart, selEnd, candStart, candEnd)
        for ((i, segment) in segments.withIndex()) {
            val origin = segment.origin
            if (origin == null && !report.composing) return keep(i, 0)
            for ((j, state) in segment.states.withIndex()) {
                val by = origin ?: if (state.composing && report.composing) report.candStart - state.candStart else continue
                if (state.plus(by) == report) {
                    segment.origin = by
                    return keep(i, j)
                }
            }
        }
        segments.clear()
        segments.add(Segment(0, report))
        return Reconciled.MOVED
    }

    override fun commitText(text: String) {
        inner.commitText(text)
        val at = editStart() + text.length
        expect(Snapshot(at, at, -1, -1))
    }

    override fun setComposingText(text: String) {
        inner.setComposingText(text)
        val start = editStart()
        val end = start + text.length
        expect(Snapshot.of(end, end, start, end))
    }

    override fun finishComposingText() {
        inner.finishComposingText()
        val last = segments.last().states.lastOrNull() ?: return
        expect(last.copy(candStart = -1, candEnd = -1))
    }

    override fun sendKey(keyCode: Int, meta: Int) {
        inner.sendKey(keyCode, meta)
        yieldKey()
    }

    override fun sendBareKey(keyCode: Int) {
        inner.sendBareKey(keyCode)
        yieldKey()
    }

    override fun performEditorAction(action: Int) {
        inner.performEditorAction(action)
        yieldKey()
    }

    override fun paste(raw: Boolean) {
        inner.paste(raw)
        yieldKey()
    }

    override fun selectAll() {
        inner.selectAll()
        yieldKey()
    }

    override fun copy() {
        inner.copy()
        yieldKey()
    }

    override fun cut() {
        inner.cut()
        yieldKey()
    }

    override fun beginBatch() = inner.beginBatch()

    override fun endBatch() = inner.endBatch()

    private fun keep(segment: Int, state: Int): Reconciled {
        repeat(segment) { segments.removeFirst() }
        repeat(state) { segments.first().states.removeFirst() }
        return Reconciled.ECHO
    }

    private fun editStart(): Int = segments.last().states.lastOrNull()?.editStart ?: 0

    private fun expect(state: Snapshot) {
        val states = segments.last().states
        if (states.lastOrNull() == state) return
        states.addLast(state)
        if (segments.sumOf { it.states.size } <= LIMIT) return
        val oldest = segments.first { it.states.isNotEmpty() }
        oldest.states.removeFirst()
        if (oldest.states.isEmpty() && segments.size > 1) segments.remove(oldest)
    }

    private companion object {
        const val LIMIT = 64
    }
}

private data class Snapshot(val selStart: Int, val selEnd: Int, val candStart: Int, val candEnd: Int) {
    val composing: Boolean get() = candStart >= 0

    val editStart: Int get() = if (composing) candStart else minOf(selStart, selEnd)

    fun plus(by: Int): Snapshot =
        if (composing) Snapshot(selStart + by, selEnd + by, candStart + by, candEnd + by) else Snapshot(selStart + by, selEnd + by, -1, -1)

    companion object {
        fun of(selStart: Int, selEnd: Int, candStart: Int, candEnd: Int): Snapshot =
            if (candStart < 0 || candEnd <= candStart) Snapshot(selStart, selEnd, -1, -1) else Snapshot(selStart, selEnd, candStart, candEnd)
    }
}

private class Segment(var origin: Int?, vararg initial: Snapshot) {
    val states = ArrayDeque(initial.toList())
}
