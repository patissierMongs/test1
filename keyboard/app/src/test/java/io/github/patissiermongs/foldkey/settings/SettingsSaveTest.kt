package io.github.patissiermongs.foldkey.settings

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.SeekBar
import io.github.patissiermongs.foldkey.ime.Prefs
import org.junit.Assert.assertEquals
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
        assertEquals(shown, Prefs(activity).sp.getInt(Prefs.ROW_HEIGHT, 95))
        controller.pause().stop().destroy()
    }
}
