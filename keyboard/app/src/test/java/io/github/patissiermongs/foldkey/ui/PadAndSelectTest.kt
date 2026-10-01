package io.github.patissiermongs.foldkey.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.patissiermongs.foldkey.engine.EditorContext
import io.github.patissiermongs.foldkey.engine.FakeEditor
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.Modifier
import io.github.patissiermongs.foldkey.engine.Modifiers
import io.github.patissiermongs.foldkey.engine.RecordingListener
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.layout.Box
import io.github.patissiermongs.foldkey.layout.Key
import java.io.File
import java.io.FileOutputStream
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
class PadAndSelectTest {
    private object Silent : Feedback {
        override fun press(view: View, key: Key) = Unit
        override fun detent(view: View) = Unit
    }

    private lateinit var editor: FakeEditor
    private lateinit var engine: KeyboardEngine
    private lateinit var view: KeyboardView
    private var pxPerMm = 0f

    private fun setup(raw: Boolean = false, coverScreen: Boolean = false) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dm = activity.resources.displayMetrics
        val ppi = if (coverScreen) 422f else 368f
        dm.xdpi = ppi
        dm.ydpi = ppi
        pxPerMm = ppi / 25.4f
        val prefs = Prefs(activity)
        prefs.sp.edit().clear().putBoolean(Prefs.SPLIT_PORTRAIT, true).commit()
        editor = FakeEditor()
        engine = KeyboardEngine(editor, RecordingListener())
        engine.startInput(EditorContext(raw = raw, preferLatin = raw))
        view = KeyboardView(activity, engine, prefs, Silent)
        activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(view.layoutKeys.isNotEmpty())
    }

    private fun touch(action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, action, x, y, 0)
        view.onTouchEvent(e)
        e.recycle()
    }

    private fun tap(box: Box) {
        touch(MotionEvent.ACTION_DOWN, box.centerX, box.centerY)
        touch(MotionEvent.ACTION_UP, box.centerX, box.centerY)
    }

    private fun fnKey(): Box = view.layoutKeys.first { it.def.action == KeyAction.Mod(Modifier.FN) }.face

    private fun pad(label: String): Box = view.fnLayoutKeys.first { it.pad && it.def.label == label }.face

    private fun save(name: String) {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/render").apply { mkdirs() }
        FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun fnTapSwitchesToTheNumpadUntilFnIsTappedAgain() {
        setup()
        tap(fnKey())
        assertTrue(engine.modifiers.isLocked(Modifier.FN))
        save("split_portrait_fn_locked.png")
        for (label in listOf("7", "4", "+", "1", "⌫", "(", "0", ".", "5", ")")) tap(pad(label))
        assertEquals("74+(0.5)", editor.text.toString())
        tap(pad("⏎"))
        assertEquals(KeyEvent.KEYCODE_ENTER, editor.keys.last().first)
        assertTrue(engine.modifiers.isLocked(Modifier.FN))
        tap(fnKey())
        assertFalse(engine.modifiers.isActive(Modifier.FN))
        tap(pad("1"))
        assertEquals("74+(0.5)l", editor.text.toString())
    }

    @Test
    fun numpadStaysThroughDigitsAndBackspaceUntilFnIsTappedAgain() {
        setup()
        tap(fnKey())
        for (label in listOf("7", "+", "4", "⌫", "(")) {
            tap(pad(label))
            assertTrue(engine.modifiers.isActive(Modifier.FN))
        }
        tap(fnKey())
        assertFalse(engine.modifiers.isActive(Modifier.FN))
        tap(pad("1"))
        assertEquals("7+(l", editor.text.toString())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-420dpi")
    fun coverScreenGetsTheNumpadOnTheRightHalf() {
        setup(coverScreen = true)
        assertTrue(view.fnLayoutKeys.filter { it.pad }.all { it.face.left >= view.width / 2f - 1f })
        tap(fnKey())
        save("cover_compact_fn_touch.png")
        tap(pad("9"))
        tap(pad("0"))
        assertEquals("90", editor.text.toString())
    }

    @Test
    fun heldFnShowsTheNumpadOnlyWhileHeld() {
        setup()
        tap(fnKey())
        tap(pad("4"))
        tap(fnKey())
        tap(pad("1"))
        assertEquals("4l", editor.text.toString())
        val fn = fnKey()
        touch(MotionEvent.ACTION_DOWN, fn.centerX, fn.centerY)
        val two = pad("2")
        val e = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0 }, MotionEvent.PointerProperties().apply { id = 1 }),
            arrayOf(MotionEvent.PointerCoords().apply { x = fn.centerX; y = fn.centerY }, MotionEvent.PointerCoords().apply { x = two.centerX; y = two.centerY }),
            0, 0, 1f, 1f, 0, 0, 0, 0)
        view.onTouchEvent(e)
        e.recycle()
        val up = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0 }, MotionEvent.PointerProperties().apply { id = 1 }),
            arrayOf(MotionEvent.PointerCoords().apply { x = fn.centerX; y = fn.centerY }, MotionEvent.PointerCoords().apply { x = two.centerX; y = two.centerY }),
            0, 0, 1f, 1f, 0, 0, 0, 0)
        view.onTouchEvent(up)
        up.recycle()
        assertTrue(engine.modifiers.isHeld(Modifier.FN))
        touch(MotionEvent.ACTION_UP, fn.centerX, fn.centerY)
        assertFalse(engine.modifiers.isActive(Modifier.FN))
        assertEquals("4l2", editor.text.toString())
    }

    private fun space(): Box = view.layoutKeys.filter { it.def.isSpace }.maxBy { it.face.left }.face

    private fun holdAndDrag(dxMm: Float) {
        val s = space()
        touch(MotionEvent.ACTION_DOWN, s.centerX, s.centerY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        save("split_portrait_space_select.png")
        var x = s.centerX
        repeat(10) {
            x += dxMm / 10f * pxPerMm
            touch(MotionEvent.ACTION_MOVE, x, s.centerY)
        }
        touch(MotionEvent.ACTION_UP, x, s.centerY)
    }

    @Test
    fun holdingSpaceThenDraggingLeftSelectsWithShiftArrows() {
        setup()
        holdAndDrag(-11.5f)
        assertEquals("", editor.text.toString())
        assertEquals(List(3) { KeyEvent.KEYCODE_DPAD_LEFT to Modifiers.SHIFT_META }, editor.keys)
    }

    @Test
    fun holdingSpaceInATerminalMovesTheCursorWithoutShift() {
        setup(raw = true)
        holdAndDrag(11.5f)
        assertEquals("", editor.text.toString())
        assertEquals(List(3) { KeyEvent.KEYCODE_DPAD_RIGHT to 0 }, editor.keys)
    }

    @Test
    fun quickSpaceDragStillMovesWithoutSelecting() {
        setup()
        val s = space()
        touch(MotionEvent.ACTION_DOWN, s.centerX, s.centerY)
        var x = s.centerX
        repeat(10) {
            x += 1.15f * pxPerMm
            touch(MotionEvent.ACTION_MOVE, x, s.centerY)
        }
        touch(MotionEvent.ACTION_UP, x, s.centerY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(List(3) { KeyEvent.KEYCODE_DPAD_RIGHT to 0 }, editor.keys)
        tap(s)
        assertEquals(" ", editor.text.toString())
    }
}
