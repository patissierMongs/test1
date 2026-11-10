package io.github.patissiermongs.foldkey.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import io.github.patissiermongs.foldkey.R
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.ime.ScreenProfile
import io.github.patissiermongs.foldkey.ui.Dpi
import io.github.patissiermongs.foldkey.ui.LayoutBuilder
import io.github.patissiermongs.foldkey.ui.SplitEditModel
import io.github.patissiermongs.foldkey.ui.SplitEditModel.Handle
import io.github.patissiermongs.foldkey.ui.SplitEditModel.Sizes
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w900dp-h480dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LayoutEditorActivityTest {
    private fun all(v: View): List<View> = if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else listOf(v)

    private fun drag(view: View, fromX: Float, toX: Float) {
        val y = view.height / 2f
        val t = SystemClock.uptimeMillis()
        val steps = 6
        val points = (0..steps).map { fromX + (toX - fromX) * it / steps }
        points.forEachIndexed { i, x ->
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                points.lastIndex -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val e = MotionEvent.obtain(t, t + i * 8L, action, x, y, 0)
            view.dispatchTouchEvent(e)
            e.recycle()
        }
    }

    private fun render(root: View, name: String) {
        val dir = File("build/render").apply { mkdirs() }
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        FileOutputStream(File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun button(root: View, text: String): Button = all(root).filterIsInstance<Button>().first { it.text.toString().startsWith(text) }

    @Test
    fun draggingTheHandlesSetsEachHalfSeparatelyAndSavesAtOnce() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext<Context>())
        prefs.sp.edit().clear().commit()
        val controller = Robolectric.buildActivity(LayoutEditorActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.window.decorView
        val profile = ScreenProfile.of(activity)
        val view = all(root).filterIsInstance<LayoutEditView>().single()
        val model = view.model!!
        val px = Dpi.physical(view.resources.displayMetrics, true) / 25.4f
        assertTrue(view.width > 0)
        assertEquals(Sizes(0.5f, 8.5f, 8.5f, 0.5f), view.sizes)
        assertNull(view.selected)
        assertFalse(button(root, "−").isEnabled)
        assertFalse(prefs.hasHalfWidths(profile))

        val row = model.handleRow(view.sizes!!)
        val rightInner = model.handleMm(view.sizes!!, Handle.RIGHT_INNER, row) * px
        drag(view, rightInner, rightInner - 6f * px)
        val afterRight = view.sizes!!
        assertEquals(Handle.RIGHT_INNER, view.selected)
        assertEquals(8.5f + 6f / 6.75f, afterRight.rightUnitMm, 0.1f)
        assertEquals(8.5f, afterRight.leftUnitMm, 0f)
        assertTrue(button(root, "−").isEnabled)
        assertEquals(85, prefs.sp.getInt(Prefs.widthLeftKey(profile), 0))
        assertEquals(SplitEditModel.tenths(afterRight.rightUnitMm), prefs.sp.getInt(Prefs.widthRightKey(profile), 0))
        assertEquals(5, prefs.sp.getInt(Prefs.CODE_SIDE_LEFT, 0))
        val units = LayoutBuilder.codeSplitUnits(prefs, profile)
        assertEquals(8.5f, units.leftMm, 0f)
        assertEquals(afterRight.rightUnitMm, units.rightMm, 0.0001f)
        assertTrue(units.halves)

        val leftOuter = model.handleMm(afterRight, Handle.LEFT_OUTER, row) * px
        drag(view, leftOuter, leftOuter + 5f * px)
        assertEquals(Handle.LEFT_OUTER, view.selected)
        assertEquals(5.5f, view.sizes!!.leftSideMm, 0.1f)
        assertEquals(afterRight.rightUnitMm, view.sizes!!.rightUnitMm, 0f)
        assertEquals(view.sizes!!.leftSideMm, prefs.codeSideLeftMm, 0.0001f)
        assertEquals(0.5f, prefs.codeSideRightMm, 0f)

        val before = view.sizes!!.leftSideMm
        button(root, "+").performClick()
        assertEquals(before + 0.1f, view.sizes!!.leftSideMm, 0.0001f)
        assertEquals(view.sizes!!.leftSideMm, prefs.codeSideLeftMm, 0.0001f)
        button(root, "−").performClick()
        button(root, "−").performClick()
        assertEquals(before - 0.1f, view.sizes!!.leftSideMm, 0.0001f)
        controller.pause().stop().destroy()
    }

    @Test
    fun theTextLayerKeepsItsOwnHalvesAndRestoreBringsBackTheEntryValues() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext<Context>())
        prefs.sp.edit().clear().commit()
        val controller = Robolectric.buildActivity(LayoutEditorActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.window.decorView
        val profile = ScreenProfile.of(activity)
        val view = all(root).filterIsInstance<LayoutEditView>().single()
        val px = Dpi.physical(view.resources.displayMetrics, true) / 25.4f
        val codeRow = view.model!!.handleRow(view.sizes!!)
        val codeInner = view.model!!.handleMm(view.sizes!!, Handle.LEFT_INNER, codeRow) * px
        drag(view, codeInner, codeInner + 4f * px)
        val code = view.sizes!!
        assertEquals(Layer.CODE, view.layer)

        button(root, "Layer: code").performClick()
        assertEquals(Layer.GENERAL, view.layer)
        assertNull(view.selected)
        assertNotNull(view.sizes)
        val generalModel = view.model!!
        val generalRow = generalModel.handleRow(view.sizes!!)
        assertEquals(1, generalRow)
        val before = view.sizes!!
        val generalInner = generalModel.handleMm(before, Handle.RIGHT_INNER, generalRow) * px
        drag(view, generalInner, generalInner - 3f * px)
        assertTrue(view.sizes!!.rightUnitMm > before.rightUnitMm)
        assertEquals(before.leftUnitMm, view.sizes!!.leftUnitMm, 0f)
        assertTrue(prefs.hasGeneralHalfWidths)
        assertEquals(SplitEditModel.tenths(view.sizes!!.rightUnitMm), prefs.sp.getInt(Prefs.GENERAL_UNIT_RIGHT, 0))
        assertEquals(SplitEditModel.tenths(code.leftUnitMm), prefs.sp.getInt(Prefs.widthLeftKey(profile), 0))

        shadowOf(Looper.getMainLooper()).idle()
        render(root, "layout_editor_text.png")

        button(root, "Restore").performClick()
        assertFalse(prefs.hasGeneralHalfWidths)
        assertEquals(before, view.sizes)
        button(root, "Layer: text").performClick()
        assertEquals(Layer.CODE, view.layer)
        assertEquals(SplitEditModel.tenths(code.leftUnitMm), prefs.sp.getInt(Prefs.widthLeftKey(profile), 0))
        render(root, "layout_editor_code.png")
        button(root, "Restore").performClick()
        assertFalse(prefs.hasHalfWidths(profile))
        assertEquals(Sizes(0.5f, 8.5f, 8.5f, 0.5f), view.sizes)
        controller.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w832dp-h750dp-land-420dpi")
    fun theEditorFitsTheFoldableInnerScreenWithoutOverlap() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext<Context>())
        prefs.sp.edit().clear().commit()
        val controller = Robolectric.buildActivity(LayoutEditorActivity::class.java).setup()
        val root = controller.get().window.decorView
        val view = all(root).filterIsInstance<LayoutEditView>().single()
        val panel = (view.parent as ViewGroup).getChildAt(0)
        val px = Dpi.physical(view.resources.displayMetrics, true) / 25.4f
        assertTrue(panel.bottom <= view.top)
        assertTrue(view.height > 60f * px)
        val row = view.model!!.handleRow(view.sizes!!)
        val inner = view.model!!.handleMm(view.sizes!!, Handle.LEFT_INNER, row) * px
        drag(view, inner, inner + 4f * px)
        render(root, "layout_editor_inner_code.png")
        button(root, "Layer: code").performClick()
        render(root, "layout_editor_inner_text.png")
        controller.pause().stop().destroy()
    }

    @Test
    fun theBandWithTheHandlesIsExcludedFromTheSystemGestures() {
        val controller = Robolectric.buildActivity(LayoutEditorActivity::class.java).setup()
        val view = all(controller.get().window.decorView).filterIsInstance<LayoutEditView>().single()
        val limit = (LayoutEditView.GESTURE_EXCLUSION_DP * view.resources.displayMetrics.density).toInt()
        val rect = view.systemGestureExclusionRects.single()
        assertEquals(0, rect.left)
        assertEquals(view.width, rect.right)
        assertEquals(limit, rect.height())
        assertTrue(rect.bottom <= view.height)
        controller.pause().stop().destroy()
    }

    @Test
    fun theEditorStartsFromTheSavedHalvesOfBothLayers() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext<Context>())
        prefs.sp.edit().clear().commit()
        val controller = Robolectric.buildActivity(LayoutEditorActivity::class.java).create()
        val profile = ScreenProfile.of(controller.get())
        prefs.saveCodeHalves(profile, 80, 92, 20, 30)
        prefs.saveGeneralHalves(75, 99, 40, 50)
        controller.start().postCreate(null).resume().visible().windowFocusChanged(true)
        val root = controller.get().window.decorView
        val view = all(root).filterIsInstance<LayoutEditView>().single()
        assertEquals(Sizes(2.0f, 8.0f, 9.2f, 3.0f), view.sizes)
        button(root, "Layer: code").performClick()
        assertEquals(Layer.GENERAL, view.layer)
        assertEquals(Sizes(4.0f, 7.5f, 9.9f, 5.0f), view.sizes)
        controller.pause().stop().destroy()
    }

    @Test
    fun valueLabelsStayInsideTheScreenEvenAtTheEdges() {
        val controller = Robolectric.buildActivity(LayoutEditorActivity::class.java).setup()
        val view = all(controller.get().window.decorView).filterIsInstance<LayoutEditView>().single()
        val labelWidth = 60f
        assertTrue(view.onScreen(0f, labelWidth) - labelWidth / 2f >= 0f)
        assertTrue(view.onScreen(view.width.toFloat(), labelWidth) + labelWidth / 2f <= view.width)
        assertEquals(400f, view.onScreen(400f, labelWidth), 0f)
        controller.pause().stop().destroy()
    }

    @Test
    fun theSettingsScreenOpensTheEditor() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext<Context>())
        prefs.sp.edit().clear().commit()
        prefs.setupShown = true
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        val open = all(activity.window.decorView).filterIsInstance<Button>()
            .first { it.text.toString() == activity.getString(R.string.settings_edit) }
        open.performClick()
        assertEquals(LayoutEditorActivity::class.java.name, shadowOf(activity).nextStartedActivity.component?.className)
        controller.pause().stop().destroy()
    }

    @Test
    fun aTouchAwayFromTheHandlesChangesNothing() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext<Context>())
        prefs.sp.edit().clear().commit()
        val controller = Robolectric.buildActivity(LayoutEditorActivity::class.java).setup()
        val view = all(controller.get().window.decorView).filterIsInstance<LayoutEditView>().single()
        val px = Dpi.physical(view.resources.displayMetrics, true) / 25.4f
        val before = view.sizes
        val model = view.model!!
        val row = model.handleRow(before!!)
        val between = (model.handleMm(before, Handle.LEFT_INNER, row) + model.handleMm(before, Handle.RIGHT_INNER, row)) / 2f * px
        drag(view, between, between + 20f * px)
        assertEquals(before, view.sizes)
        assertNull(view.selected)
        assertFalse(prefs.hasHalfWidths(ScreenProfile.of(controller.get())))
        controller.pause().stop().destroy()
    }
}
