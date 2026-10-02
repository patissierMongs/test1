package io.github.patissiermongs.foldkey.settings

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ApplicationProvider
import io.github.patissiermongs.foldkey.ime.FoldKeyService
import io.github.patissiermongs.foldkey.ime.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SetupFlowTest {
    private fun activate(ctx: Context) {
        val info = InputMethodInfo::class.java
            .getConstructor(String::class.java, String::class.java, CharSequence::class.java, String::class.java)
            .newInstance(ctx.packageName, FoldKeyService::class.java.name, "FoldKey", null)
        shadowOf(ctx.getSystemService(InputMethodManager::class.java)).setEnabledInputMethodInfoList(listOf(info))
        Settings.Secure.putString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, info.id)
    }

    @Test
    fun setupFlowOpensOnceAfterTheKeyboardIsSelected() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Prefs(ctx).sp.edit().clear().commit()
        val before = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertNull(shadowOf(before).nextStartedActivity)
        activate(ctx)
        val first = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertEquals(ReachCalibrationActivity::class.java.name, shadowOf(first).nextStartedActivity?.component?.className)
        assertTrue(Prefs(ctx).setupShown)
        val second = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()
        assertNull(shadowOf(second).nextStartedActivity)
    }
}
