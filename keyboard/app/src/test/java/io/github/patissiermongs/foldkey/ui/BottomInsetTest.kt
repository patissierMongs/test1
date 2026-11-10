package io.github.patissiermongs.foldkey.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Insets
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import io.github.patissiermongs.foldkey.engine.FakeEditor
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.RecordingListener
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.layout.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w750dp-h832dp-420dpi")
class BottomInsetTest {
    private object Silent : Feedback {
        override fun press(view: View, key: Key) = Unit
        override fun detent(view: View) = Unit
    }

    private class ImeNavBarHost(context: Context) : FrameLayout(context) {
        var navBarPx = 0
        var dispatches = 0

        override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
            dispatches++
            val withBar = WindowInsets.Builder(insets)
                .setInsets(WindowInsets.Type.captionBar(), Insets.of(0, 0, 0, navBarPx))
                .build()
            return super.dispatchApplyWindowInsets(withBar)
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun shownImeWindow(navBarPx: Int): ImeNavBarHost {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        Prefs(activity).sp.edit().clear().putBoolean(Prefs.SPLIT_PORTRAIT, true).commit()
        val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_InputMethod)
        val window = dialog.window!!
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
        )
        val host = ImeNavBarHost(dialog.context)
        host.navBarPx = navBarPx
        dialog.setContentView(host)
        dialog.show()
        idle()
        return host
    }

    private fun addKeyboard(host: ImeNavBarHost): KeyboardView {
        val ctx = host.context
        val view = KeyboardView(ctx, KeyboardEngine(FakeEditor(), RecordingListener()), Prefs(ctx), Silent)
        host.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        idle()
        return view
    }

    private fun lowestKeyEdge(view: KeyboardView): Float =
        view.layoutKeys.filter { !it.ghost }.maxOf { maxOf(it.face.bottom, it.touch.bottom) }

    @Test
    fun keyboardCreatedAfterTheLastInsetsDispatchStaysAboveTheImeNavigationBar() {
        val host = shownImeWindow(NAV_BAR_PX)
        assertTrue(host.dispatches > 0)
        val view = addKeyboard(host)
        assertTrue(view.height > NAV_BAR_PX)
        assertTrue(lowestKeyEdge(view) <= view.height - NAV_BAR_PX + 1f)
    }

    @Test
    fun keyboardHeightFollowsLaterNavigationBarChanges() {
        val host = shownImeWindow(0)
        val view = addKeyboard(host)
        val bare = view.height
        host.navBarPx = NAV_BAR_PX
        host.requestApplyInsets()
        idle()
        assertEquals((bare + NAV_BAR_PX).toDouble(), view.height.toDouble(), 1.0)
        assertTrue(lowestKeyEdge(view) <= view.height - NAV_BAR_PX + 1f)
        host.navBarPx = 0
        host.requestApplyInsets()
        idle()
        assertEquals(bare, view.height)
    }

    companion object {
        const val NAV_BAR_PX = 126
    }
}
