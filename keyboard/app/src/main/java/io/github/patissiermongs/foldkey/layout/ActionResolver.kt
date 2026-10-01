package io.github.patissiermongs.foldkey.layout

import io.github.patissiermongs.foldkey.engine.Gesture
import io.github.patissiermongs.foldkey.engine.KeyAction

data class Resolved(val action: KeyAction, val forceShift: Boolean = false, val forceCtrl: Boolean = false)

object ActionResolver {
    fun resolve(def: KeyDef, gesture: Gesture, fnActive: Boolean, swipeDownCtrl: Boolean): Resolved {
        if (fnActive) {
            val fn = def.fn
            if (fn != null && (gesture == Gesture.TAP || gesture == Gesture.REPEAT)) return Resolved(fn)
            val fnUp = def.fnUp
            if (fnUp != null && (gesture == Gesture.UP || gesture == Gesture.LONG)) return Resolved(fnUp)
        }
        val action = def.action
        return when (gesture) {
            Gesture.TAP, Gesture.REPEAT -> Resolved(action)
            Gesture.UP, Gesture.LONG -> def.up?.let { Resolved(it) }
                ?: Resolved(action, forceShift = action is KeyAction.Char)
            Gesture.DOWN -> def.down?.let { Resolved(it) }
                ?: Resolved(action, forceCtrl = action is KeyAction.Char && swipeDownCtrl)
        }
    }
}
