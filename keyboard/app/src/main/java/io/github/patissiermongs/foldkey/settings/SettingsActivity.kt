package io.github.patissiermongs.foldkey.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import io.github.patissiermongs.foldkey.R
import io.github.patissiermongs.foldkey.ime.FoldKeyService
import io.github.patissiermongs.foldkey.ime.Prefs

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val scroll = ScrollView(this)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
        }
        scroll.addView(column)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        build()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus()
    }

    private fun build() {
        heading(getString(R.string.app_name), 24f)
        body(getString(R.string.settings_intro))
        status = body("")
        button(getString(R.string.settings_enable)) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        button(getString(R.string.settings_pick)) {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }

        heading(getString(R.string.settings_try), 18f)
        column.addView(EditText(this).apply { hint = getString(R.string.settings_try_single); isSingleLine = true })
        column.addView(EditText(this).apply {
            hint = getString(R.string.settings_try_multi)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            typeface = Typeface.MONOSPACE
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
        })
        body(getString(R.string.settings_echo))
        val echo = KeyEchoView(this)
        column.addView(echo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        button(getString(R.string.settings_echo_clear)) { echo.clear() }

        heading(getString(R.string.settings_layout), 18f)
        toggle(getString(R.string.pref_split_portrait), Prefs.SPLIT_PORTRAIT, false)
        toggle(getString(R.string.pref_split_landscape), Prefs.SPLIT_LANDSCAPE, true)
        slider(getString(R.string.pref_row_height), Prefs.ROW_HEIGHT, 95, 70, 130) { "%.1f mm".format(it / 10f) }
        slider(getString(R.string.pref_split_unit), Prefs.SPLIT_UNIT, 85, 70, 110) { "%.1f mm".format(it / 10f) }
        toggle(getString(R.string.pref_latin_hints), Prefs.LATIN_HINTS, true)

        heading(getString(R.string.settings_feedback), 18f)
        toggle(getString(R.string.pref_haptic), Prefs.HAPTIC, true)
        slider(getString(R.string.pref_haptic_level), Prefs.HAPTIC_LEVEL, 0, 0, 2) {
            getString(
                when (it) {
                    0 -> R.string.haptic_system
                    1 -> R.string.haptic_tick
                    else -> R.string.haptic_click
                }
            )
        }
        toggle(getString(R.string.pref_sound), Prefs.SOUND, false)
        slider(getString(R.string.pref_popup), Prefs.POPUP_MODE, Prefs.POPUP_AUTO, Prefs.POPUP_OFF, Prefs.POPUP_ON) {
            getString(
                when (it) {
                    Prefs.POPUP_OFF -> R.string.off
                    Prefs.POPUP_ON -> R.string.on
                    else -> R.string.popup_auto
                }
            )
        }

        heading(getString(R.string.settings_input), 18f)
        toggle(getString(R.string.pref_swipe_down_ctrl), Prefs.SWIPE_DOWN_CTRL, true)
        toggle(getString(R.string.pref_esc_latin), Prefs.ESC_TO_LATIN, true)
        slider(getString(R.string.pref_long_press), Prefs.LONG_PRESS, 400, 0, 800) {
            if (it == 0) getString(R.string.off) else "$it ms"
        }
        toggle(getString(R.string.pref_adaptive), Prefs.ADAPTIVE, true)
        button(getString(R.string.pref_adaptive_reset)) { prefs.clearOffsets() }
        body(getString(R.string.pref_raw_packages))
        column.addView(EditText(this).apply {
            setText(prefs.sp.getString(Prefs.RAW_PACKAGES, Prefs.DEFAULT_RAW_PACKAGES))
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setOnFocusChangeListener { v, focused ->
                if (!focused) prefs.sp.edit().putString(Prefs.RAW_PACKAGES, (v as EditText).text.toString()).apply()
            }
        })

        heading(getString(R.string.settings_gestures), 18f)
        body(getString(R.string.settings_gestures_body))
    }

    private fun refreshStatus() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        val id = imm.enabledInputMethodList.firstOrNull { it.serviceName == FoldKeyService::class.java.name && it.packageName == packageName }?.id
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        status.text = getString(
            when {
                id == null -> R.string.status_disabled
                id == current -> R.string.status_active
                else -> R.string.status_enabled
            }
        )
    }

    private fun heading(text: String, size: Float): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(18), 0, dp(6))
        column.addView(this)
    }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setPadding(0, dp(4), 0, dp(4))
        column.addView(this)
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
        column.addView(this)
    }

    @Suppress("DEPRECATION")
    private fun toggle(text: String, key: String, default: Boolean) {
        column.addView(Switch(this).apply {
            this.text = text
            textSize = 15f
            isChecked = prefs.sp.getBoolean(key, default)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, checked -> prefs.sp.edit().putBoolean(key, checked).apply() }
        })
    }

    private fun slider(text: String, key: String, default: Int, min: Int, max: Int, format: (Int) -> String) {
        val label = TextView(this).apply { textSize = 15f; setPadding(0, dp(8), 0, 0) }
        val value = prefs.sp.getInt(key, default).coerceIn(min, max)
        label.text = getString(R.string.label_value, text, format(value))
        column.addView(label)
        column.addView(SeekBar(this).apply {
            this.min = min
            this.max = max
            progress = value
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    label.text = getString(R.string.label_value, text, format(progress))
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    prefs.sp.edit().putInt(key, seekBar.progress).apply()
                }
            })
        })
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
