package io.github.patissiermongs.foldkey.input

import io.github.patissiermongs.foldkey.engine.Gesture
import io.github.patissiermongs.foldkey.layout.Key
import kotlin.math.abs

data class TouchParams(
    val swipeThresholdPx: Float,
    val cursorStartPx: Float,
    val cursorStepPx: Float,
    val longPressMs: Long = 0L,
    val repeatDelayMs: Long = 400L,
    val repeatIntervalMs: Long = 50L,
    val verticalDominance: Float = 1.2f,
)

interface TouchSink {
    fun hit(x: Float, y: Float, t: Long): Key?

    fun keyDown(pointer: Int, key: Key, t: Long)

    fun modifierDown(key: Key, t: Long)

    fun modifierUp(key: Key, t: Long)

    fun fire(key: Key, gesture: Gesture, t: Long)

    fun variant(pointer: Int, key: Key, gesture: Gesture)

    fun released(pointer: Int)

    fun cursor(steps: Int)

    fun cursorEnd()

    fun sample(key: Key, x: Float, y: Float)
}

class TouchTracker(private val sink: TouchSink, var params: TouchParams) {
    private enum class Mode { PENDING, MODIFIER, REPEAT, CURSOR, DONE }

    private class Pointer(val id: Int, val key: Key, val x0: Float, val y0: Float) {
        var x = x0
        var y = y0
        var mode = Mode.PENDING
        var deadline = Long.MAX_VALUE
        var gesture = Gesture.TAP
        var anchor = x0
        var repeatOnHold = false
    }

    private val pointers = LinkedHashMap<Int, Pointer>()

    val activePointers: Int get() = pointers.size

    fun down(id: Int, x: Float, y: Float, t: Long) {
        pointers.remove(id)?.let { finish(it, t) }
        val key = sink.hit(x, y, t) ?: return
        if (key.def.isModifier) releasePending(t, pointers.values.toList())
        val p = Pointer(id, key, x, y)
        pointers[id] = p
        sink.keyDown(id, key, t)
        val def = key.def
        when {
            def.isModifier -> {
                p.mode = Mode.MODIFIER
                sink.modifierDown(key, t)
            }
            def.repeat && !def.hasVariants -> {
                releasePending(t, pointers.values.filter { it !== p })
                sink.fire(key, Gesture.TAP, t)
                p.mode = Mode.REPEAT
                p.deadline = t + params.repeatDelayMs
            }
            def.repeat -> {
                p.repeatOnHold = true
                p.deadline = t + params.repeatDelayMs
                sink.variant(id, key, Gesture.TAP)
            }
            else -> {
                if (params.longPressMs > 0 && def.hasVariants && !def.isSpace) p.deadline = t + params.longPressMs
                sink.variant(id, key, Gesture.TAP)
            }
        }
    }

    fun move(id: Int, x: Float, y: Float, t: Long) {
        val p = pointers[id] ?: return
        p.x = x
        p.y = y
        when (p.mode) {
            Mode.PENDING -> {
                if (p.key.def.isSpace) {
                    val dx = x - p.x0
                    if (abs(dx) >= params.cursorStartPx) {
                        releasePending(t, pointers.values.takeWhile { it !== p })
                        p.mode = Mode.CURSOR
                        p.anchor = p.x0 + if (dx > 0) params.cursorStartPx else -params.cursorStartPx
                        stepCursor(p)
                    }
                    return
                }
                val g = classify(x - p.x0, y - p.y0, p.key)
                if (g != p.gesture) {
                    p.gesture = g
                    if (g != Gesture.TAP) p.deadline = Long.MAX_VALUE
                    sink.variant(id, p.key, g)
                }
            }
            Mode.CURSOR -> stepCursor(p)
            else -> Unit
        }
    }

    fun up(id: Int, x: Float, y: Float, t: Long) {
        val p = pointers[id] ?: return
        p.x = x
        p.y = y
        releasePending(t, pointers.values.takeWhile { it !== p })
        pointers.remove(id)
        finish(p, t)
    }

    fun cancel(id: Int, t: Long) {
        val p = pointers.remove(id) ?: return
        when (p.mode) {
            Mode.MODIFIER -> sink.modifierUp(p.key, t)
            Mode.CURSOR -> sink.cursorEnd()
            else -> Unit
        }
        sink.released(p.id)
    }

    fun cancelAll(t: Long) {
        for (id in pointers.keys.toList()) cancel(id, t)
    }

    fun nextDeadline(): Long? = pointers.values.minOfOrNull { it.deadline }?.takeIf { it != Long.MAX_VALUE }

    fun tick(t: Long) {
        for (p in pointers.values.toList()) {
            if (p.deadline > t) continue
            when (p.mode) {
                Mode.REPEAT -> {
                    sink.fire(p.key, Gesture.REPEAT, t)
                    p.deadline = t + params.repeatIntervalMs
                }
                Mode.PENDING -> {
                    releasePending(t, pointers.values.takeWhile { it !== p })
                    if (p.repeatOnHold) {
                        sink.fire(p.key, Gesture.TAP, t)
                        p.mode = Mode.REPEAT
                        p.deadline = t + params.repeatIntervalMs
                    } else {
                        p.deadline = Long.MAX_VALUE
                        sink.fire(p.key, Gesture.LONG, t)
                        sink.variant(p.id, p.key, Gesture.LONG)
                        p.mode = Mode.DONE
                    }
                }
                else -> p.deadline = Long.MAX_VALUE
            }
        }
    }

    private fun releasePending(t: Long, candidates: List<Pointer>) {
        for (other in candidates) {
            if (other.mode != Mode.PENDING) continue
            commit(other, t)
            other.mode = Mode.DONE
            other.deadline = Long.MAX_VALUE
            sink.released(other.id)
        }
    }

    private fun finish(p: Pointer, t: Long) {
        when (p.mode) {
            Mode.MODIFIER -> sink.modifierUp(p.key, t)
            Mode.PENDING -> commit(p, t)
            Mode.CURSOR -> sink.cursorEnd()
            Mode.REPEAT, Mode.DONE -> Unit
        }
        sink.released(p.id)
    }

    private fun commit(p: Pointer, t: Long) {
        val g = if (p.key.def.isSpace) Gesture.TAP else classify(p.x - p.x0, p.y - p.y0, p.key)
        sink.fire(p.key, g, t)
        if (g == Gesture.TAP) sink.sample(p.key, p.x0, p.y0)
    }

    private fun stepCursor(p: Pointer) {
        val steps = ((p.x - p.anchor) / params.cursorStepPx).toInt()
        if (steps != 0) {
            p.anchor += steps * params.cursorStepPx
            sink.cursor(steps)
        }
    }

    private fun classify(dx: Float, dy: Float, key: Key): Gesture {
        if (!key.def.hasVariants) return Gesture.TAP
        val ady = abs(dy)
        if (ady >= params.swipeThresholdPx && ady >= abs(dx) * params.verticalDominance) {
            return if (dy < 0) Gesture.UP else Gesture.DOWN
        }
        return Gesture.TAP
    }
}
