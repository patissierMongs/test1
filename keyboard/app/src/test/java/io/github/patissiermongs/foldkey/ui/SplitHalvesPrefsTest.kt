package io.github.patissiermongs.foldkey.ui

import android.app.Activity
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.input.TypingCalibration
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.ime.ScreenProfile
import io.github.patissiermongs.foldkey.layout.LayoutKind
import io.github.patissiermongs.foldkey.layout.Layouts
import io.github.patissiermongs.foldkey.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w832dp-h750dp-land-420dpi")
class SplitHalvesPrefsTest {
    private val profile = ScreenProfile.INNER_LANDSCAPE

    private fun prefs(): Prefs {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return Prefs(activity).also { it.sp.edit().clear().commit() }
    }

    private fun allViews(v: View): List<View> =
        if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { allViews(v.getChildAt(it)) } else listOf(v)

    private fun spec(prefs: Prefs, layer: Layer) =
        LayoutBuilder.spec(prefs, LayoutKind.SPLIT, layer, profile, 2184f, 14.488f, 14.488f, 0f, 9.5f)

    @Test
    fun withoutHalfValuesBothHalvesShareTheCommonWidth() {
        val prefs = prefs()
        val code = LayoutBuilder.splitUnits(prefs, Layer.CODE, profile)
        assertEquals(LayoutBuilder.Units(8.5f, 8.5f, false), code)
        assertTrue(spec(prefs, Layer.CODE).splitUnitRightMm.isNaN())
        assertFalse(prefs.hasHalfWidths(profile))
        assertFalse(prefs.hasGeneralHalfWidths)
    }

    @Test
    fun savedCodeHalvesReachTheGeometrySpecAndKeepTheCommonKeyInStep() {
        val prefs = prefs()
        prefs.saveCodeHalves(profile, 80, 92, 20, 30)
        assertEquals(LayoutBuilder.Units(8.0f, 9.2f, true), LayoutBuilder.splitUnits(prefs, Layer.CODE, profile))
        val spec = spec(prefs, Layer.CODE)
        assertEquals(8.0f, spec.splitUnitMm, 0f)
        assertEquals(9.2f, spec.splitUnitRightMm, 0f)
        assertEquals(2.0f * 14.488f, spec.splitMarginLeftPx, 0.01f)
        assertEquals(3.0f * 14.488f, spec.splitMarginRightPx, 0.01f)
        assertEquals(86, prefs.sp.getInt(Prefs.widthKey(profile), 0))
        assertEquals(2.0f, prefs.codeSideLeftMm, 0f)
        assertEquals(3.0f, prefs.codeSideRightMm, 0f)
        for (other in ScreenProfile.entries - profile) assertFalse(prefs.hasHalfWidths(other))
    }

    @Test
    fun savedTextLayerHalvesLeaveTheCodeLayerAlone() {
        val prefs = prefs()
        prefs.saveCodeHalves(profile, 80, 92, 20, 30)
        prefs.saveGeneralHalves(75, 95, 40, 50)
        assertEquals(LayoutBuilder.Units(7.5f, 9.5f, true), LayoutBuilder.splitUnits(prefs, Layer.GENERAL, profile))
        assertEquals(LayoutBuilder.Units(8.0f, 9.2f, true), LayoutBuilder.splitUnits(prefs, Layer.CODE, profile))
        assertEquals(85, prefs.sp.getInt(Prefs.GENERAL_UNIT, 0))
        assertEquals(4.0f, prefs.generalSideLeftMm, 0f)
        assertEquals(5.0f, prefs.generalSideRightMm, 0f)
    }

    @Test
    fun theTextLayerFollowsEachCodeHalfWhenItHasNoWidthOfItsOwn() {
        val prefs = prefs()
        prefs.saveCodeHalves(profile, 80, 92, 20, 30)
        val units = LayoutBuilder.splitUnits(prefs, Layer.GENERAL, profile)
        val (left, right) = Layouts.generalSplitUnitsMm(8.0f, 9.2f, 14.0f - 2.0f, 14.0f - 3.0f)
        assertEquals(left, units.leftMm, 0.0001f)
        assertEquals(right, units.rightMm, 0.0001f)
        assertTrue(units.halves)
    }

    @Test
    fun aCommonTextLayerWidthStaysCommonAndANewOneReplacesTheHalves() {
        val prefs = prefs()
        prefs.sp.edit().putInt(Prefs.GENERAL_UNIT, 90).commit()
        assertEquals(LayoutBuilder.Units(9.0f, 9.0f, false), LayoutBuilder.splitUnits(prefs, Layer.GENERAL, profile))
        prefs.saveGeneralHalves(75, 95, 40, 50)
        prefs.clearGeneralHalfWidths()
        assertFalse(prefs.hasGeneralHalfWidths)
        assertEquals(85, prefs.sp.getInt(Prefs.GENERAL_UNIT, 0))
    }

