package io.github.patissiermongs.foldkey.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.patissiermongs.foldkey.R
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.input.ReachCalibration
import io.github.patissiermongs.foldkey.input.ReachCalibration.Side
import io.github.patissiermongs.foldkey.layout.Layouts
import io.github.patissiermongs.foldkey.ui.Dpi
import io.github.patissiermongs.foldkey.ui.KeyboardView
import io.github.patissiermongs.foldkey.ui.Palette
import kotlin.math.roundToInt

class ReachCalibrationActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var reach: ReachView
    private lateinit var status: TextView
    private lateinit var apply: Button
    private var pendingUnit: Float? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val palette = Palette.of(resources.configuration)
        reach = ReachView(this, prefs, palette) { update() }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        panel.addView(TextView(this).apply {
            text = getString(R.string.reach_instructions)
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
        apply = button(getString(R.string.reach_apply)) {
            pendingUnit?.let { prefs.sp.edit().putInt(Prefs.SPLIT_UNIT, (it * 10f).roundToInt()).apply() }
            finish()
        }
        buttons.addView(apply)
        buttons.addView(button(getString(R.string.reach_reset)) { reach.reset() })
        buttons.addView(button(getString(R.string.reach_close)) { finish() })
        panel.addView(buttons)
        val root = FrameLayout(this)
        root.addView(reach, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val pad = dp(16)
            panel.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, pad)
            reach.bottomInset = bars.bottom
            reach.invalidate()
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

    private fun update() {
        val lines = ArrayList<String>()
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            lines.add(getString(R.string.reach_rotate))
        }
        val left = reach.reaches.getValue(Side.LEFT)
        val right = reach.reaches.getValue(Side.RIGHT)
        lines.add(getString(R.string.reach_progress, left.size, right.size, ReachCalibration.STROKES))
        if (reach.lastInvalid) lines.add(getString(R.string.reach_invalid))
        val l = ReachCalibration.summarize(left)
        val r = ReachCalibration.summarize(right)
        for ((name, list, summary) in listOf(
            Triple(getString(R.string.reach_left), left, l),
            Triple(getString(R.string.reach_right), right, r),
        )) {
            if (summary != null && list.size == ReachCalibration.STROKES && !summary.consistent) {
                lines.add(getString(R.string.reach_inconsistent, name, summary.spreadMm))
            }
        }
        pendingUnit = null
        if (l != null && r != null && left.size == ReachCalibration.STROKES && right.size == ReachCalibration.STROKES &&
            l.consistent && r.consistent
        ) {
            val unit = ReachCalibration.unitMm(
                l.medianMm, r.medianMm, KeyboardView.SIDE_MM,
                Layouts.splitLeftUnits, Layouts.splitRightUnits,
                UNIT_MIN_MM, UNIT_MAX_MM,
            )
            pendingUnit = unit
            lines.add(getString(R.string.reach_result, l.medianMm, r.medianMm, unit, prefs.splitUnitMm))
        }
        status.text = lines.joinToString("\n")
        apply.isEnabled = pendingUnit != null
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val UNIT_MIN_MM = 7.0f
        const val UNIT_MAX_MM = 11.0f
    }
}

