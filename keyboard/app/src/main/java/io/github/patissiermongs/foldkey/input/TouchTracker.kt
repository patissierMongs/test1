package io.github.patissiermongs.foldkey.input

import io.github.patissiermongs.foldkey.engine.Gesture
import io.github.patissiermongs.foldkey.layout.Key
import kotlin.math.abs

data class TouchParams(
    val swipeThresholdPx: Float,
    val cursorStartPx: Float,
    val cursorStepPx: Float,
    val cursorRowStepPx: Float = cursorStepPx * 1.6f,
    val longPressMs: Long = 0L,
    val longPressRepeats: Boolean = false,
    val repeatDelayMs: Long = 400L,
    val repeatIntervalMs: Long = 50L,
    val verticalDominance: Float = 1.2f,
    val selectHoldMs: Long = 400L,
    val rollMs: Long = 150L,
)

interface TouchSink {
    fun hit(x: Float, y: Float, t: Long): Key?

    fun keyDown(pointer: Int, key: Key, t: Long)

    fun modifierDown(key: Key, t: Long)

    fun modifierUp(key: Key, t: Long)

    fun modifierCancel(key: Key, t: Long)

    fun fire(key: Key, gesture: Gesture, t: Long)

    fun variant(pointer: Int, key: Key, gesture: Gesture)

    fun released(pointer: Int)

    fun cursor(steps: Int, select: Boolean)

    fun cursorRows(steps: Int, select: Boolean)

    fun cursorEnd()

    fun sample(key: Key, x: Float, y: Float)
}

class TouchTracker(private val sink: TouchSink, var params: TouchParams) {
    private enum class Mode { PENDING, MODIFIER, REPEAT, HOLD, CURSOR, CURSOR_ROWS, DONE }

    private class Pointer(val id: Int, val key: Key, val x0: Float, val y0: Float, val t0: Long) {
        var x = x0
        var y = y0
        var mode = Mode.PENDING
        var deadline = Long.MAX_VALUE
        var gesture = Gesture.TAP
        var anchor = x0
        var anchorY = y0
        var repeatOnHold = false
        var select = false
        var sx = x0
        var sy = y0
        var used = false
    }

    private val pointers = LinkedHashMap<Int, Pointer>()

    val activePointers: Int get() = pointers.size

