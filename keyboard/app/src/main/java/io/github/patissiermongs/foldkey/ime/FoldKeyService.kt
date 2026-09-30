package io.github.patissiermongs.foldkey.ime

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.text.InputType
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
import io.github.patissiermongs.foldkey.ui.HapticFeedback
import io.github.patissiermongs.foldkey.ui.KeyboardView
import io.github.patissiermongs.foldkey.ui.Palette

class FoldKeyService : InputMethodService(), EngineListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: Prefs
    private lateinit var engine: KeyboardEngine
    private lateinit var feedback: HapticFeedback
    private var keyboardView: KeyboardView? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        feedback = HapticFeedback(this, prefs)
        engine = KeyboardEngine(InputConnectionEditor(this) { currentInputConnection }, this)
        applySettings()
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        keyboardView?.saveState()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        keyboardView?.saveState()
        val view = KeyboardView(this, engine, prefs, feedback)
        keyboardView = view
        updateNavigationBarAppearance()
        return view
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        engine.startInput(contextOf(info), restarting)
        keyboardView?.cancelTouches()
        keyboardView?.showSwitchKey = shouldOfferSwitchingToNextInputMethod()
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
            Command.PASTE -> Unit
        }
    }

    private fun applySettings() {
        engine.settings = EngineSettings(escToLatin = prefs.escToLatin)
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
        return EditorContext(raw = raw, enterAction = enterAction, preferLatin = latin, packageName = info.packageName)
    }

    companion object {
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
