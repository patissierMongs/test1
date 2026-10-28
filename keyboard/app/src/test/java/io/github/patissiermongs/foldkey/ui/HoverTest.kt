package io.github.patissiermongs.foldkey.ui

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.patissiermongs.foldkey.engine.Command
import io.github.patissiermongs.foldkey.engine.EditorContext
import io.github.patissiermongs.foldkey.engine.FakeEditor
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.RecordingListener
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.layout.Box
import io.github.patissiermongs.foldkey.layout.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w832dp-h750dp-land-420dpi")
class HoverTest {
    private object Silent : Feedback {
        override fun press(view: View, key: Key) = Unit
        override fun detent(view: View) = Unit
    }

    private lateinit var editor: FakeEditor
    private lateinit var engine: KeyboardEngine
    private lateinit var prefs: Prefs
    private lateinit var view: KeyboardView
    private val overlay = Overlay()
    private val pxPerMm = 368f / 25.4f

    private fun setup(hover: Boolean = false, raw: Boolean = false) {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val dm = activity.resources.displayMetrics
        dm.xdpi = 368f
        dm.ydpi = 368f
        prefs = Prefs(activity)
        prefs.sp.edit().clear().putBoolean(Prefs.SPLIT_LANDSCAPE, true).putBoolean(Prefs.HOVER, hover).commit()
        editor = FakeEditor()
        engine = KeyboardEngine(editor, RecordingListener())
        engine.startInput(EditorContext(raw = raw, preferLatin = raw))
        view = KeyboardView(activity, engine, prefs, Silent, overlay)
        activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun touch(action: Int, x: Float, y: Float, flags: Int = 0) {
        val t = SystemClock.uptimeMillis()
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 0.1f })
        val e = MotionEvent.obtain(t, t, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, flags)
        view.onTouchEvent(e)
        e.recycle()
    }

    private fun tap(box: Box) {
        touch(MotionEvent.ACTION_DOWN, box.centerX, box.centerY)
        touch(MotionEvent.ACTION_UP, box.centerX, box.centerY)
        idle()
    }

    private fun letter(c: Char): Box =
        view.layoutKeys.first { !it.ghost && (it.def.action as? KeyAction.Char)?.base == c }.face

    private fun rect(b: Box) = Rect(
        kotlin.math.floor(b.left).toInt(), kotlin.math.floor(b.top).toInt(),
        kotlin.math.ceil(b.right).toInt(), kotlin.math.ceil(b.bottom).toInt(),
    )

    @Test
    fun floatButtonMakesTheKeyboardHoverAtItsOwnOpacity() {
        setup()
        assertEquals(Presentation.DOCKED, view.presentation)
        assertEquals(1f, view.alpha)
        assertNull(view.stripBox(Command.BEHIND))
        assertNull(view.sliderArea)
        val float = view.stripBox(Command.FLOAT)!!
        val height = view.height
        tap(float)
        assertTrue(prefs.hover)
        assertEquals(Presentation.HOVER, view.presentation)
        assertEquals(height, view.height)
        assertEquals(Prefs.DEFAULT_HOVER_ALPHA / 100f, view.alpha, 0.001f)
        assertEquals(listOf(Rect(0, 0, view.width, view.height)), view.touchRects())
        assertEquals(view.touchRects(), view.systemGestureExclusionRects)
        val behind = view.stripBox(Command.BEHIND)!!
        val slider = view.sliderArea!!
        assertEquals(view.stripBox(Command.FLOAT)!!.right, behind.left, 0.5f)
        assertTrue(slider.right <= view.stripBox(Command.SELECT_ALL)!!.left + 0.5f)
        tap(letter('a'))
        assertEquals("a", editor.text.toString())
        tap(view.stripBox(Command.FLOAT)!!)
        assertFalse(prefs.hover)
        assertEquals(Presentation.DOCKED, view.presentation)
        assertEquals(1f, view.alpha)
        assertEquals(emptyList<Rect>(), view.systemGestureExclusionRects)
    }

    @Test
    fun sliderInTheStripSetsOpacityAndSavesItOnRelease() {
        setup(hover = true)
        val slider = view.sliderArea!!
        val y = slider.centerY
        touch(MotionEvent.ACTION_DOWN, slider.right - 1f, y)
        assertEquals(100, view.opacity)
        assertEquals(1f, view.alpha, 0.001f)
        touch(MotionEvent.ACTION_MOVE, slider.left + 1f, y)
        assertEquals(Prefs.HOVER_ALPHA_MIN, view.opacity)
        assertEquals(0.2f, view.alpha, 0.001f)
        assertEquals(Prefs.DEFAULT_HOVER_ALPHA, prefs.hoverAlpha)
        touch(MotionEvent.ACTION_MOVE, slider.left + slider.width * 0.7f, y)
        val chosen = view.opacity
        assertTrue(chosen in 45..95 && chosen % 5 == 0)
        touch(MotionEvent.ACTION_UP, slider.left + slider.width * 0.7f, y)
        assertEquals(chosen, prefs.hoverAlpha)
        assertEquals(chosen / 100f, view.alpha, 0.001f)
        assertEquals("", editor.text.toString())
        touch(MotionEvent.ACTION_DOWN, slider.right - 1f, y)
        touch(MotionEvent.ACTION_UP, slider.right - 1f, y, MotionEvent.FLAG_CANCELED)
        assertEquals(chosen, prefs.hoverAlpha)
        assertEquals(chosen / 100f, view.alpha, 0.001f)
    }

    @Test
    fun collapseLeavesOnlyAHandleThatBringsTheKeyboardBack() {
        setup(hover = true)
        engine.perform(KeyAction.Lang)
        engine.perform(KeyAction.Char('g', 'G'))
        engine.perform(KeyAction.Char('k', 'K'))
        assertEquals("하", editor.composing)
        val behind = view.stripBox(Command.BEHIND)!!
        val a = letter('a')
        tap(behind)
        assertEquals(Presentation.COLLAPSED, view.presentation)
        assertEquals("", editor.composing)
        assertEquals("하", editor.text.toString())
        assertEquals(1f, view.alpha)
        val handle = view.handleBox()!!
        assertEquals(behind.right, handle.right, 0.01f)
        assertTrue(handle.width >= 11f * pxPerMm - 0.5f)
        assertEquals(listOf(rect(handle)), view.touchRects())
        assertEquals(view.touchRects(), view.systemGestureExclusionRects)
        tap(a)
        assertEquals("하", editor.text.toString())
        touch(MotionEvent.ACTION_DOWN, handle.centerX, handle.centerY)
        touch(MotionEvent.ACTION_UP, handle.left - 30f, handle.bottom + 30f)
        assertEquals(Presentation.COLLAPSED, view.presentation)
        tap(handle)
        assertEquals(Presentation.HOVER, view.presentation)
        assertEquals(Prefs.DEFAULT_HOVER_ALPHA / 100f, view.alpha, 0.001f)
        tap(a)
        assertEquals("하ㅁ", editor.text.toString())
    }

    @Test
    @Config(qualifiers = "w832dp-h750dp-land-420dpi-qwerty")
    fun physicalKeyboardShowsOnlyAChipUntilItIsTapped() {
        setup()
        assertEquals(Presentation.CHIP, view.presentation)
        assertTrue(view.height < 12f * pxPerMm)
        val box = view.chipBox()
        assertEquals(listOf(rect(box)), view.touchRects())
        assertEquals(view.width - 1.5f * pxPerMm, box.right, 1f)
        assertTrue(box.bottom <= view.height - 1.5f * pxPerMm + 1f)
        val latin = box.width
        engine.perform(KeyAction.Lang)
        view.setPreedit("한")
        assertTrue(view.chipBox().width > latin)
        view.setPreedit("")
        overlay.passThrough = true
        assertTrue(view.chipBox().width < latin)
        overlay.passThrough = false
        touch(MotionEvent.ACTION_DOWN, view.width / 2f, view.height / 2f)
        touch(MotionEvent.ACTION_UP, view.width / 2f, view.height / 2f)
        assertEquals(Presentation.CHIP, view.presentation)
        val chip = view.chipBox()
        touch(MotionEvent.ACTION_DOWN, chip.centerX, chip.centerY)
        touch(MotionEvent.ACTION_UP, chip.left - 40f, chip.centerY)
        assertEquals(Presentation.CHIP, view.presentation)
        tap(view.chipBox())
        assertTrue(overlay.summoned)
        assertEquals(Presentation.DOCKED, view.presentation)
        assertTrue(view.height > 40f * pxPerMm)
        tap(letter('a'))
        assertEquals("ㅁ", editor.composing)
        prefs.setHover(true)
        view.reload()
        assertEquals(Presentation.HOVER, view.presentation)
        overlay.reset()
        view.reload()
        idle()
        assertEquals(Presentation.CHIP, view.presentation)
        assertNotNull(view.chipBox())
    }
}
