package io.github.patissiermongs.foldkey.ime

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Rect
import android.inputmethodservice.InputMethodService
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import io.github.patissiermongs.foldkey.engine.Command
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.Lang
import io.github.patissiermongs.foldkey.layout.Box
import io.github.patissiermongs.foldkey.settings.KeyEchoView
import io.github.patissiermongs.foldkey.ui.KeyboardView
import io.github.patissiermongs.foldkey.ui.Overlay
import io.github.patissiermongs.foldkey.ui.Presentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowViewRootImpl

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w832dp-h750dp-land-420dpi")
class PhysicalKeyboardTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun engineOf(service: FoldKeyService) =
        FoldKeyService::class.java.getDeclaredField("engine").apply { isAccessible = true }.get(service) as KeyboardEngine

    private fun bindConnection(service: FoldKeyService, ic: InputConnection) {
        InputMethodService::class.java.getDeclaredField("mStartedInputConnection").apply { isAccessible = true }.set(service, ic)
    }

    private fun focus(view: View) {
        view.requestFocus()
        Shadow.extract<ShadowViewRootImpl>(view.rootView.parent).callWindowFocusChanged(true)
        idle()
    }

    private fun press(service: FoldKeyService, keyCode: Int, meta: Int = 0): Boolean {
        val t = SystemClock.uptimeMillis()
        val down = service.onKeyDown(keyCode, KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        service.onKeyUp(keyCode, KeyEvent(t, t + 30, KeyEvent.ACTION_UP, keyCode, 0, meta))
        return down
    }

    private fun type(service: FoldKeyService, latin: String) = latin.forEach {
        assertTrue(press(service, KeyEvent.KEYCODE_A + (it - 'a')))
    }

    private fun editText(service: FoldKeyService, packageName: String = "com.example.chat"): Pair<EditText, EditorInfo> {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val edit = EditText(activity)
        activity.setContentView(edit)
        focus(edit)
        val info = EditorInfo()
        bindConnection(service, edit.onCreateInputConnection(info)!!)
        info.packageName = packageName
        return edit to info
    }

    private fun attached(service: FoldKeyService): KeyboardView {
        service.resources.displayMetrics.apply { xdpi = 368f; ydpi = 368f }
        val view = service.onCreateInputView() as KeyboardView
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        idle()
        return view
    }

    private fun tap(view: View, box: Box) {
        val t = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val e = MotionEvent.obtain(t, t, action, box.centerX, box.centerY, 0)
            view.onTouchEvent(e)
            e.recycle()
        }
        idle()
    }

    @Test
    fun physicalKeysTypeHangulAfterTheHanEngKey() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        Prefs(service).sp.edit().clear().commit()
        val (edit, info) = editText(service)
        service.onStartInput(info, false)
        assertFalse(press(service, KeyEvent.KEYCODE_A))
        assertTrue(press(service, KeyEvent.KEYCODE_KANA))
        assertEquals(Lang.HANGUL, engineOf(service).lang)
        type(service, "gksrmf")
        idle()
        assertEquals("한글", edit.text.toString())
        assertFalse(press(service, KeyEvent.KEYCODE_SPACE))
        idle()
        assertEquals("한글", edit.text.toString())
        service.onFinishInput()
        assertFalse(press(service, KeyEvent.KEYCODE_G))
        service.onDestroy()
    }

    @Test
    fun remoteDesktopAppsGetThePhysicalKeysUnchanged() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        Prefs(service).sp.edit().clear().commit()
        val (edit, info) = editText(service, Prefs.DEFAULT_REMOTE_PACKAGES)
        service.onStartInput(info, false)
        assertFalse(press(service, KeyEvent.KEYCODE_KANA))
        assertEquals(Lang.LATIN, engineOf(service).lang)
        engineOf(service).toggleLang()
        assertFalse(press(service, KeyEvent.KEYCODE_G))
        idle()
        assertEquals("", edit.text.toString())
        service.onDestroy()
    }

    @Test
    fun terminalSyllableTypedBeforeTheWindowShowsIsKept() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        Prefs(service).sp.edit().clear().commit()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val echo = KeyEchoView(activity)
        activity.setContentView(echo)
        focus(echo)
        val info = EditorInfo()
        bindConnection(service, echo.onCreateInputConnection(info))
        service.onStartInput(info, false)
        assertTrue(press(service, KeyEvent.KEYCODE_KANA))
        type(service, "gk")
        assertEquals("하", engineOf(service).preedit)
        service.onCreateInputView()
        service.onStartInputView(info, false)
        assertEquals("하", engineOf(service).preedit)
        assertTrue(press(service, KeyEvent.KEYCODE_ENTER))
        idle()
        val log = echo.text.toString()
        assertTrue(log, log.indexOf("text \"하\"") in 0 until log.lastIndexOf("\\n"))
        service.onDestroy()
    }

    @Test
    @Config(qualifiers = "w832dp-h750dp-land-420dpi-qwerty")
    fun attachedKeyboardShowsAChipAndHideReturnsToIt() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        Prefs(service).sp.edit().clear().commit()
        assertTrue(service.onEvaluateInputViewShown())
        assertTrue(service.onShowInputRequested(0, false))
        val view = attached(service)
        service.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "com.example.chat" }, false)
        assertEquals(Presentation.CHIP, view.presentation)
        tap(view, view.chipBox())
        assertEquals(Presentation.DOCKED, view.presentation)
        tap(view, view.stripBox(Command.HIDE)!!)
        assertEquals(Presentation.CHIP, view.presentation)
        tap(view, view.chipBox())
        assertEquals(Presentation.DOCKED, view.presentation)
        service.onWindowHidden()
        assertEquals(Presentation.CHIP, view.presentation)
        service.onDestroy()
    }

    @Test
    @Config(qualifiers = "w832dp-h750dp-land-420dpi-qwerty")
    fun reattachingTheKeyboardStartsFromTheChipAgain() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        Prefs(service).sp.edit().clear().commit()
        val view = attached(service)
        service.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        tap(view, view.chipBox())
        assertEquals(Presentation.DOCKED, view.presentation)
        RuntimeEnvironment.setQualifiers("+nokeys")
        assertFalse(Overlay.hardwareKeyboard(service.resources.configuration))
        service.onConfigurationChanged(Configuration(service.resources.configuration))
        assertEquals(Presentation.DOCKED, view.presentation)
        RuntimeEnvironment.setQualifiers("+qwerty")
        service.onConfigurationChanged(Configuration(service.resources.configuration))
        assertEquals(Presentation.CHIP, view.presentation)
        service.onDestroy()
    }

    @Test
    fun withoutAKeyboardTheSystemRulesStand() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        assertTrue(service.onEvaluateInputViewShown())
        assertEquals(Presentation.DOCKED, attached(service).presentation)
        service.onDestroy()
    }

    @Test
    fun hoveringKeyboardLeavesTheAppUncoveredAndTouchesOnlyItsOwnArea() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        Prefs(service).sp.edit().clear().commit()
        val view = attached(service)
        service.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        val inputFrame = InputMethodService::class.java.getDeclaredField("mInputFrame").apply { isAccessible = true }.get(service) as View
        inputFrame.visibility = View.VISIBLE
        val decorView = service.window!!.window!!.decorView
        decorView.measure(
            View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.EXACTLY),
        )
        decorView.layout(0, 0, view.width, view.height)
        val decor = decorView.height
        assertEquals(view.height, decor)
        val docked = InputMethodService.Insets()
        service.onComputeInsets(docked)
        assertNotEquals(InputMethodService.Insets.TOUCHABLE_INSETS_REGION, docked.touchableInsets)
        assertTrue(docked.contentTopInsets < decor && docked.visibleTopInsets < decor)
        tap(view, view.stripBox(Command.FLOAT)!!)
        val location = IntArray(2).also { view.getLocationInWindow(it) }
        val hover = InputMethodService.Insets()
        service.onComputeInsets(hover)
        assertEquals(decor, hover.contentTopInsets)
        assertEquals(decor, hover.visibleTopInsets)
        assertEquals(InputMethodService.Insets.TOUCHABLE_INSETS_REGION, hover.touchableInsets)
        assertEquals(Rect(0, 0, view.width, view.height).apply { offset(location[0], location[1]) }, hover.touchableRegion.bounds)
        tap(view, view.stripBox(Command.BEHIND)!!)
        val collapsed = InputMethodService.Insets()
        service.onComputeInsets(collapsed)
        val handle = view.touchRects().single().apply { offset(location[0], location[1]) }
        assertEquals(handle, collapsed.touchableRegion.bounds)
        assertTrue(handle.width() * handle.height() * 20 < view.width * view.height)
        service.onWindowHidden()
        assertEquals(Presentation.HOVER, view.presentation)
        service.onDestroy()
    }
}
