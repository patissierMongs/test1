package io.github.patissiermongs.foldkey.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.layout.Key

class HapticFeedback(context: Context, private val prefs: Prefs) : Feedback {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private var pressEffect: VibrationEffect? = null
    private var detentEffect: VibrationEffect? = null

    init {
        reload()
    }

    override fun reload() {
        val v = vibrator
        if (v == null || !v.hasVibrator() || prefs.hapticLevel == LEVEL_SYSTEM) {
            pressEffect = null
            detentEffect = null
            return
        }
        pressEffect = VibrationEffect.createPredefined(
            if (prefs.hapticLevel == LEVEL_TICK) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK
        )
        detentEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    }

    override fun press(view: View, key: Key) {
        if (prefs.haptic) play(view, pressEffect, HapticFeedbackConstants.KEYBOARD_TAP)
        if (prefs.sound && audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val fx = when (key.def.action) {
                KeyAction.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyAction.Backspace -> AudioManager.FX_KEYPRESS_DELETE
                KeyAction.Enter -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio?.playSoundEffect(fx, -1f)
        }
    }

    override fun detent(view: View) {
        if (prefs.haptic) play(view, detentEffect, HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun play(view: View, effect: VibrationEffect?, fallback: Int) {
        val v = vibrator
        if (effect == null || v == null) {
            view.performHapticFeedback(fallback)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, TOUCH_AUDIO_ATTRIBUTES)
        }
    }

    companion object {
        const val LEVEL_SYSTEM = 0
        const val LEVEL_TICK = 1
        private val TOUCH_AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}
