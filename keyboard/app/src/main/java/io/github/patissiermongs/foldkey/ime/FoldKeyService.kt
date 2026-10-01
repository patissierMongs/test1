package io.github.patissiermongs.foldkey.ime

import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import io.github.patissiermongs.foldkey.engine.Command
import io.github.patissiermongs.foldkey.engine.EditorContext
import io.github.patissiermongs.foldkey.engine.EngineListener
import io.github.patissiermongs.foldkey.engine.EngineSettings
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.settings.SettingsActivity
import io.github.patissiermongs.foldkey.ui.Clip
import io.github.patissiermongs.foldkey.ui.ClipEdit
import io.github.patissiermongs.foldkey.ui.HapticFeedback
import io.github.patissiermongs.foldkey.ui.KeyboardView
import io.github.patissiermongs.foldkey.ui.Palette
import java.io.File
import java.io.IOException

class FoldKeyService : InputMethodService(), EngineListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: Prefs
    private lateinit var engine: KeyboardEngine
    private lateinit var feedback: HapticFeedback
    private var keyboardView: KeyboardView? = null
    private val clipboardHistory = ClipboardHistory()
    private lateinit var pinStore: PinStore
    private var lastClip: Pair<Long, String>? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip(fromListener = true) }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        feedback = HapticFeedback(this, prefs)
        engine = KeyboardEngine(InputConnectionEditor(this) { currentInputConnection }, this)
        pinStore = PinStore(File(noBackupFilesDir, PIN_FILE))
        clipboardHistory.restorePins(pinStore.load())
        applySettings()
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        getSystemService(ClipboardManager::class.java)?.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipListener)
        clipboardHistory.clear()
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        keyboardView?.saveState()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        keyboardView?.saveState()
        val view = KeyboardView(this, engine, prefs, feedback)
        view.clipSource = { clipboardHistory.items(SystemClock.elapsedRealtime()).map { Clip(it.text, it.pinned) } }
        view.onClipEdit = { edit, text -> editClip(edit, text) }
        view.pinsFull = { clipboardHistory.pinsFull }
        keyboardView = view
        updateNavigationBarAppearance()
        return view
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        engine.startInput(contextOf(info), restarting)
        captureClip()
        keyboardView?.cancelTouches()
        keyboardView?.showEditCommands = !engine.context.raw
        keyboardView?.showSwitchKey = shouldOfferSwitchingToNextInputMethod()
        keyboardView?.setEditorLine(lineOf(info.getInitialTextBeforeCursor(ECHO_CHARS, 0)))
        keyboardView?.invalidate()
    }

    override fun onFinishInput() {
        engine.finishInput()
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        keyboardView?.cancelTouches()
        engine.finishInput()
        keyboardView?.saveState()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        engine.selectionChanged(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        refreshEditorLine()
    }

    private fun refreshEditorLine() {
        val view = keyboardView ?: return
        val ctx = engine.context
        if (!prefs.centerEcho || ctx.raw || ctx.secret || !view.hasPanel) {
            view.setEditorLine("")
            return
        }
        view.setEditorLine(lineOf(currentInputConnection?.getTextBeforeCursor(ECHO_CHARS, 0)))
    }

    private fun lineOf(text: CharSequence?): String {
        if (text == null || engine.context.secret) return ""
        return text.toString().substringAfterLast('\n')
    }

    private fun captureClip(fromListener: Boolean = false) {
        if (!prefs.centerClipboard) return
        val clip = getSystemService(ClipboardManager::class.java)?.primaryClip ?: return
        if (clip.itemCount == 0) return
        if (clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true) return
        val text = clip.getItemAt(0).text?.toString() ?: return
        val key = (clip.description?.timestamp ?: 0L) to text
        if (!fromListener && key == lastClip) return
        lastClip = key
        if (clipboardHistory.add(text, SystemClock.elapsedRealtime())) keyboardView?.invalidate()
    }

    private fun editClip(edit: ClipEdit, text: String) {
        val pinsBefore = clipboardHistory.pinned
        when (edit) {
            ClipEdit.PIN -> clipboardHistory.pin(text)
            ClipEdit.UNPIN -> clipboardHistory.unpin(text, SystemClock.elapsedRealtime())
            ClipEdit.DELETE -> {
                clipboardHistory.remove(text)
                val cm = getSystemService(ClipboardManager::class.java)
                val current = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                if (current == text) cm?.clearPrimaryClip()
            }
        }
        if (clipboardHistory.pinned != pinsBefore) {
            try {
                pinStore.save(clipboardHistory.pinned)
            } catch (e: IOException) {
                Log.w(TAG, "saving pinned clips failed", e)
            }
        }
        keyboardView?.invalidate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        keyboardView?.reload()
        updateNavigationBarAppearance()
    }

    private fun updateNavigationBarAppearance() {
        val light = Palette.of(resources.configuration) == Palette.LIGHT
        window?.window?.insetsController?.setSystemBarsAppearance(
            if (light) WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (!prefs.centerClipboard) clipboardHistory.clearRecent()
        applySettings()
        feedback.reload()
        keyboardView?.reload()
    }

    override fun onStateChanged() {
        keyboardView?.invalidate()
    }

    override fun onPreedit(text: String) {
        keyboardView?.setPreedit(text)
    }

    override fun onEcho(token: String) {
        keyboardView?.echo(token)
    }

    override fun onCommand(command: Command) {
        when (command) {
            Command.SETTINGS -> startActivity(
                Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            Command.SWITCH_IME -> if (!switchToNextInputMethod(false)) {
                getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
            }
            Command.HIDE -> requestHideSelf(0)
            Command.TOGGLE_SPLIT -> prefs.toggleSplit(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            Command.PASTE, Command.SELECT_ALL, Command.COPY -> Unit
        }
    }

    private fun applySettings() {
        engine.settings = EngineSettings(escToLatin = prefs.escToLatin, terminalEcho = prefs.terminalEcho)
        clipboardHistory.capacity = prefs.clipCount
        clipboardHistory.ttlMs = prefs.clipHours * HOUR_MS
    }

    private fun contextOf(info: EditorInfo): EditorContext {
        val type = info.inputType
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        val raw = cls == InputType.TYPE_NULL || info.packageName in prefs.rawPackages
        val forceAscii = (info.imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII) != 0
        val latin = raw || forceAscii || cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME || (cls == InputType.TYPE_CLASS_TEXT && variation in LATIN_VARIATIONS)
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val enterAction = if (raw || noEnterAction || action == EditorInfo.IME_ACTION_NONE) null else action
        val secret = (cls == InputType.TYPE_CLASS_TEXT && variation in PASSWORD_VARIATIONS) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        return EditorContext(
            raw = raw,
            enterAction = enterAction,
            preferLatin = latin,
            packageName = info.packageName,
            secret = secret,
        )
    }

    companion object {
        private const val ECHO_CHARS = 120
        private const val PIN_FILE = "clip_pins.bin"
        private const val HOUR_MS = 60 * 60 * 1000L
        private const val TAG = "FoldKey"
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
        private val PASSWORD_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
        private val LATIN_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        )
    }
}
