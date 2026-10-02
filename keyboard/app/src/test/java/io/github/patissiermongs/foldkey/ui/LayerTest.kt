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
import io.github.patissiermongs.foldkey.layout.Box
import io.github.patissiermongs.foldkey.layout.Key
import io.github.patissiermongs.foldkey.layout.Layouts
import org.junit.Assert.assertEquals
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
class LayerTest {
    private object Silent : Feedback {
        override fun press(view: View, key: Key) = Unit
        override fun detent(view: View) = Unit
    }

    private lateinit var editor: FakeEditor
    private lateinit var engine: KeyboardEngine
    private lateinit var view: KeyboardView
    private lateinit var prefs: Prefs
    private var pxPerMm = 0f

    private fun setup(split: Boolean = true, ppi: Float = 368f) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dm = activity.resources.displayMetrics
        dm.xdpi = ppi
        dm.ydpi = ppi
        pxPerMm = ppi / 25.4f
        prefs = Prefs(activity)
        prefs.sp.edit().clear().putBoolean(Prefs.SPLIT_PORTRAIT, split).commit()
        editor = FakeEditor()
        engine = KeyboardEngine(editor, RecordingListener())
        engine.startInput(EditorContext())
        view = KeyboardView(activity, engine, prefs, Silent)
        activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        idle()
        assertTrue(view.layoutKeys.isNotEmpty())
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun touch(action: Int, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, action, x, y, 0)
        view.onTouchEvent(e)
        e.recycle()
    }

    private fun tap(b: Box) {
        touch(MotionEvent.ACTION_DOWN, b.centerX, b.centerY)
        touch(MotionEvent.ACTION_UP, b.centerX, b.centerY)
        idle()
    }

    private fun swipe(b: Box, dyMm: Float) {
        touch(MotionEvent.ACTION_DOWN, b.centerX, b.centerY)
        touch(MotionEvent.ACTION_MOVE, b.centerX, b.centerY + dyMm * pxPerMm)
        touch(MotionEvent.ACTION_UP, b.centerX, b.centerY + dyMm * pxPerMm)
        idle()
    }

    private fun key(pred: (Key) -> Boolean): Box = view.layoutKeys.filter { !it.ghost && pred(it) }.minBy { it.face.left }.face

    private fun char(c: Char) = key { (it.def.action as? KeyAction.Char)?.base == c }

    private fun layerKey() = key { it.def.action == Layouts.LAYER_TOGGLE }

    private fun hasCodeKeys() = view.layoutKeys.any { it.def.action == KeyAction.EscCtrl }

    @Test
    fun layerKeySwitchesTheVisibleLayoutAndBack() {
        setup()
        assertTrue(hasCodeKeys())
        val codeQ = char('q')
        tap(layerKey())
        assertEquals(Layer.GENERAL, engine.layer)
        assertTrue(!hasCodeKeys())
        val q = char('q')
        assertEquals(9.6f - 0.9f, q.width / pxPerMm, 0.05f)
        assertTrue(q.width > codeQ.width)
        tap(layerKey())
        assertEquals(Layer.CODE, engine.layer)
        assertTrue(hasCodeKeys())
        assertEquals(codeQ, char('q'))
    }

    @Test
    fun generalLayerTypesHangulAndProsePunctuation() {
        setup()
        tap(layerKey())
        tap(key { it.def.action == KeyAction.Lang })
        "dkssud".forEach { tap(char(it)) }
        tap(key { it.def.isSpace })
        swipe(char('.'), -6f)
        swipe(char('1'), 6f)
        tap(char(','))
        swipe(char(','), -6f)
        swipe(char('7'), 6f)
        tap(char('.'))
        assertEquals("안녕 ?~,!:.", editor.text.toString())
    }

    @Test
    fun learnedOffsetsAreKeptApartPerLayer() {
        setup()
        repeat(12) { tap(char('f')) }
        tap(layerKey())
        repeat(20) { tap(char('f')) }
        view.saveState()
        fun stored() = prefs.sp.all.filterKeys { it.startsWith(Prefs.OFFSETS_PREFIX) }
            .mapValues { (_, v) -> (v as String).split(";").sumOf { it.split(",")[2].toInt() } }
        val slots = stored()
        assertEquals(2, slots.size)
        assertEquals(20, slots.entries.single { it.key.contains(KeyboardView.GENERAL_SLOT_SUFFIX) }.value)
        assertEquals(12, slots.entries.single { it.key.contains(KeyboardView.SPLIT_SLOT_SUFFIX) }.value)
    }

    @Test
    @Config(qualifiers = "w411dp-h900dp-420dpi")
    fun coverScreenGeneralLayerIsOneRowShorter() {
        setup(split = false, ppi = 422f)
        assertTrue(view.layoutKeys.any { it.def.action == KeyAction.EscCtrl })
        val before = view.height
        val row = view.layoutKeys.first { (it.def.action as? KeyAction.Char)?.base == 'q' }.let { it.touch.height }
        tap(layerKey())
        assertEquals(before - row, view.height.toFloat(), 2f)
        assertEquals(5, view.layoutKeys.maxOf { it.row } + 1)
    }
}
