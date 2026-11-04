package io.github.patissiermongs.foldkey.settings

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import io.github.patissiermongs.foldkey.R
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.ime.ScreenProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsSaveTest {
    private fun allViews(v: View): List<View> =
        if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { allViews(v.getChildAt(it)) } else listOf(v)

    @Test
    fun terminalAppListIsSavedWhenLeavingSettings() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val field = allViews(activity.window.decorView).filterIsInstance<EditText>()
            .single { it.text.toString() == Prefs.DEFAULT_RAW_PACKAGES }
        field.requestFocus()
        field.setText(Prefs.DEFAULT_RAW_PACKAGES + ", dev.example.term")
        controller.pause().stop().destroy()
        assertEquals(Prefs.DEFAULT_RAW_PACKAGES + ", dev.example.term", Prefs(activity).sp.getString(Prefs.RAW_PACKAGES, null))
    }

    @Test
    fun sliderMovedWithKeysOrAccessibilityIsSaved() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val rowHeight = allViews(activity.window.decorView).filterIsInstance<SeekBar>().first()
        assertEquals(95, rowHeight.progress)
        rowHeight.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue(rowHeight.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, null))
        val shown = rowHeight.progress
        assertTrue(shown > 95)
        assertEquals(shown, Prefs(activity).sp.getInt(Prefs.rowKey(ScreenProfile.INNER_PORTRAIT), 95))
        assertFalse(Prefs(activity).sp.contains(Prefs.ROW_HEIGHT))
        controller.pause().stop().destroy()
    }

    @Test
    @Config(qualifiers = "w832dp-h750dp-land-420dpi")
    fun eachScreenHasItsOwnSizeSlidersAndTheSplitSwitchUpdatesTheShownKeyWidth() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val prefs = Prefs(activity)
        val views = allViews(activity.window.decorView)
        val texts = views.filterIsInstance<TextView>().map { it.text.toString() }
        for (name in listOf("Unfolded screen, portrait", "Unfolded screen, landscape (this screen)", "Cover screen, portrait", "Cover screen, landscape")) {
            assertTrue(name, texts.contains(name))
        }
        fun label(bar: SeekBar): String {
            val parent = bar.parent as ViewGroup
            return (parent.getChildAt(parent.indexOfChild(bar) - 1) as TextView).text.toString()
        }
        val bars = views.filterIsInstance<SeekBar>().take(8)
        assertEquals(listOf(95, 39, 95, 85, 95, 39, 95, 85), bars.map { it.progress })
        assertEquals(listOf("Row height: 9.5 mm", "Key width: Fill", "Row height: 9.5 mm", "Key width: 8.5 mm"), bars.take(4).map { label(it) })
        bars[2].onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue(bars[2].progress > 95)
        assertEquals(bars[2].progress, prefs.sp.getInt(Prefs.rowKey(ScreenProfile.INNER_LANDSCAPE), 0))
        for (s in ScreenProfile.entries - ScreenProfile.INNER_LANDSCAPE) assertFalse(prefs.sp.contains(Prefs.rowKey(s)))
        views.filterIsInstance<Switch>().single { it.text == activity.getString(R.string.pref_split_portrait) }.performClick()
        assertTrue(prefs.isSplit(false))
        assertEquals(85, bars[1].progress)
        assertEquals("Key width: 8.5 mm", label(bars[1]))
        assertEquals(39, bars[5].progress)
        assertTrue(ScreenProfile.entries.none { prefs.sp.contains(Prefs.widthKey(it)) })
        bars[1].onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        assertTrue(bars[1].progress < 85)
        assertEquals(bars[1].progress, prefs.sp.getInt(Prefs.widthKey(ScreenProfile.INNER_PORTRAIT), 0))
        views.filterIsInstance<Switch>().single { it.text == activity.getString(R.string.pref_split_landscape) }.performClick()
        assertFalse(prefs.isSplit(true))
        assertEquals(39, bars[3].progress)
        assertEquals("Key width: Fill", label(bars[3]))
        assertEquals(39, bars[7].progress)
        assertFalse(prefs.sp.contains(Prefs.widthKey(ScreenProfile.INNER_LANDSCAPE)))
        controller.pause().stop().destroy()
    }
}
