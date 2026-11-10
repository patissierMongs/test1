package io.github.patissiermongs.foldkey.ime

import android.app.Activity
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.Lang
import io.github.patissiermongs.foldkey.settings.KeyEchoView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowViewRootImpl

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ModifierRelayTest {
    private class Recorder(target: InputConnection) : InputConnectionWrapper(target, true) {
        val sent = ArrayList<KeyEvent>()

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            sent.add(event)
            return super.sendKeyEvent(event)
        }
    }

    private class App(val service: FoldKeyService, val edit: EditText, val recorder: Recorder)

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun engineOf(service: FoldKeyService) =
        FoldKeyService::class.java.getDeclaredField("engine").apply { isAccessible = true }.get(service) as KeyboardEngine

    private fun start(packageName: String = "com.example.chat"): App {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        Prefs(service).sp.edit().clear().commit()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val edit = EditText(activity)
        activity.setContentView(edit)
        edit.requestFocus()
        Shadow.extract<ShadowViewRootImpl>(edit.rootView.parent).callWindowFocusChanged(true)
        idle()
        val info = EditorInfo()
        val recorder = Recorder(edit.onCreateInputConnection(info)!!)
        info.packageName = packageName
        InputMethodService::class.java.getDeclaredField("mStartedInputConnection").apply { isAccessible = true }.set(service, recorder)
        service.onStartInput(info, false)
        return App(service, edit, recorder)
    }

    private fun event(action: Int, keyCode: Int, scanCode: Int, repeat: Int, meta: Int): KeyEvent {
        val t = SystemClock.uptimeMillis()
        return KeyEvent(t, t, action, keyCode, repeat, meta, KEYBOARD, scanCode, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD)
    }

    private fun App.down(keyCode: Int, scanCode: Int, repeat: Int = 0, meta: Int = NUM_LOCK): Boolean =
        service.onKeyDown(keyCode, event(KeyEvent.ACTION_DOWN, keyCode, scanCode, repeat, meta))

    private fun App.up(keyCode: Int, scanCode: Int, meta: Int = NUM_LOCK): Boolean =
        service.onKeyUp(keyCode, event(KeyEvent.ACTION_UP, keyCode, scanCode, 0, meta))

    private fun App.press(keyCode: Int, scanCode: Int) {
        down(keyCode, scanCode)
        up(keyCode, scanCode)
    }

    private fun App.sent(): List<String> = recorder.sent.map { e ->
        val mods = buildList {
            if (e.isCtrlPressed) add("Ctrl")
            if (e.isAltPressed) add("Alt")
            if (e.isShiftPressed) add("Shift")
            if (e.isMetaPressed) add("Meta")
        }
        val action = if (e.action == KeyEvent.ACTION_DOWN) "down" else "up"
        "$action " + (mods + (NAMES[e.keyCode] ?: e.keyCode.toString())).joinToString("+")
    }

    @Test
    fun unmappedCtrlReachesTheAppAsCtrl() {
        val app = start()
        assertTrue(app.down(UNKNOWN, LEFT_CTRL))
        assertTrue(app.down(KeyEvent.KEYCODE_C, SCAN_C))
        assertTrue(app.up(KeyEvent.KEYCODE_C, SCAN_C))
        assertTrue(app.up(UNKNOWN, LEFT_CTRL))
        assertEquals(listOf("down Ctrl+CTRL_LEFT", "down Ctrl+C", "up Ctrl+C", "up CTRL_LEFT"), app.sent())
        assertEquals(listOf(LEFT_CTRL, SCAN_C, SCAN_C, LEFT_CTRL), app.recorder.sent.map { it.scanCode })
        assertTrue(app.recorder.sent.all { it.deviceId == KEYBOARD && it.source == InputDevice.SOURCE_KEYBOARD })
        assertEquals(KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or NUM_LOCK, app.recorder.sent[1].metaState)
        assertEquals(NUM_LOCK, app.recorder.sent[3].metaState)
        app.service.onDestroy()
    }

    @Test
    fun unmappedAltStaysHeldWhileTabIsPressedAgain() {
        val app = start()
        app.down(UNKNOWN, LEFT_ALT)
        app.press(KeyEvent.KEYCODE_TAB, SCAN_TAB)
        app.press(KeyEvent.KEYCODE_TAB, SCAN_TAB)
        app.up(UNKNOWN, LEFT_ALT)
        assertEquals(
            listOf("down Alt+ALT_LEFT", "down Alt+TAB", "up Alt+TAB", "down Alt+TAB", "up Alt+TAB", "up ALT_LEFT"),
            app.sent(),
        )
        app.service.onDestroy()
    }

    @Test
    fun secondUnmappedModifierCountsEvenWhenAndroidReportsItAsARepeat() {
        val app = start()
        app.down(UNKNOWN, LEFT_CTRL)
        assertTrue(app.down(UNKNOWN, LEFT_ALT, repeat = 1))
        app.press(KeyEvent.KEYCODE_TAB, SCAN_TAB)
        app.up(UNKNOWN, LEFT_ALT)
        app.up(UNKNOWN, LEFT_CTRL)
        assertEquals(
            listOf(
                "down Ctrl+CTRL_LEFT", "down Ctrl+Alt+ALT_LEFT", "down Ctrl+Alt+TAB", "up Ctrl+Alt+TAB",
                "up Ctrl+ALT_LEFT", "up CTRL_LEFT",
            ),
            app.sent(),
        )
        assertEquals(0, app.recorder.sent[1].repeatCount)
        app.service.onDestroy()
    }

    @Test
    fun heldUnmappedModifierIsSentOnce() {
        val app = start()
        assertTrue(app.down(UNKNOWN, LEFT_CTRL))
        assertTrue(app.down(UNKNOWN, LEFT_CTRL, repeat = 1))
        assertTrue(app.down(UNKNOWN, LEFT_CTRL, repeat = 2))
        assertTrue(app.up(UNKNOWN, LEFT_CTRL))
        assertEquals(listOf("down Ctrl+CTRL_LEFT", "up CTRL_LEFT"), app.sent())
        app.service.onDestroy()
    }

    @Test
    fun unmappedWindowsKeysArriveAsMeta() {
        val app = start()
        app.down(UNKNOWN, LEFT_WIN)
        app.press(KeyEvent.KEYCODE_R, SCAN_R)
        app.up(UNKNOWN, LEFT_WIN)
        app.press(UNKNOWN, RIGHT_WIN)
        assertEquals(
            listOf("down Meta+META_LEFT", "down Meta+R", "up Meta+R", "up META_LEFT", "down Meta+META_RIGHT", "up META_RIGHT"),
            app.sent(),
        )
        assertEquals(KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON or NUM_LOCK, app.recorder.sent[1].metaState)
        assertEquals(KeyEvent.META_META_ON or KeyEvent.META_META_RIGHT_ON or NUM_LOCK, app.recorder.sent[4].metaState)
        app.service.onDestroy()
    }

    @Test
    fun rightCtrlAndRightAltKeepTheirSide() {
        val app = start(Prefs.DEFAULT_REMOTE_PACKAGES)
        app.press(UNKNOWN, RIGHT_CTRL)
        app.press(UNKNOWN, RIGHT_ALT)
        assertEquals(listOf("down Ctrl+CTRL_RIGHT", "up CTRL_RIGHT", "down Alt+ALT_RIGHT", "up ALT_RIGHT"), app.sent())
        assertEquals(KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_RIGHT_ON or NUM_LOCK, app.recorder.sent[0].metaState)
        assertEquals(KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON or NUM_LOCK, app.recorder.sent[2].metaState)
        app.service.onDestroy()
    }

    @Test
    fun remoteDesktopAppsGetTheRestoredModifiers() {
        val app = start(Prefs.DEFAULT_REMOTE_PACKAGES)
        app.down(UNKNOWN, LEFT_ALT)
        app.press(KeyEvent.KEYCODE_TAB, SCAN_TAB)
        app.up(UNKNOWN, LEFT_ALT)
        assertFalse(app.down(KeyEvent.KEYCODE_G, SCAN_G))
        assertFalse(app.up(KeyEvent.KEYCODE_G, SCAN_G))
        assertEquals(listOf("down Alt+ALT_LEFT", "down Alt+TAB", "up Alt+TAB", "up ALT_LEFT"), app.sent())
        assertEquals(Lang.LATIN, engineOf(app.service).lang)
        app.service.onDestroy()
    }

    @Test
    fun unmappedRightAltAloneStillSwitchesHangul() {
        val app = start()
        assertEquals(Lang.LATIN, engineOf(app.service).lang)
        app.press(UNKNOWN, RIGHT_ALT)
        assertEquals(Lang.HANGUL, engineOf(app.service).lang)
        assertEquals(listOf("down Alt+ALT_RIGHT", "up ALT_RIGHT"), app.sent())
        app.service.onDestroy()
    }

    @Test
    fun restoredCtrlEndsTheSyllableBeforeTheShortcut() {
        val app = start()
        app.press(KeyEvent.KEYCODE_KANA, SCAN_HANGEUL)
        app.press(KeyEvent.KEYCODE_G, SCAN_G)
        app.press(KeyEvent.KEYCODE_K, SCAN_K)
        app.down(UNKNOWN, LEFT_CTRL)
        assertTrue(engineOf(app.service).isComposing)
        assertTrue(app.down(KeyEvent.KEYCODE_DPAD_LEFT, SCAN_LEFT))
        assertFalse(engineOf(app.service).isComposing)
        idle()
        assertEquals("하", app.edit.text.toString())
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(app.edit.text))
        assertEquals(listOf("down Ctrl+CTRL_LEFT", "down Ctrl+DPAD_LEFT"), app.sent())
        app.service.onDestroy()
    }

    @Test
    fun keyPressedBeforeTheModifierKeepsItsOwnRelease() {
        val app = start()
        assertFalse(app.down(KeyEvent.KEYCODE_A, SCAN_A))
        assertTrue(app.down(UNKNOWN, LEFT_CTRL))
        assertFalse(app.up(KeyEvent.KEYCODE_A, SCAN_A))
        assertTrue(app.up(UNKNOWN, LEFT_CTRL))
        assertEquals(listOf("down Ctrl+CTRL_LEFT", "up CTRL_LEFT"), app.sent())
        app.service.onDestroy()
    }

    @Test
    fun systemKeysAreLeftToTheSystemWhileAModifierIsHeld() {
        val app = start()
        app.down(UNKNOWN, LEFT_ALT)
        app.down(KeyEvent.KEYCODE_VOLUME_UP, SCAN_VOLUME_UP)
        app.up(KeyEvent.KEYCODE_VOLUME_UP, SCAN_VOLUME_UP)
        app.up(UNKNOWN, LEFT_ALT)
        assertEquals(listOf("down Alt+ALT_LEFT", "up ALT_LEFT"), app.sent())
        app.service.onDestroy()
    }

    @Test
    fun keysWithoutAKeyCodeThatAreNotModifiersPassUnchanged() {
        val app = start()
        assertFalse(app.down(UNKNOWN, SCAN_F13))
        assertFalse(app.up(UNKNOWN, SCAN_F13))
        assertTrue(app.recorder.sent.isEmpty())
        app.service.onDestroy()
    }

    @Test
    fun mappedModifiersAreLeftToAndroid() {
        val app = start()
        val ctrl = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or NUM_LOCK
        assertFalse(app.down(KeyEvent.KEYCODE_CTRL_LEFT, LEFT_CTRL, meta = ctrl))
        assertFalse(app.down(KeyEvent.KEYCODE_C, SCAN_C, meta = ctrl))
        assertFalse(app.up(KeyEvent.KEYCODE_C, SCAN_C, meta = ctrl))
        assertFalse(app.up(KeyEvent.KEYCODE_CTRL_LEFT, LEFT_CTRL))
        assertTrue(app.recorder.sent.isEmpty())
        app.service.onDestroy()
    }

    @Test
    fun detachingTheKeyboardReleasesHeldModifiers() {
        val app = start()
        val attached = Configuration(app.service.resources.configuration).apply {
            keyboard = Configuration.KEYBOARD_QWERTY
            hardKeyboardHidden = Configuration.HARDKEYBOARDHIDDEN_NO
        }
        app.service.onConfigurationChanged(attached)
        app.down(UNKNOWN, LEFT_CTRL)
        app.down(UNKNOWN, LEFT_ALT)
        app.service.onConfigurationChanged(Configuration(attached).apply { keyboard = Configuration.KEYBOARD_NOKEYS })
        assertFalse(app.down(KeyEvent.KEYCODE_C, SCAN_C))
        assertEquals(listOf("down Ctrl+CTRL_LEFT", "down Ctrl+Alt+ALT_LEFT", "up Ctrl+ALT_LEFT", "up CTRL_LEFT"), app.sent())
        assertEquals(listOf(LEFT_ALT, LEFT_CTRL), app.recorder.sent.drop(2).map { it.scanCode })
        app.service.onDestroy()
    }

    @Test
    fun eventMonitorShowsTheScanCodeOfKeysWithoutAKeyCode() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val echo = KeyEchoView(activity)
        echo.onKeyDown(UNKNOWN, event(KeyEvent.ACTION_DOWN, UNKNOWN, LEFT_CTRL, 0, NUM_LOCK))
        echo.onKeyDown(KeyEvent.KEYCODE_C, event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C, SCAN_C, 0, NUM_LOCK))
        val c = KeyEvent.keyCodeToString(KeyEvent.KEYCODE_C).removePrefix("KEYCODE_")
        assertEquals("down UNKNOWN(scan 29)\ndown $c", echo.text.toString())
    }

    private companion object {
        val NAMES = mapOf(
            KeyEvent.KEYCODE_CTRL_LEFT to "CTRL_LEFT",
            KeyEvent.KEYCODE_CTRL_RIGHT to "CTRL_RIGHT",
            KeyEvent.KEYCODE_ALT_LEFT to "ALT_LEFT",
            KeyEvent.KEYCODE_ALT_RIGHT to "ALT_RIGHT",
            KeyEvent.KEYCODE_META_LEFT to "META_LEFT",
            KeyEvent.KEYCODE_META_RIGHT to "META_RIGHT",
            KeyEvent.KEYCODE_TAB to "TAB",
            KeyEvent.KEYCODE_C to "C",
            KeyEvent.KEYCODE_R to "R",
            KeyEvent.KEYCODE_DPAD_LEFT to "DPAD_LEFT",
        )

        const val UNKNOWN = KeyEvent.KEYCODE_UNKNOWN
        const val NUM_LOCK = KeyEvent.META_NUM_LOCK_ON
        const val KEYBOARD = 25
        const val LEFT_CTRL = 29
        const val RIGHT_CTRL = 97
        const val LEFT_ALT = 56
        const val RIGHT_ALT = 100
        const val LEFT_WIN = 125
        const val RIGHT_WIN = 126
        const val SCAN_TAB = 15
        const val SCAN_R = 19
        const val SCAN_A = 30
        const val SCAN_G = 34
        const val SCAN_K = 37
        const val SCAN_C = 46
        const val SCAN_LEFT = 105
        const val SCAN_HANGEUL = 122
        const val SCAN_VOLUME_UP = 115
        const val SCAN_F13 = 183
    }
}
