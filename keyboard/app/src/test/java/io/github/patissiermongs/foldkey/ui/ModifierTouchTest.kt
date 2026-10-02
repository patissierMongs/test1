package io.github.patissiermongs.foldkey.ui

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.patissiermongs.foldkey.engine.Command
import io.github.patissiermongs.foldkey.engine.EditorContext
import io.github.patissiermongs.foldkey.engine.FakeEditor
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.ModState
import io.github.patissiermongs.foldkey.engine.Modifier
import io.github.patissiermongs.foldkey.engine.Modifiers
import io.github.patissiermongs.foldkey.engine.RecordingListener
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.layout.Box
import io.github.patissiermongs.foldkey.layout.Key
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w750dp-h832dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModifierTouchTest {
    private object Silent : Feedback {
        override fun press(view: View, key: Key) = Unit
        override fun detent(view: View) = Unit
    }

    private lateinit var editor: FakeEditor
    private lateinit var engine: KeyboardEngine
    private lateinit var view: KeyboardView
    private lateinit var prefs: Prefs
    private val fingers = LinkedHashMap<Int, Pair<Float, Float>>()
    private var downTime = 0L

    private fun setup(raw: Boolean = false, clips: List<Clip> = emptyList(), feedback: (Activity, Prefs) -> Feedback = { _, _ -> Silent }) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dm = activity.resources.displayMetrics
        dm.xdpi = 368f
        dm.ydpi = 368f
        prefs = Prefs(activity)
        prefs.sp.edit().clear().putBoolean(Prefs.SPLIT_PORTRAIT, true).putBoolean(Prefs.SPLIT_LANDSCAPE, true).commit()
        editor = FakeEditor()
        engine = KeyboardEngine(editor, RecordingListener())
        engine.startInput(EditorContext(raw = raw, preferLatin = true))
        view = KeyboardView(activity, engine, prefs, feedback(activity, prefs))
        view.clipSource = { clips }
        activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(view.layoutKeys.isNotEmpty())
    }

    private fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun send(action: Int, index: Int, flags: Int = 0) {
        val t = SystemClock.uptimeMillis()
        val ids = fingers.keys.toList()
        val props = ids.map { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coords = ids.map { id ->
            MotionEvent.PointerCoords().apply { x = fingers.getValue(id).first; y = fingers.getValue(id).second; pressure = 1f; size = 0.1f }
        }.toTypedArray()
        val masked = if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        } else {
            action
        }
        val e = MotionEvent.obtain(downTime, t, masked, ids.size, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, flags)
        view.onTouchEvent(e)
        e.recycle()
    }

    private fun down(id: Int, b: Box) {
        if (fingers.isEmpty()) downTime = SystemClock.uptimeMillis()
        fingers[id] = b.centerX to b.centerY
        send(if (fingers.size == 1) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_POINTER_DOWN, fingers.keys.indexOf(id))
    }

    private fun up(id: Int, flags: Int = 0) {
        send(if (fingers.size == 1) MotionEvent.ACTION_UP else MotionEvent.ACTION_POINTER_UP, fingers.keys.indexOf(id), flags)
        fingers.remove(id)
    }

    private fun cancelAll() {
        send(MotionEvent.ACTION_CANCEL, 0)
        fingers.clear()
    }

    private fun tap(b: Box) {
        down(9, b)
        advance(40)
        up(9)
        advance(60)
    }

    private fun key(pred: (Key) -> Boolean): Box = view.layoutKeys.filter { !it.ghost && pred(it) }.minBy { it.face.left }.face

    private fun letter(c: Char) = key { (it.def.action as? KeyAction.Char)?.base == c }

    private fun escCtrl() = key { it.def.action == KeyAction.EscCtrl }

    private fun mod(m: Modifier) = key { it.def.action == KeyAction.Mod(m) }

    private fun pad(label: String): Box = view.fnLayoutKeys.first { it.pad && it.def.label == label }.face

    private fun output() = editor.log.filter { it.startsWith("commit:") || it.startsWith("key:") || it.startsWith("paste:") }

    @Test
    fun escCtrlRolledIntoTheNextKeySendsEscapeFirst() {
        setup(raw = true)
        down(0, escCtrl())
        advance(50)
        down(1, letter('l'))
        advance(40)
        up(0)
        advance(40)
        up(1)
        assertEquals(listOf("key:${KeyEvent.keyCodeToString(KeyEvent.KEYCODE_ESCAPE)}:0", "commit:l"), output())
    }

    @Test
    fun escCtrlHeldBeforeTheLetterIsCtrlEvenWhenReleasedFirst() {
        setup(raw = true)
        down(0, escCtrl())
        advance(200)
        down(1, letter('c'))
        advance(100)
        up(0)
        advance(40)
        up(1)
        assertEquals(listOf(KeyEvent.KEYCODE_C to Modifiers.CTRL_META), editor.keys)
        assertEquals("", editor.text.toString())
    }

    @Test
    fun heldCtrlAppliesToEveryKeyPressedDuringTheHold() {
        setup(raw = true)
        down(0, escCtrl())
        advance(200)
        down(1, letter('l'))
        advance(60)
        up(1)
        advance(60)
        down(1, letter('u'))
        advance(40)
        up(0)
        advance(40)
        up(1)
        assertEquals(listOf(KeyEvent.KEYCODE_L to Modifiers.CTRL_META, KeyEvent.KEYCODE_U to Modifiers.CTRL_META), editor.keys)
        assertEquals("", editor.text.toString())
    }

    @Test
    fun heldShiftAppliesToEveryKeyPressedDuringTheHold() {
        setup()
        down(0, mod(Modifier.SHIFT))
        advance(30)
        down(1, letter('h'))
        advance(60)
        up(1)
        advance(30)
        down(1, letter('i'))
        advance(30)
        up(0)
        advance(30)
        up(1)
        assertEquals("HI", editor.text.toString())
        assertEquals(ModState.OFF, engine.modifiers.state(Modifier.SHIFT))
    }

    @Test
    fun fnHeldForAPadKeyClosesTheNumpad() {
        setup()
        down(0, mod(Modifier.FN))
        advance(200)
        down(1, pad("5"))
        advance(100)
        up(0)
        advance(40)
        up(1)
        assertEquals("5", editor.text.toString())
        assertFalse(engine.modifiers.isActive(Modifier.FN))
    }

    @Test
    fun fnRolledIntoAPadKeyKeepsTheNumpad() {
        setup()
        down(0, mod(Modifier.FN))
        advance(50)
        down(1, pad("5"))
        advance(40)
        up(0)
        advance(40)
        up(1)
        assertEquals("5", editor.text.toString())
        assertTrue(engine.modifiers.isActive(Modifier.FN))
    }

    @Test
    fun canceledModifierTouchesDoNothing() {
        setup(raw = true)
        down(0, escCtrl())
        advance(60)
        cancelAll()
        down(0, letter('k'))
        advance(30)
        down(1, escCtrl())
        advance(30)
        up(1, MotionEvent.FLAG_CANCELED)
        advance(30)
        up(0)
        for (m in listOf(Modifier.SHIFT, Modifier.CTRL, Modifier.FN)) {
            down(0, mod(m))
            advance(60)
            cancelAll()
        }
        tap(letter('d'))
        assertTrue("sent ${editor.keys}", editor.keys.isEmpty())
        assertEquals("kd", editor.text.toString())
        for (m in Modifier.entries) assertFalse(m.name, engine.modifiers.isActive(m))
    }

    @Test
    fun oneShotShiftCoversTheWholeAutoRepeat() {
        setup()
        tap(mod(Modifier.SHIFT))
        assertEquals(ModState.ONESHOT, engine.modifiers.state(Modifier.SHIFT))
        down(0, key { it.def.action == KeyAction.Code(KeyEvent.KEYCODE_DPAD_LEFT) })
        advance(600)
        up(0)
        assertTrue(editor.keys.size >= 3)
        assertEquals(List(editor.keys.size) { KeyEvent.KEYCODE_DPAD_LEFT to Modifiers.SHIFT_META }, editor.keys)
    }

    @Test
    fun stripCommandRunsAfterAKeyPressedBeforeIt() {
        setup()
        down(0, letter('a'))
        advance(30)
        down(1, view.stripBox(Command.PASTE)!!)
        advance(30)
        up(1)
        advance(30)
        up(0)
        assertEquals(listOf("commit:a", "paste:false"), output())
    }

    @Test
    @Config(qualifiers = "w832dp-h750dp-land-420dpi")
    fun clipIsPastedAfterAKeyPressedBeforeIt() {
        setup(clips = listOf(Clip("make test")))
        assertTrue(view.hasPanel)
        down(0, letter('a'))
        advance(30)
        down(1, view.clipSlots[0])
        advance(30)
        up(1)
        advance(30)
        up(0)
        assertEquals("amake test", editor.text.toString())
    }

    @Test
    fun stripButtonsFollowTheHapticSetting() {
        setup(feedback = { a, p -> HapticFeedback(a, p) })
        prefs.sp.edit().putBoolean(Prefs.HAPTIC, false).commit()
        tap(letter('a'))
        tap(view.stripBox(Command.PASTE)!!)
        assertEquals(-1, shadowOf(view).lastHapticFeedbackPerformed())
        prefs.sp.edit().putBoolean(Prefs.HAPTIC, true).commit()
        tap(view.stripBox(Command.PASTE)!!)
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, shadowOf(view).lastHapticFeedbackPerformed())
    }
}
