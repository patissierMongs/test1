package io.github.patissiermongs.foldkey.layout

import android.view.KeyEvent
import io.github.patissiermongs.foldkey.engine.Gesture
import io.github.patissiermongs.foldkey.engine.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionResolverTest {
    private val letter = KeyDef(KeyAction.Char('c', 'C'))
    private val digit = KeyDef(
        KeyAction.Char('1', '!'),
        down = KeyAction.Code(KeyEvent.KEYCODE_F1),
        downLabel = "F1",
    )

    @Test
    fun letterGestures() {
        assertEquals(Resolved(letter.action), ActionResolver.resolve(letter, Gesture.TAP, false, true))
        assertEquals(Resolved(letter.action, forceShift = true), ActionResolver.resolve(letter, Gesture.UP, false, true))
        assertEquals(Resolved(letter.action, forceShift = true), ActionResolver.resolve(letter, Gesture.LONG, false, true))
        assertEquals(Resolved(letter.action, forceCtrl = true), ActionResolver.resolve(letter, Gesture.DOWN, false, true))
        assertEquals(Resolved(letter.action), ActionResolver.resolve(letter, Gesture.DOWN, false, false))
        assertEquals(Resolved(letter.action), ActionResolver.resolve(letter, Gesture.TAP, true, true))
    }

    @Test
    fun explicitVariantsWin() {
        assertEquals(Resolved(KeyAction.Code(KeyEvent.KEYCODE_F1)), ActionResolver.resolve(digit, Gesture.DOWN, false, true))
        assertEquals(Resolved(digit.action, forceShift = true), ActionResolver.resolve(digit, Gesture.UP, false, true))
        assertEquals(Resolved(digit.action), ActionResolver.resolve(digit, Gesture.TAP, true, true))
    }

    @Test
    fun numpadKeysTypeTheirTextAndBackspaceSwipesUpToDelete() {
        val seven = Layouts.pad[0][0]
        for (g in Gesture.entries) assertEquals(Resolved(KeyAction.Text("7")), ActionResolver.resolve(seven, g, true, true))
        val backspace = Layouts.pad[0][4]
        assertEquals(Resolved(KeyAction.Backspace), ActionResolver.resolve(backspace, Gesture.TAP, true, true))
        assertEquals(Resolved(KeyAction.Backspace), ActionResolver.resolve(backspace, Gesture.REPEAT, true, true))
        assertEquals(Resolved(KeyAction.Code(KeyEvent.KEYCODE_FORWARD_DEL)), ActionResolver.resolve(backspace, Gesture.UP, true, true))
    }

    @Test
    fun coverScreenNavigationKeysUseFnForTapAndRepeatOnly() {
        val h = Layouts.compact.flatMap { it.keys }.first { (it.action as? KeyAction.Char)?.base == 'h' }
        val left = KeyAction.Code(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals(Resolved(left), ActionResolver.resolve(h, Gesture.TAP, true, true))
        assertEquals(Resolved(left), ActionResolver.resolve(h, Gesture.REPEAT, true, true))
        assertEquals(Resolved(h.action, forceShift = true), ActionResolver.resolve(h, Gesture.UP, true, true))
        assertEquals(Resolved(h.action), ActionResolver.resolve(h, Gesture.TAP, false, true))
        val backspace = Layouts.compact.flatMap { it.keys }.first { it.action == KeyAction.Backspace }
        assertEquals(Resolved(KeyAction.Code(KeyEvent.KEYCODE_FORWARD_DEL)), ActionResolver.resolve(backspace, Gesture.TAP, true, true))
    }
}
