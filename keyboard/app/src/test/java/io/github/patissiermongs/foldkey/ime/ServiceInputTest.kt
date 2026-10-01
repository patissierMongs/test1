package io.github.patissiermongs.foldkey.ime

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import io.github.patissiermongs.foldkey.engine.FakeEditor
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.UsKeyMap
import io.github.patissiermongs.foldkey.layout.Box
import io.github.patissiermongs.foldkey.settings.KeyEchoView
import io.github.patissiermongs.foldkey.ui.KeyboardView
import org.junit.Assert.assertEquals
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
@Config(sdk = [36], qualifiers = "w750dp-h832dp-420dpi")
class ServiceInputTest {
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun ch(c: Char) = KeyAction.Char(c, UsKeyMap.shiftedOf(c))

    private fun engineField() = FoldKeyService::class.java.getDeclaredField("engine").apply { isAccessible = true }

    private fun engineOf(service: FoldKeyService) = engineField().get(service) as KeyboardEngine

    private fun bindConnection(service: FoldKeyService, ic: InputConnection) {
        InputMethodService::class.java.getDeclaredField("mStartedInputConnection").apply { isAccessible = true }.set(service, ic)
    }

    private fun focus(view: View) {
        view.requestFocus()
        Shadow.extract<ShadowViewRootImpl>(view.rootView.parent).callWindowFocusChanged(true)
        idle()
    }

    private fun measured(service: FoldKeyService): KeyboardView {
        val dm = service.resources.displayMetrics
        dm.xdpi = 368f
        dm.ydpi = 368f
        val view = service.onCreateInputView() as KeyboardView
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1968, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        return view
    }

    private fun touch(view: View, action: Int, box: Box, t: Long) {
        val e = MotionEvent.obtain(t, t, action, box.centerX, box.centerY, 0)
        view.onTouchEvent(e)
        e.recycle()
    }

    private fun fakeEngine(service: FoldKeyService): FakeEditor {
        val editor = FakeEditor()
        engineField().set(service, KeyboardEngine(editor, service))
        return editor
    }

    @Test
    fun belatedSelectionReportKeepsTheHangulSyllable() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        service.onCreateInputView()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val edit = EditText(activity)
        activity.setContentView(edit)
        focus(edit)
        val info = EditorInfo()
        bindConnection(service, edit.onCreateInputConnection(info)!!)
        service.onStartInputView(info, false)
        val engine = engineOf(service)
        engine.perform(KeyAction.Lang)
        engine.perform(ch('r'))
        engine.perform(ch('k'))
        engine.perform(KeyAction.Space)
        idle()
        assertEquals("가 ", edit.text.toString())
        val afterSpace = edit.selectionEnd
        engine.perform(ch('s'))
        idle()
        service.onUpdateSelection(1, 1, afterSpace, afterSpace, -1, -1)
        engine.perform(ch('k'))
        idle()
        assertEquals("가 나", edit.text.toString())
        service.onDestroy()
    }

    @Test
    fun keyHeldWhileTheAppRestartsInputIsStillTyped() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        val editor = fakeEngine(service)
        val view = measured(service)
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "com.example.chat" }
        service.onStartInputView(info, false)
        val h = view.layoutKeys.first { !it.ghost && (it.def.action as? KeyAction.Char)?.base == 'h' }.face
        val t = SystemClock.uptimeMillis()
        touch(view, MotionEvent.ACTION_DOWN, h, t)
        service.onStartInputView(info, true)
        touch(view, MotionEvent.ACTION_UP, h, t + 60)
        assertEquals("h", editor.text.toString())
        service.onDestroy()
    }

    @Test
    fun escCtrlHeldWhenANewFieldStartsSendsNothing() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        val editor = fakeEngine(service)
        val view = measured(service)
        service.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        val esc = view.layoutKeys.first { it.def.action == KeyAction.EscCtrl }.face
        val t = SystemClock.uptimeMillis()
        touch(view, MotionEvent.ACTION_DOWN, esc, t)
        service.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT; packageName = "com.example.other" }, false)
        touch(view, MotionEvent.ACTION_UP, esc, t + 80)
        assertTrue("sent ${editor.keys}", editor.keys.isEmpty())
        service.onDestroy()
    }

    @Test
    fun terminalSyllableSurvivesAConfigurationChange() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val echo = KeyEchoView(activity)
        activity.setContentView(echo)
        focus(echo)
        val info = EditorInfo()
        bindConnection(service, echo.onCreateInputConnection(info))
        service.onCreateInputView()
        service.onStartInputView(info, false)
        val engine = engineOf(service)
        engine.perform(KeyAction.Lang)
        engine.perform(ch('g'))
        engine.perform(ch('k'))
        val rotated = Configuration(service.resources.configuration).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
        service.onConfigurationChanged(rotated)
        service.onCreateInputView()
        service.onStartInputView(info, false)
        idle()
        assertTrue("terminal log: " + echo.text, echo.text.toString().contains("text \"하\""))
        service.onDestroy()
    }

    @Test
    fun passwordFieldOfATerminalAppIsNotRecorded() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        Prefs(service).sp.edit().clear().putBoolean(Prefs.TERMINAL_ECHO, true).commit()
        service.onCreateInputView()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val edit = EditText(activity)
        edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        activity.setContentView(edit)
        focus(edit)
        val info = EditorInfo()
        val ic = edit.onCreateInputConnection(info)!!
        info.packageName = "com.sonelli.juicessh"
        bindConnection(service, ic)
        service.onStartInputView(info, false)
        val engine = engineOf(service)
        assertTrue(engine.context.raw && engine.context.secret)
        "hunter2".forEach { engine.perform(ch(it)) }
        idle()
        assertEquals("hunter2", edit.text.toString())
        assertTrue(engine.typedSegments.isEmpty())
        service.onDestroy()
    }

    @Test
    @Config(qualifiers = "w832dp-h750dp-land-420dpi")
    fun clipboardHistoryIsHiddenWhileTheDeviceIsLocked() {
        val service = Robolectric.setupService(FoldKeyService::class.java)
        service.resources.displayMetrics.apply { xdpi = 368f; ydpi = 368f }
        Prefs(service).sp.edit().clear().commit()
        val view = service.onCreateInputView() as KeyboardView
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        idle()
        val cm = service.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("iban", "DE89 3704 0044 0532 0130 00"))
        idle()
        service.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        assertEquals(listOf("DE89 3704 0044 0532 0130 00"), view.clipSource().map { it.text })
        val km = service.getSystemService(KeyguardManager::class.java)
        shadowOf(km).setKeyguardLocked(true)
        shadowOf(km).setIsDeviceLocked(true)
        service.onStartInputView(
            EditorInfo().apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                packageName = "com.android.systemui"
            },
            false,
        )
        idle()
        assertEquals(2, view.clipSlots.size)
        assertEquals(emptyList<String>(), view.clipSource().map { it.text })
        shadowOf(km).setKeyguardLocked(false)
        shadowOf(km).setIsDeviceLocked(false)
        service.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        assertEquals(listOf("DE89 3704 0044 0532 0130 00"), view.clipSource().map { it.text })
        service.onDestroy()
    }
}
