package io.github.patissiermongs.foldkey.ui

import android.content.res.Configuration

enum class Presentation { DOCKED, HOVER, COLLAPSED, CHIP }

class Overlay {
    var summoned = false
    var collapsed = false
    var passThrough = false

    fun presentation(configuration: Configuration, hover: Boolean): Presentation = when {
        hardwareKeyboard(configuration) && !summoned -> Presentation.CHIP
        !hover -> Presentation.DOCKED
        collapsed -> Presentation.COLLAPSED
        else -> Presentation.HOVER
    }

    fun reset() {
        summoned = false
        collapsed = false
    }

    companion object {
        fun hardwareKeyboard(configuration: Configuration): Boolean =
            configuration.keyboard == Configuration.KEYBOARD_QWERTY &&
                configuration.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
    }
}
