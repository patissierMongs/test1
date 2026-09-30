package io.github.patissiermongs.foldkey.ui

import android.view.View
import io.github.patissiermongs.foldkey.layout.Key

interface Feedback {
    fun press(view: View, key: Key)

    fun detent(view: View)

    fun reload() {}
}
