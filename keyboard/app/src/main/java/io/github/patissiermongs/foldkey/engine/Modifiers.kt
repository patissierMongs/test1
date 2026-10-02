package io.github.patissiermongs.foldkey.engine

import android.view.KeyEvent
import java.util.EnumMap
import java.util.EnumSet

enum class ModState { OFF, ONESHOT, LOCKED }

class Modifiers(var doubleTapMs: Long = 350L) {
    private val states = EnumMap<Modifier, ModState>(Modifier::class.java)
    private val held = EnumMap<Modifier, Int>(Modifier::class.java)
    private val used = EnumSet.noneOf(Modifier::class.java)
    private val lastTap = EnumMap<Modifier, Long>(Modifier::class.java)
    private val disarmedAt = EnumMap<Modifier, Long>(Modifier::class.java)

    init {
        clear()
    }

    fun clear() {
        for (m in Modifier.entries) {
            states[m] = ModState.OFF
            held[m] = 0
        }
        used.clear()
        lastTap.clear()
        disarmedAt.clear()
    }

    fun state(m: Modifier): ModState = states.getValue(m)

    fun isHeld(m: Modifier): Boolean = held.getValue(m) > 0

    fun isActive(m: Modifier): Boolean = isHeld(m) || states.getValue(m) != ModState.OFF

    fun isLocked(m: Modifier): Boolean = states.getValue(m) == ModState.LOCKED

    fun press(m: Modifier) {
        held[m] = held.getValue(m) + 1
        used.remove(m)
    }

    fun release(m: Modifier, now: Long, toggle: Boolean = true): Boolean {
        val count = held.getValue(m)
        if (count == 0) return false
        held[m] = count - 1
        if (count > 1) return false
        if (used.remove(m)) {
            if (states.getValue(m) == ModState.ONESHOT) states[m] = ModState.OFF
            return false
        }
        if (toggle) {
            val previousTap = lastTap[m]
            val disarmed = disarmedAt.remove(m)
            states[m] = when (states.getValue(m)) {
                ModState.OFF ->
                    if (m.latches || (disarmed != null && now - disarmed <= doubleTapMs)) ModState.LOCKED else ModState.ONESHOT
                ModState.ONESHOT ->
                    if (previousTap != null && now - previousTap <= doubleTapMs) {
                        ModState.LOCKED
                    } else {
                        disarmedAt[m] = now
                        ModState.OFF
                    }
                ModState.LOCKED -> ModState.OFF
            }
            lastTap[m] = now
        }
        return true
    }

    fun cancel(m: Modifier) {
        val count = held.getValue(m)
        if (count == 0) return
        held[m] = count - 1
        if (count == 1) used.remove(m)
    }

    fun markHeldUsed() {
        for (m in Modifier.entries) if (isHeld(m)) used.add(m)
    }

    fun consume() {
        markHeldUsed()
        for (m in Modifier.entries) if (states.getValue(m) == ModState.ONESHOT) states[m] = ModState.OFF
        disarmedAt.clear()
    }

    fun oneShots(): Set<Modifier> = Modifier.entries.filterTo(EnumSet.noneOf(Modifier::class.java)) { states.getValue(it) == ModState.ONESHOT }

    fun arm(oneShots: Set<Modifier>) {
        for (m in oneShots) if (states.getValue(m) == ModState.OFF) states[m] = ModState.ONESHOT
    }

    fun shiftForSymbols(): Boolean = isHeld(Modifier.SHIFT) || state(Modifier.SHIFT) == ModState.ONESHOT

    fun shiftForLetters(): Boolean = isActive(Modifier.SHIFT)

    fun hasCommandModifier(): Boolean = isActive(Modifier.CTRL) || isActive(Modifier.ALT)

    fun metaState(includeShift: Boolean): Int {
        var meta = 0
        if (includeShift) meta = meta or SHIFT_META
        if (isActive(Modifier.CTRL)) meta = meta or CTRL_META
        if (isActive(Modifier.ALT)) meta = meta or ALT_META
        return meta
    }

    companion object {
        const val SHIFT_META = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        const val CTRL_META = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        const val ALT_META = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
    }
}
