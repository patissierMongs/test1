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
        fn = KeyAction.Code(KeyEvent.KEYCODE_F1),
    )
    private val backspace = KeyDef(KeyAction.Backspace, repeat = true, fn = KeyAction.Code(KeyEvent.KEYCODE_FORWARD_DEL))

    @Test
    fun letterGestures() {
        assertEquals(Resolved(letter.action), ActionResolver.resolve(letter, Gesture.TAP, false, true))
        assertEquals(Resolved(letter.action, forceShift = true), ActionResolver.resolve(letter, Gesture.UP, false, true))
        assertEquals(Resolved(letter.action, forceShift = true), ActionResolver.resolve(letter, Gesture.LONG, false, true))
        assertEquals(Resolved(letter.action, forceCtrl = true), ActionResolver.resolve(letter, Gesture.DOWN, false, true))
        assertEquals(Resolved(letter.action), ActionResolver.resolve(letter, Gesture.DOWN, false, false))
    }

    @Test
    fun explicitVariantsWin() {
        assertEquals(Resolved(KeyAction.Code(KeyEvent.KEYCODE_F1)), ActionResolver.resolve(digit, Gesture.DOWN, false, true))
        assertEquals(Resolved(digit.action, forceShift = true), ActionResolver.resolve(digit, Gesture.UP, false, true))
    }

    @Test
    fun fnSwipeUpGivesSecondSymbolAndSwipeDownStaysCtrl() {
        val comma = KeyDef(
            KeyAction.Char(',', '<'),
            fn = KeyAction.Text("·"),
            fnUp = KeyAction.Text("≤"),
        )
        assertEquals(Resolved(KeyAction.Text("·")), ActionResolver.resolve(comma, Gesture.TAP, true, true))
        assertEquals(Resolved(KeyAction.Text("≤")), ActionResolver.resolve(comma, Gesture.UP, true, true))
        assertEquals(Resolved(KeyAction.Text("≤")), ActionResolver.resolve(comma, Gesture.LONG, true, true))
        assertEquals(Resolved(comma.action, forceCtrl = true), ActionResolver.resolve(comma, Gesture.DOWN, true, true))
        assertEquals(Resolved(comma.action, forceShift = true), ActionResolver.resolve(comma, Gesture.UP, false, true))
        assertEquals(Resolved(digit.action, forceShift = true), ActionResolver.resolve(digit, Gesture.UP, true, true))
    }

    @Test
    fun fnLayerAppliesToTapAndRepeat() {
        assertEquals(Resolved(KeyAction.Code(KeyEvent.KEYCODE_F1)), ActionResolver.resolve(digit, Gesture.TAP, true, true))
        assertEquals(Resolved(KeyAction.Code(KeyEvent.KEYCODE_FORWARD_DEL)), ActionResolver.resolve(backspace, Gesture.REPEAT, true, true))
        assertEquals(Resolved(KeyAction.Backspace), ActionResolver.resolve(backspace, Gesture.TAP, false, true))
        assertEquals(Resolved(letter.action), ActionResolver.resolve(letter, Gesture.TAP, true, true))
    }
}
