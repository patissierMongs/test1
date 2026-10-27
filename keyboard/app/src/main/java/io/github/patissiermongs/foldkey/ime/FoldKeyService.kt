package io.github.patissiermongs.foldkey.ime

import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import io.github.patissiermongs.foldkey.engine.Command
import io.github.patissiermongs.foldkey.engine.EditorContext
import io.github.patissiermongs.foldkey.engine.EngineListener
import io.github.patissiermongs.foldkey.engine.EngineSettings
import io.github.patissiermongs.foldkey.engine.HardwareKeys
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.settings.SettingsActivity
import io.github.patissiermongs.foldkey.ui.Clip
import io.github.patissiermongs.foldkey.ui.ClipEdit
import io.github.patissiermongs.foldkey.ui.HapticFeedback
import io.github.patissiermongs.foldkey.ui.KeyboardView
import io.github.patissiermongs.foldkey.ui.Overlay
import io.github.patissiermongs.foldkey.ui.Palette
import java.io.File
import java.io.IOException

class FoldKeyService : InputMethodService(), EngineListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: Prefs
    private lateinit var engine: KeyboardEngine
    private lateinit var feedback: HapticFeedback
    private var keyboardView: KeyboardView? = null
    private val clipboardHistory = ClipboardHistory()
    private lateinit var clipStore: ClipStore
    private var savedClips: List<ClipboardHistory.Entry> = emptyList()
    private var lastClip: Pair<Long, String>? = null
    private var locked = false
    private lateinit var hardware: HardwareKeys
    private val overlay = Overlay()
    private var startedInfo: EditorInfo? = null
    private var hardwareAttached = false
    private val location = IntArray(2)
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip(fromListener = true) }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        feedback = HapticFeedback(this, prefs)
        engine = KeyboardEngine(InputConnectionEditor(this) { currentInputConnection }, this)
        hardware = HardwareKeys(engine)
        hardwareAttached = Overlay.hardwareKeyboard(resources.configuration)
        clipStore = ClipStore(File(noBackupFilesDir, CLIP_FILE))
        applySettings()
        clipboardHistory.restore(clipStore.load(), System.currentTimeMillis())
        savedClips = clipboardHistory.entries
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
        val view = KeyboardView(this, engine, prefs, feedback, overlay)
        view.clipSource = {
            if (locked) emptyList() else clipboardHistory.items(System.currentTimeMillis()).map { Clip(it.text, it.pinned) }
        }
        view.onClipEdit = { edit, text -> editClip(edit, text) }
        view.pinsFull = { clipboardHistory.pinsFull }
        keyboardView = view
        updateNavigationBarAppearance()
        return view
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean =
        Overlay.hardwareKeyboard(resources.configuration) || super.onShowInputRequested(flags, configChange)

    override fun onWindowHidden() {
        super.onWindowHidden()
        overlay.reset()
        keyboardView?.reload()
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val view = keyboardView ?: return
        if (!view.floating || !view.isShown) return
        val decor = window?.window?.decorView ?: return
        outInsets.contentTopInsets = decor.height
        outInsets.visibleTopInsets = decor.height
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.setEmpty()
        view.getLocationInWindow(location)
        for (r in view.touchRects()) {
            r.offset(location[0], location[1])
            outInsets.touchableRegion.union(r)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        hardware.down(keyCode, event.metaState, event.repeatCount, event.unicodeChar) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        hardware.up(keyCode) || super.onKeyUp(keyCode, event)

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        startEngine(attribute, restarting)
    }

    private fun startEngine(info: EditorInfo, restarting: Boolean) {
        startedInfo = info
        engine.startInput(contextOf(info), restarting)
        val remote = info.packageName in prefs.remotePackages
        hardware.active = true
        hardware.passThrough = remote
        overlay.passThrough = remote
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) keyboardView?.cancelTouches()
        if (info !== startedInfo) startEngine(info, restarting)
        keyboardView?.syncLayer()
        locked = getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true
        captureClip()
        keyboardView?.showEditCommands = !engine.context.raw
        keyboardView?.invalidate()
    }

    override fun onFinishInput() {
        hardware.active = false
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
        engine.selectionChanged(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    private fun captureClip(fromListener: Boolean = false) {
        if (!prefs.centerClipboard || locked) return
        val clip = getSystemService(ClipboardManager::class.java)?.primaryClip ?: return
        if (clip.itemCount == 0) return
        if (clip.description?.extras?.getBoolean(EXTRA_IS_SENSITIVE, false) == true) return
        val text = clip.getItemAt(0).text?.toString() ?: return
        val key = (clip.description?.timestamp ?: 0L) to text
        if (!fromListener && key == lastClip) return
        lastClip = key
        val changed = clipboardHistory.add(text, System.currentTimeMillis())
        saveClips()
        if (changed) keyboardView?.invalidate()
    }

    private fun editClip(edit: ClipEdit, text: String) {
        when (edit) {
            ClipEdit.PIN -> clipboardHistory.pin(text)
            ClipEdit.UNPIN -> clipboardHistory.unpin(text, System.currentTimeMillis())
            ClipEdit.DELETE -> {
                clipboardHistory.remove(text)
                val cm = getSystemService(ClipboardManager::class.java)
                val current = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                if (current == text) cm?.clearPrimaryClip()
            }
        }
        saveClips()
        keyboardView?.invalidate()
    }

    private fun saveClips() {
        val entries = clipboardHistory.entries
        if (entries == savedClips) return
        try {
            clipStore.save(entries)
            savedClips = entries
        } catch (e: IOException) {
            Log.w(TAG, "saving clips failed", e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        engine.commitComposition()
        val attached = Overlay.hardwareKeyboard(newConfig)
        if (attached != hardwareAttached) {
            hardwareAttached = attached
            overlay.reset()
        }
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
        saveClips()
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
            Command.HIDE -> if (Overlay.hardwareKeyboard(resources.configuration) && overlay.summoned) {
                overlay.reset()
                keyboardView?.reload()
            } else {
                requestHideSelf(0)
            }
            Command.TOGGLE_LAYER -> {
                if (prefs.layer != engine.preferredLayer) prefs.setLayer(engine.preferredLayer)
                keyboardView?.reload()
            }
            Command.PASTE, Command.SELECT_ALL, Command.COPY, Command.CUT, Command.FLOAT, Command.BEHIND -> Unit
        }
    }

    private fun applySettings() {
        engine.settings = EngineSettings(escToLatin = prefs.escToLatin, layer = prefs.layer)
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
            multiLine = cls == InputType.TYPE_CLASS_TEXT && (type and MULTI_LINE_FLAGS) != 0,
            selStart = info.initialSelStart,
            selEnd = info.initialSelEnd,
        )
    }

    companion object {
        private const val CLIP_FILE = "clip_pins.bin"
        private const val HOUR_MS = 60 * 60 * 1000L
        private const val TAG = "FoldKey"
        private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
        private const val MULTI_LINE_FLAGS = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE
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