@SuppressLint("ViewConstructor")
private class ReachView(
    context: Context,
    private val prefs: Prefs,
    private val palette: Palette,
    private val onChange: () -> Unit,
) : View(context) {
    val reaches = mapOf(Side.LEFT to ArrayList<Float>(), Side.RIGHT to ArrayList<Float>())
    var lastInvalid = false
    var bottomInset = 0
    private val strokes = HashMap<Int, Pair<Side, ArrayList<PointF>>>()
    private val finished = ArrayList<Pair<Side, List<PointF>>>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val pxPerMmX: Float get() = Dpi.physical(resources.displayMetrics, true) / MM_PER_INCH
    private val pxPerMmY: Float get() = Dpi.physical(resources.displayMetrics, false) / MM_PER_INCH
    private val bandBottom: Float get() = height - bottomInset.toFloat()
    private val bands: ReachCalibration.Bands
        get() = ReachCalibration.Bands(Layouts.split.size, prefs.rowHeightMm, prefs.splitLiftMm)

    fun reset() {
        reaches.values.forEach { it.clear() }
        strokes.clear()
        finished.clear()
        lastInvalid = false
        invalidate()
        onChange()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val side = if (event.getX(i) < width / 2f) Side.LEFT else Side.RIGHT
                strokes[event.getPointerId(i)] = side to arrayListOf(PointF(event.getX(i), event.getY(i)))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                val points = strokes[event.getPointerId(i)]?.second ?: continue
                for (h in 0 until event.historySize) points.add(PointF(event.getHistoricalX(i, h), event.getHistoricalY(i, h)))
                points.add(PointF(event.getX(i), event.getY(i)))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> finish(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_CANCEL -> strokes.clear()
        }
        invalidate()
        return true
    }

    private fun finish(pointer: Int) {
        val (side, points) = strokes.remove(pointer) ?: return
        val samples = points.map { ReachCalibration.Sample(it.x / pxPerMmX, (bandBottom - it.y) / pxPerMmY) }
        val reach = ReachCalibration.strokeReach(samples, side, width / pxPerMmX, bands)
        finished.add(side to points)
        while (finished.size > 2 * ReachCalibration.STROKES) finished.removeAt(0)
        lastInvalid = reach == null
        if (reach != null) {
            val list = reaches.getValue(side)
            if (list.size >= ReachCalibration.STROKES) list.removeAt(0)
            list.add(reach)
        }
        onChange()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.background)
        val b = bands
        val rowH = b.rowHeightMm * pxPerMmY
        val rowsBottom = bandBottom - b.liftMm * pxPerMmY
        val rowsTop = rowsBottom - b.rows * rowH
        paint.style = Paint.Style.FILL
        paint.color = palette.strip
        canvas.drawRect(0f, rowsTop, width.toFloat(), rowsBottom, paint)
        paint.color = palette.hint
        paint.strokeWidth = 0.15f * pxPerMmY
        for (r in 0..b.rows) canvas.drawLine(0f, rowsTop + r * rowH, width.toFloat(), rowsTop + r * rowH, paint)
        val unit = prefs.splitUnitMm
        paint.strokeWidth = 0.3f * pxPerMmX
        for ((r, row) in Layouts.split.withIndex()) {
            val top = rowsTop + r * rowH
            val leftInner = (KeyboardView.SIDE_MM + row.left.units * unit) * pxPerMmX
            val rightInner = width - (KeyboardView.SIDE_MM + (Layouts.splitUnits - row.left.units) * unit) * pxPerMmX
            canvas.drawLine(leftInner, top, leftInner, top + rowH, paint)
            canvas.drawLine(rightInner, top, rightInner, top + rowH, paint)
        }
        paint.strokeWidth = 0.6f * pxPerMmX
        paint.color = palette.accent
        ReachCalibration.summarize(reaches.getValue(Side.LEFT))?.let {
            val x = it.medianMm * pxPerMmX
            canvas.drawLine(x, rowsTop, x, rowsBottom, paint)
        }
        ReachCalibration.summarize(reaches.getValue(Side.RIGHT))?.let {
            val x = width - it.medianMm * pxPerMmX
            canvas.drawLine(x, rowsTop, x, rowsBottom, paint)
        }
        paint.strokeWidth = 0.4f * pxPerMmX
        for ((side, points) in finished + strokes.values.map { it.first to it.second }) {
            paint.color = if (side == Side.LEFT) palette.accent else palette.locked
            for (k in 1 until points.size) canvas.drawLine(points[k - 1].x, points[k - 1].y, points[k].x, points[k].y, paint)
        }
    }

    companion object {
        const val MM_PER_INCH = 25.4f
    }
}
