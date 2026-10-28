package io.github.patissiermongs.foldkey.ui

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.patissiermongs.foldkey.engine.EditorContext
import io.github.patissiermongs.foldkey.engine.FakeEditor
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.engine.RecordingListener
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.input.TapHeat
import io.github.patissiermongs.foldkey.layout.Box
import io.github.patissiermongs.foldkey.layout.Key
import io.github.patissiermongs.foldkey.layout.LayoutKind
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
class TapHeatRecordingTest {
    private object Silent : Feedback {
        override fun press(view: View, key: Key) = Unit
        override fun detent(view: View) = Unit
    }

    private lateinit var editor: FakeEditor
    private lateinit var engine: KeyboardEngine
    private lateinit var view: KeyboardView
    private lateinit var prefs: Prefs
    private val pxPerMm = 368f / 25.4f

    private fun setup(adaptive: Boolean = true) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dm = activity.resources.displayMetrics
        dm.xdpi = 368f
        dm.ydpi = 368f
        prefs = Prefs(activity)
        prefs.sp.edit().clear().putBoolean(Prefs.SPLIT_PORTRAIT, true).putBoolean(Prefs.ADAPTIVE, adaptive).commit()
        editor = FakeEditor()
        engine = KeyboardEngine(editor, RecordingListener())
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

    private fun tap(b: Box, dxMm: Float = 0f, dyMm: Float = 0f) {
        touch(MotionEvent.ACTION_DOWN, b.centerX + dxMm * pxPerMm, b.centerY + dyMm * pxPerMm)
        touch(MotionEvent.ACTION_UP, b.centerX + dxMm * pxPerMm, b.centerY + dyMm * pxPerMm)
    }

    private fun letter(c: Char): Box = view.layoutKeys.filter { !it.ghost && (it.def.action as? KeyAction.Char)?.base == c }.minBy { it.face.left }.face

    private val slot: String get() = KeyboardView.offsetSlot(LayoutKind.SPLIT, Layer.CODE, view.width.toFloat(), pxPerMm)

    private fun stored(): TapHeat = TapHeat.parse(prefs.heat(slot), prefs.layoutSignature)

    @Test
    fun tapsAreGatheredPerKeyAndSavedWithTheLayout() {
        setup()
        repeat(12) { tap(letter('f'), 1f, 0.5f) }
        repeat(10) { tap(letter('j'), -0.8f) }
        view.saveState()
        val heat = stored()
        assertEquals(12, heat.cloud('f')!!.n)
        assertEquals(1f, heat.cloud('f')!!.meanXMm, 0.02f)
        assertEquals(0.5f, heat.cloud('f')!!.meanYMm, 0.02f)
        assertEquals(10, heat.cloud('j')!!.n)
        assertEquals(-0.8f, heat.cloud('j')!!.meanXMm, 0.02f)
        assertEquals("f".repeat(12) + "j".repeat(10), editor.text.toString())
        assertEquals(setOf('f', 'j'), heat.keys)
    }

    @Test
    fun learningOffTurnsOffTheCorrectionButStillGathersTheHeatmap() {
        setup(adaptive = false)
        repeat(9) { tap(letter('d'), 0.6f) }
        view.saveState()
        assertEquals(9, stored().cloud('d')!!.n)
        assertNull(prefs.offsets(slot))
    }

    @Test
    fun passwordFieldsAreNotGathered() {
        setup()
        engine.startInput(EditorContext(preferLatin = true, secret = true))
        repeat(9) { tap(letter('s')) }
        view.saveState()
        assertEquals(0, stored().taps)
        engine.startInput(EditorContext(preferLatin = true))
        repeat(9) { tap(letter('s')) }
        view.saveState()
        assertEquals(9, stored().taps)
    }

    @Test
    fun changedKeySizeOrPlaceStartsANewHeatmapAndSettingsCanClearIt() {
        setup()
        repeat(10) { tap(letter('f')) }
        view.saveState()
        assertEquals(10, stored().taps)
        prefs.sp.edit().putInt(Prefs.CODE_SIDE_LEFT, 40).commit()
        view.reload()
        repeat(4) { tap(letter('f')) }
        view.saveState()
        assertEquals(4, stored().taps)
        repeat(3) { tap(letter('f')) }
        prefs.replaceTouchData(emptyMap(), listOf(slot))
        repeat(2) { tap(letter('f')) }
        view.saveState()
        assertEquals(2, stored().taps)
    }
}
