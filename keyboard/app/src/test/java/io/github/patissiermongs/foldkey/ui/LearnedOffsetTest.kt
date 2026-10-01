package io.github.patissiermongs.foldkey.ui

import android.app.Activity
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
import io.github.patissiermongs.foldkey.engine.RecordingListener
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.layout.Box
import io.github.patissiermongs.foldkey.layout.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class LearnedOffsetTest {
    private object Silent : Feedback {
        override fun press(view: View, key: Key) = Unit
        override fun detent(view: View) = Unit
    }

    private lateinit var editor: FakeEditor
    private lateinit var view: KeyboardView
    private lateinit var prefs: Prefs
    private val pxPerMm = 368f / 25.4f

    private fun setup() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dm = activity.resources.displayMetrics
        dm.xdpi = 368f
        dm.ydpi = 368f
        prefs = Prefs(activity)
        prefs.sp.edit().clear().putBoolean(Prefs.SPLIT_PORTRAIT, true).commit()
        editor = FakeEditor()
        val engine = KeyboardEngine(editor, RecordingListener())
        engine.startInput(EditorContext(preferLatin = true))
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

    private fun tapAt(x: Float, y: Float) {
        touch(MotionEvent.ACTION_DOWN, x, y)
        touch(MotionEvent.ACTION_UP, x, y)
    }

    private fun tap(b: Box, dxMm: Float = 0f, dyMm: Float = 0f) = tapAt(b.centerX + dxMm * pxPerMm, b.centerY + dyMm * pxPerMm)

    private fun key(pred: (Key) -> Boolean): Box = view.layoutKeys.filter { !it.ghost && pred(it) }.minBy { it.face.left }.face

    private fun letter(c: Char) = key { (it.def.action as? KeyAction.Char)?.base == c }

    private fun stored(): List<Int> {
        val data = prefs.sp.all.entries.firstOrNull { it.key.startsWith(Prefs.OFFSETS_PREFIX) }?.value as? String ?: return emptyList()
        return data.split(";").map { it.split(",")[2].toInt() }
    }

    @Test
    fun learnedOffsetNeverMovesATouchOutOfAKeyCentre() {
        setup()
        repeat(16) {
            tap(letter(';'), dyMm = 3f)
            tap(letter('\''), dyMm = 3f)
        }
        assertEquals(";'".repeat(16), editor.text.toString())
        val enter = key { it.def.action == KeyAction.Enter }
        assertTrue(2f * pxPerMm <= enter.height * 0.25f)
        tapAt(enter.centerX, enter.centerY - 2f * pxPerMm)
        assertEquals(KeyEvent.KEYCODE_ENTER to 0, editor.keys.lastOrNull())
    }

    @Test
    fun learnedOffsetNeverTurnsAVisibleKeyIntoAGhost() {
        setup()
        repeat(25) {
            tap(letter('d'), dxMm = -1.5f)
            tap(letter('f'), dxMm = -1.5f)
        }
        val g = letter('g')
        val x = g.centerX + 3f * pxPerMm
        assertTrue(x < g.right)
        val before = editor.text.length
        tapAt(x, g.centerY)
        assertEquals("g", editor.text.substring(before))
    }

    @Test
    fun tapsUndoneWithBackspaceAreNotLearned() {
        setup()
        val bs = key { it.def.action == KeyAction.Backspace }
        tap(letter('a'))
        tap(letter('b'))
        tap(bs)
        tap(bs)
        assertEquals("", editor.text.toString())
        view.saveState()
        assertEquals(0, stored().sum())
        tap(letter('a'))
        tap(letter('s'))
        tap(letter('x'))
        tap(bs)
        view.saveState()
        assertEquals(2, stored().sum())
    }

    @Test
    fun resetInSettingsAlsoResetsTheRunningKeyboard() {
        setup()
        repeat(30) { tap(letter('f')) }
        view.saveState()
        assertEquals(30, stored().sum())
        prefs.clearOffsets()
        assertNull(prefs.sp.all.keys.firstOrNull { it.startsWith(Prefs.OFFSETS_PREFIX) })
        tap(letter('f'))
        view.saveState()
        assertEquals(1, stored().sum())
    }
}