    @Test
    fun aCalibrationReplacesTheHalvesWithItsOwnCommonWidth() {
        val prefs = prefs()
        prefs.saveCodeHalves(profile, 80, 92, 20, 30)
        prefs.saveGeneralHalves(75, 95, 40, 50)
        val placement = TypingCalibration.Placement(unitMm = 7.7f, rowMm = 9.0f, leftMm = 14f, rightMm = 17f, liftMm = 1f)
        prefs.applyCalibration(mapOf(Layer.CODE to placement, Layer.GENERAL to placement.copy(unitMm = 9.0f)), emptyMap(), profile)
        assertFalse(prefs.hasHalfWidths(profile))
        assertFalse(prefs.hasGeneralHalfWidths)
        assertEquals(LayoutBuilder.Units(7.7f, 7.7f, false), LayoutBuilder.splitUnits(prefs, Layer.CODE, profile))
        assertEquals(LayoutBuilder.Units(9.0f, 9.0f, false), LayoutBuilder.splitUnits(prefs, Layer.GENERAL, profile))
    }

    @Test
    fun theHeatmapSignatureChangesWhenTheHalvesChangeTheKeySizes() {
        val prefs = prefs()
        val before = prefs.heatSignature(profile)
        prefs.saveCodeHalves(profile, 80, 92, 20, 30)
        val halves = prefs.heatSignature(profile)
        assertNotEquals(before, halves)
        prefs.saveCodeHalves(profile, 80, 93, 20, 30)
        assertNotEquals(halves, prefs.heatSignature(profile))
        prefs.saveCodeHalves(profile, 80, 92, 20, 30)
        assertEquals(halves, prefs.heatSignature(profile))
        prefs.saveCodeHalves(profile, 82, 90, 20, 30)
        assertEquals(86, prefs.sp.getInt(Prefs.widthKey(profile), 0))
        assertNotEquals(halves, prefs.heatSignature(profile))
        prefs.saveCodeHalves(profile, 80, 92, 20, 30)
        prefs.saveGeneralHalves(75, 95, 40, 50)
        val general = prefs.heatSignature(profile)
        prefs.saveGeneralHalves(77, 93, 40, 50)
        assertEquals(85, prefs.sp.getInt(Prefs.GENERAL_UNIT, 0))
        assertNotEquals(general, prefs.heatSignature(profile))
        assertTrue(Prefs.LAYOUT_KEYS.contains(Prefs.widthLeftKey(profile)))
        assertTrue(Prefs.LAYOUT_KEYS.contains(Prefs.GENERAL_UNIT_RIGHT))
    }

    @Test
    fun draggingTheCommonKeyWidthSliderWithAFingerReplacesTheHalves() {
        val prefs = prefs()
        prefs.saveCodeHalves(profile, 80, 92, 20, 30)
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val width = allViews(controller.get().window.decorView).filterIsInstance<SeekBar>()
            .single { it.min == Prefs.KEY_WIDTH_FILL && it.progress == 86 }
        assertTrue(width.width > 0)
        val y = width.height / 2f
        val start = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, after: Long) {
            val event = MotionEvent.obtain(start, start + after, action, x, y, 0)
            width.dispatchTouchEvent(event)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, width.width * 0.5f, 0)
        send(MotionEvent.ACTION_MOVE, width.width * 0.9f, 20)
        assertTrue(prefs.hasHalfWidths(profile))
        send(MotionEvent.ACTION_UP, width.width * 0.9f, 40)
        assertFalse(prefs.hasHalfWidths(profile))
        assertTrue(width.progress != 86)
        assertEquals(width.progress, prefs.sp.getInt(Prefs.widthKey(profile), 0))
        assertEquals(LayoutBuilder.Units(width.progress / 10f, width.progress / 10f, false), LayoutBuilder.splitUnits(prefs, Layer.CODE, profile))
        controller.pause().stop().destroy()
    }

    @Test
    fun movingTheCommonKeyWidthSliderReplacesTheHalves() {
        val prefs = prefs()
        prefs.saveCodeHalves(profile, 80, 92, 20, 30)
        prefs.saveGeneralHalves(75, 99, 40, 50)
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val bars = allViews(activity.window.decorView).filterIsInstance<SeekBar>()
        val width = bars.single { it.min == Prefs.KEY_WIDTH_FILL && it.progress == 86 }
        width.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertFalse(prefs.hasHalfWidths(profile))
        assertEquals(width.progress, prefs.sp.getInt(Prefs.widthKey(profile), 0))
        assertEquals(LayoutBuilder.Units(width.progress / 10f, width.progress / 10f, false), LayoutBuilder.splitUnits(prefs, Layer.CODE, profile))
        assertTrue(prefs.hasGeneralHalfWidths)
        val general = bars.single { it.min == Prefs.GENERAL_UNIT_AUTO && it.progress == 87 }
        general.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertFalse(prefs.hasGeneralHalfWidths)
        assertEquals(general.progress, prefs.sp.getInt(Prefs.GENERAL_UNIT, 0))
        controller.pause().stop().destroy()
    }
}
