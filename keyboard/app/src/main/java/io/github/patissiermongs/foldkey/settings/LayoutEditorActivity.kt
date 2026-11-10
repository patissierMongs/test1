package io.github.patissiermongs.foldkey.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.patissiermongs.foldkey.R
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.ime.ScreenProfile
import io.github.patissiermongs.foldkey.layout.KeyboardGeometry
import io.github.patissiermongs.foldkey.layout.LayoutKind
import io.github.patissiermongs.foldkey.layout.Layouts
import io.github.patissiermongs.foldkey.ui.Dpi
import io.github.patissiermongs.foldkey.ui.KeyboardView
import io.github.patissiermongs.foldkey.ui.LayoutBuilder
import io.github.patissiermongs.foldkey.ui.Palette
import io.github.patissiermongs.foldkey.ui.SplitEditModel
import io.github.patissiermongs.foldkey.ui.SplitEditModel.Handle
import io.github.patissiermongs.foldkey.ui.SplitEditModel.Sizes
import kotlin.math.abs

class LayoutEditorActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var view: LayoutEditView
    private lateinit var status: TextView
    private lateinit var layerButton: Button
    private lateinit var minus: Button
    private lateinit var plus: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val palette = Palette.of(resources.configuration)
        view = LayoutEditView(this, prefs, palette) { update() }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        panel.addView(TextView(this).apply {
            text = getString(R.string.edit_instructions)
            textSize = 15f
            setTextColor(palette.text)
        })
        status = TextView(this).apply {
            textSize = 15f
            setTextColor(palette.text)
            setPadding(0, dp(6), 0, dp(6))
        }
        panel.addView(status)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        layerButton = button("") {
            view.switchLayer()
            update()
        }
        buttons.addView(layerButton)
        minus = button(getString(R.string.edit_minus)) { view.nudge(-SplitEditModel.STEP_MM) }
        buttons.addView(minus)
        plus = button(getString(R.string.edit_plus)) { view.nudge(SplitEditModel.STEP_MM) }
        buttons.addView(plus)
        buttons.addView(button(getString(R.string.edit_restore)) { view.restore() })
        buttons.addView(button(getString(R.string.edit_close)) { finish() })
        panel.addView(buttons)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val pad = dp(16)
            panel.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, pad)
            view.setInsets(bars.left, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        update()
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun handleName(handle: Handle): String = getString(
        when (handle) {
            Handle.LEFT_OUTER -> R.string.edit_left_side
            Handle.LEFT_INNER -> R.string.edit_left_unit
            Handle.RIGHT_INNER -> R.string.edit_right_unit
            Handle.RIGHT_OUTER -> R.string.edit_right_side
        }
    )

    private fun update() {
        val lines = ArrayList<String>()
        if (!view.usesSplit()) lines.add(getString(R.string.edit_no_split))
        val s = view.sizes
        val model = view.model
        if (s != null && model != null) {
            lines.add(getString(R.string.edit_status, s.leftSideMm, s.leftUnitMm, s.rightUnitMm, s.rightSideMm, model.gapMm(s)))
            view.selected?.let { lines.add(getString(R.string.edit_selected, handleName(it), model.value(s, it))) }
        }
        status.text = lines.joinToString("\n")
        layerButton.text = getString(if (view.layer == Layer.CODE) R.string.edit_layer_code else R.string.edit_layer_general)
        val enabled = view.selected != null
        minus.isEnabled = enabled
        plus.isEnabled = enabled
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

@SuppressLint("ViewConstructor")
internal class LayoutEditView(
    context: Context,
    private val prefs: Prefs,
    private val palette: Palette,
    private val onChange: () -> Unit,
) : View(context) {
    var layer = Layer.CODE
        private set
    var sizes: Sizes? = null
        private set
    var model: SplitEditModel? = null
        private set
    var selected: Handle? = null
        private set
    private val profile = ScreenProfile.of(context)
    private var leftInset = 0
    private var rightInset = 0
    private var bottomInset = 0
    private var dragHandle: Handle? = null
    private var dragRow = 0
    private var grabMm = 0f
    private var dragged = false
    private val entry = HashMap<Layer, Map<String, Int?>>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val pxPerMmX: Float get() = Dpi.physical(resources.displayMetrics, true) / KeyboardView.MM_PER_INCH
    private val pxPerMmY: Float get() = Dpi.physical(resources.displayMetrics, false) / KeyboardView.MM_PER_INCH
    private val baseline: Float get() = height - bottomInset.toFloat()
    private val rowMm: Float get() = prefs.rowHeightMm(profile)
    private val liftPx: Float get() = prefs.splitLiftMm * pxPerMmY
    private val rowCount: Int get() = Layouts.rows(LayoutKind.SPLIT, layer)

    fun usesSplit(): Boolean = prefs.usesSplit(profile)

    fun setInsets(left: Int, right: Int, bottom: Int) {
        leftInset = left
        rightInset = right
        bottomInset = bottom
        excludeSystemGestures()
        if (width > 0) load()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        excludeSystemGestures()
        if (w > 0) load()
    }

    private fun excludeSystemGestures() {
        if (width <= 0) return
        val bottom = baseline.toInt()
        val limit = (GESTURE_EXCLUSION_DP * resources.displayMetrics.density).toInt()
        systemGestureExclusionRects = listOf(Rect(0, maxOf(0, bottom - limit), width, bottom))
    }

    private fun keys(l: Layer): List<String> =
        if (l == Layer.GENERAL) listOf(Prefs.GENERAL_UNIT_LEFT, Prefs.GENERAL_UNIT_RIGHT, Prefs.GENERAL_UNIT, Prefs.GENERAL_SIDE_LEFT, Prefs.GENERAL_SIDE_RIGHT)
        else listOf(Prefs.widthLeftKey(profile), Prefs.widthRightKey(profile), Prefs.widthKey(profile), Prefs.CODE_SIDE_LEFT, Prefs.CODE_SIDE_RIGHT)

    private fun spec() = LayoutBuilder.spec(
        prefs, LayoutKind.SPLIT, layer, profile, width.toFloat(), pxPerMmX, pxPerMmY, 0f, rowMm,
        sideInsetPx = maxOf(leftInset, rightInset).toFloat(),
        liftPx = liftPx,
        panelMinPx = if (prefs.centerClipboard) KeyboardView.PANEL_MIN_MM * pxPerMmX else 0f,
    )

    private fun load() {
        if (width <= 0) return
        entry.getOrPut(layer) { keys(layer).associateWith { if (prefs.sp.contains(it)) prefs.sp.getInt(it, 0) else null } }
        val rows = if (layer == Layer.GENERAL) Layouts.generalSplit else Layouts.split
        val m = SplitEditModel(rows, width / pxPerMmX, KeyboardView.SIDE_MM + maxOf(leftInset, rightInset) / pxPerMmX)
        val g = LayoutBuilder.build(LayoutKind.SPLIT, layer, spec())
        val sides = LayoutBuilder.sides(prefs, layer)
        model = m
        sizes = m.floorSizes(Sizes(sides.leftMm, g.leftUnitPx / pxPerMmX, g.rightUnitPx / pxPerMmX, sides.rightMm))
        onChange()
        invalidate()
    }

    fun switchLayer() {
        layer = if (layer == Layer.CODE) Layer.GENERAL else Layer.CODE
        selected = null
        load()
    }

    fun nudge(deltaMm: Float) {
        val m = model ?: return
        val s = sizes ?: return
        val h = selected ?: return
        sizes = m.nudge(s, h, deltaMm)
        commit()
        onChange()
        invalidate()
    }

    fun restore() {
        val snapshot = entry[layer] ?: return
        val editor = prefs.sp.edit()
        for ((key, value) in snapshot) if (value == null) editor.remove(key) else editor.putInt(key, value)
        editor.apply()
        selected = null
        load()
    }

    private fun commit() {
        val s = sizes ?: return
        fun t(mm: Float) = SplitEditModel.tenths(mm)
        if (layer == Layer.GENERAL) prefs.saveGeneralHalves(t(s.leftUnitMm), t(s.rightUnitMm), t(s.leftSideMm), t(s.rightSideMm))
        else prefs.saveCodeHalves(profile, t(s.leftUnitMm), t(s.rightUnitMm), t(s.leftSideMm), t(s.rightSideMm))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val m = model ?: return false
        val s = sizes ?: return false
        val xMm = event.x / pxPerMmX
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val row = m.handleRow(s)
                val near = Handle.entries.minByOrNull { abs(m.handleMm(s, it, row) - xMm) } ?: return true
                val offset = xMm - m.handleMm(s, near, row)
                if (abs(offset) > GRAB_MM) return true
                dragHandle = near
                dragRow = row
                grabMm = offset
                dragged = false
                selected = near
                onChange()
                invalidate()
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val h = dragHandle ?: return true
                val next = m.drag(s, h, xMm - grabMm, dragRow)
                if (next != s) {
                    sizes = next
                    dragged = true
                    onChange()
                    invalidate()
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) finishDrag()
            }
            MotionEvent.ACTION_CANCEL -> finishDrag()
        }
        return true
    }

    private fun finishDrag() {
        if (dragHandle != null && dragged) commit()
        dragHandle = null
        dragged = false
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.background)
        val m = model ?: return
        val s = sizes ?: return
        if (width == 0) return
        val px = pxPerMmX
        val py = pxPerMmY
        val base = spec().copy(
            splitUnitMm = s.leftUnitMm,
            splitUnitRightMm = s.rightUnitMm,
            splitMarginLeftPx = s.leftSideMm * px,
            splitMarginRightPx = s.rightSideMm * px,
        )
        val geometry = LayoutBuilder.build(LayoutKind.SPLIT, layer, base)
        val rowsHeight = rowCount * rowMm * py
        val top = baseline - liftPx - rowsHeight
        canvas.save()
        canvas.translate(0f, top)
        drawKeys(canvas, geometry, px)
        canvas.restore()
        drawHandles(canvas, m, s, top, rowsHeight + liftPx)
    }

    private fun drawKeys(canvas: Canvas, geometry: KeyboardGeometry, px: Float) {
        val radius = KeyboardView.RADIUS_MM * px
        paint.textAlign = Paint.Align.CENTER
        for (key in geometry.keys) {
            if (key.ghost) continue
            rect.set(key.face.left, key.face.top, key.face.right, key.face.bottom)
            paint.style = Paint.Style.FILL
            paint.color = palette.key
            paint.alpha = 175
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.15f * px
            paint.color = palette.text
            paint.alpha = 120
            canvas.drawRoundRect(rect, radius, radius, paint)
            val label = LayoutBuilder.previewLabel(key.def)
            if (label.isNotEmpty()) {
                paint.style = Paint.Style.FILL
                paint.color = palette.text
                paint.alpha = 230
                paint.textSize = key.face.height * if (label.length == 1) 0.42f else 0.26f
                val max = key.face.width * 0.9f
                if (paint.measureText(label) > max) paint.textSize *= max / paint.measureText(label)
                canvas.drawText(label, key.face.centerX, key.face.centerY - (paint.descent() + paint.ascent()) / 2f, paint)
            }
        }
        val hint = context.getString(R.string.edit_panel)
        paint.pathEffect = DashPathEffect(floatArrayOf(1.5f * px, 1.5f * px), 0f)
        for (box in geometry.panel) {
            rect.set(box.left, box.top + 0.45f * px, box.right, box.bottom - 0.45f * px)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.2f * px
            paint.color = palette.hint
            paint.alpha = 200
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        paint.pathEffect = null
        if (geometry.panel.isNotEmpty()) {
            val first = geometry.panel.first()
            paint.style = Paint.Style.FILL
            paint.color = palette.hint
            paint.alpha = 220
            paint.textSize = 2.6f * px
            val max = first.width * 0.95f
            if (paint.measureText(hint) > max) paint.textSize *= max / paint.measureText(hint)
            canvas.drawText(hint, first.centerX, first.top + 4f * px, paint)
        }
    }

    private fun drawHandles(canvas: Canvas, m: SplitEditModel, s: Sizes, top: Float, height: Float) {
        val px = pxPerMmX
        val row = dragHandle?.let { dragRow } ?: m.handleRow(s)
        paint.textAlign = Paint.Align.CENTER
        for (handle in Handle.entries) {
            val x = m.handleMm(s, handle, row) * px
            val left = handle == Handle.LEFT_OUTER || handle == Handle.LEFT_INNER
            val color = if (left) palette.accent else palette.locked
            val active = handle == selected
            paint.style = Paint.Style.FILL
            paint.color = color
            paint.alpha = if (active) 255 else 150
            rect.set(x - 0.4f * px, top, x + 0.4f * px, top + height)
            canvas.drawRect(rect, paint)
            val cy = top + height / 2f
            canvas.drawCircle(x, cy, (if (active) 5.5f else 4.2f) * px, paint)
            paint.color = palette.accentText
            paint.alpha = 255
            paint.textSize = 2.4f * px
            val mark = if (handle == Handle.LEFT_OUTER || handle == Handle.RIGHT_OUTER) "↔" else "⇹"
            canvas.drawText(mark, x, cy - (paint.descent() + paint.ascent()) / 2f, paint)
            paint.color = color
            paint.alpha = 255
            paint.textSize = 3.2f * px
            val label = "%.1f".format(m.value(s, handle))
            val labelY = top - (if (handle == Handle.LEFT_INNER || handle == Handle.RIGHT_INNER) 1f else 5f) * px
            canvas.drawText(label, onScreen(x, paint.measureText(label)), labelY, paint)
        }
        paint.color = palette.text
        paint.alpha = 255
        paint.textSize = 3.2f * px
        val gapMid = (m.handleMm(s, Handle.LEFT_INNER, row) + m.handleMm(s, Handle.RIGHT_INNER, row)) / 2f * px
        val gapLabel = context.getString(R.string.edit_gap, m.gapMm(s))
        canvas.drawText(gapLabel, onScreen(gapMid, paint.measureText(gapLabel)), top - 9f * px, paint)
        paint.alpha = 255
    }

    internal fun onScreen(center: Float, textWidth: Float): Float {
        val half = textWidth / 2f + 0.5f * pxPerMmX
        return center.coerceIn(half, maxOf(half, width - half))
    }

    companion object {
        const val GRAB_MM = 10f
        const val GESTURE_EXCLUSION_DP = 200f
    }
}