    fun down(id: Int, x: Float, y: Float, t: Long) {
        pointers.remove(id)?.let { finish(it, t) }
        val key = sink.hit(x, y, t) ?: return
        val def = key.def
        if (def.isModifier) releasePending(t, pointers.values.toList()) else quiet()
        val p = Pointer(id, key, x, y, t)
        pointers[id] = p
        sink.keyDown(id, key, t)
        when {
            def.isModifier -> {
                p.mode = Mode.MODIFIER
                sink.modifierDown(key, t)
            }
            def.repeat && !def.hasVariants -> {
                releasePending(t, pointers.values.filter { it !== p })
                fire(p, Gesture.TAP, t)
                p.mode = Mode.REPEAT
                p.deadline = t + params.repeatDelayMs
            }
            def.repeat -> {
                p.repeatOnHold = true
                p.deadline = t + params.repeatDelayMs
                sink.variant(id, key, Gesture.TAP)
            }
            else -> {
                if (def.isSpace) {
                    p.deadline = t + params.selectHoldMs
                } else if (params.longPressMs > 0 && def.hasVariants) {
                    p.deadline = t + params.longPressMs
                }
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
                    startCursor(p, t)
                    return
                }
                val g = classify(x - p.x0, y - p.y0, p.key)
                if (g != p.gesture) {
                    p.gesture = g
                    if (g != Gesture.TAP) p.deadline = Long.MAX_VALUE
                    sink.variant(id, p.key, g)
                }
            }
            Mode.HOLD -> startCursor(p, t)
            Mode.CURSOR -> stepCursor(p)
            Mode.CURSOR_ROWS -> stepRows(p)
            else -> Unit
        }
    }

    fun up(id: Int, x: Float, y: Float, t: Long) {
        val p = pointers[id] ?: return
        p.x = x
        p.y = y
        releasePending(t, pointers.values.takeWhile { it !== p })
        if (p.mode == Mode.MODIFIER) {
            val newer = pointers.values.dropWhile { it !== p }.drop(1).filter { it.mode == Mode.PENDING }
            if (newer.isNotEmpty() && (p.used || newer.first().t0 - p.t0 > params.rollMs)) releasePending(t, newer)
        }
        pointers.remove(id)
        finish(p, t)
    }

    fun cancel(id: Int, t: Long) {
        val p = pointers.remove(id) ?: return
        when (p.mode) {
            Mode.MODIFIER -> sink.modifierCancel(p.key, t)
            Mode.CURSOR, Mode.CURSOR_ROWS -> sink.cursorEnd()
            else -> Unit
        }
        sink.released(p.id)
    }

    fun cancelAll(t: Long) {
        for (id in pointers.keys.toList()) cancel(id, t)
    }

    fun flushPending(t: Long) = releasePending(t, pointers.values.toList())

    fun nextDeadline(): Long? = pointers.values.minOfOrNull { it.deadline }?.takeIf { it != Long.MAX_VALUE }

    fun tick(t: Long) {
        for (p in pointers.values.toList()) {
            if (p.deadline > t) continue
            when (p.mode) {
                Mode.REPEAT -> {
                    fire(p, Gesture.REPEAT, t)
                    p.deadline = t + params.repeatIntervalMs
                }
                Mode.PENDING -> {
                    releasePending(t, pointers.values.takeWhile { it !== p })
                    when {
                        p.key.def.isSpace -> {
                            p.deadline = Long.MAX_VALUE
                            p.mode = Mode.HOLD
                            p.select = true
                            p.sx = p.x
                            p.sy = p.y
                            sink.variant(p.id, p.key, Gesture.LONG)
                        }
                        p.repeatOnHold || params.longPressRepeats -> {
                            fire(p, Gesture.TAP, t)
                            p.mode = Mode.REPEAT
                            p.deadline = t + params.repeatIntervalMs
                        }
                        else -> {
                            p.deadline = Long.MAX_VALUE
                            fire(p, Gesture.LONG, t)
                            sink.variant(p.id, p.key, Gesture.LONG)
                            p.mode = Mode.DONE
                        }
                    }
                }
                else -> p.deadline = Long.MAX_VALUE
            }
        }
    }

    private fun quiet() {
        for (other in pointers.values) {
            when (other.mode) {
                Mode.REPEAT -> {
                    other.mode = Mode.DONE
                    other.deadline = Long.MAX_VALUE
                }
                Mode.PENDING -> other.deadline = Long.MAX_VALUE
                else -> Unit
            }
        }
    }

    private fun markModifiersUsed() {
        for (other in pointers.values) if (other.mode == Mode.MODIFIER) other.used = true
    }

    private fun fire(p: Pointer, g: Gesture, t: Long) {
        markModifiersUsed()
        sink.fire(p.key, g, t)
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
            Mode.CURSOR, Mode.CURSOR_ROWS -> sink.cursorEnd()
            Mode.REPEAT, Mode.HOLD, Mode.DONE -> Unit
        }
        sink.released(p.id)
    }

    private fun commit(p: Pointer, t: Long) {
        val g = if (p.key.def.isSpace) Gesture.TAP else classify(p.x - p.x0, p.y - p.y0, p.key)
        fire(p, g, t)
        if (g == Gesture.TAP) sink.sample(p.key, p.x0, p.y0)
    }

    private fun startCursor(p: Pointer, t: Long) {
        val dx = p.x - p.sx
        val dy = p.y - p.sy
        val start = params.cursorStartPx
        if (abs(dy) >= start && abs(dy) >= abs(dx) * params.verticalDominance) {
            releasePending(t, pointers.values.takeWhile { it !== p })
            p.mode = Mode.CURSOR_ROWS
            p.deadline = Long.MAX_VALUE
            p.anchorY = p.sy + if (dy > 0) start else -start
            stepRows(p)
        } else if (abs(dx) >= start) {
            releasePending(t, pointers.values.takeWhile { it !== p })
            p.mode = Mode.CURSOR
            p.deadline = Long.MAX_VALUE
            p.anchor = p.sx + if (dx > 0) start else -start
            stepCursor(p)
        }
    }

    private fun stepCursor(p: Pointer) {
        val steps = ((p.x - p.anchor) / params.cursorStepPx).toInt()
        if (steps != 0) {
            p.anchor += steps * params.cursorStepPx
            markModifiersUsed()
            sink.cursor(steps, p.select)
        }
    }

    private fun stepRows(p: Pointer) {
        val steps = ((p.y - p.anchorY) / params.cursorRowStepPx).toInt()
        if (steps != 0) {
            p.anchorY += steps * params.cursorRowStepPx
            markModifiersUsed()
            sink.cursorRows(steps, p.select)
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
