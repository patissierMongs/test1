package io.github.patissiermongs.foldkey.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
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
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.input.ThumbZones
import io.github.patissiermongs.foldkey.input.ThumbZones.Side
import io.github.patissiermongs.foldkey.input.ThumbZones.Zone
import io.github.patissiermongs.foldkey.input.TypingCalibration
import io.github.patissiermongs.foldkey.layout.LayoutKind
import io.github.patissiermongs.foldkey.layout.Layouts
import io.github.patissiermongs.foldkey.ui.Dpi
import io.github.patissiermongs.foldkey.ui.KeyboardView
import io.github.patissiermongs.foldkey.ui.LayoutBuilder
import io.github.patissiermongs.foldkey.ui.Palette

class ReachCalibrationActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var zones: ZoneView
    private lateinit var status: TextView
    private lateinit var next: Button
    private lateinit var preview: Button
    private var pendingFit: ThumbZones.Fit? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val palette = Palette.of(resources.configuration)
        zones = ZoneView(this, prefs, palette) { update() }
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
        next = button(getString(R.string.reach_next)) { pendingFit?.let { startTyping(it) } }
        buttons.addView(next)
        buttons.addView(button(getString(R.string.reach_reset)) { zones.reset() })
        preview = button("") {
            zones.previewLayer = if (zones.previewLayer == Layer.CODE) Layer.GENERAL else Layer.CODE
            update()
        }
        buttons.addView(preview)
        buttons.addView(button(getString(R.string.reach_close)) { finish() })
        panel.addView(buttons)
        val root = FrameLayout(this)
        root.addView(zones, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val pad = dp(16)
            panel.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, pad)
            zones.bottomInset = bars.bottom
            zones.invalidate()
            insets
        }
        setContentView(root)
        update()
    }

    private fun startTyping(fit: ThumbZones.Fit) {
        val l = zones.summaries[Side.LEFT] ?: return
        val r = zones.summaries[Side.RIGHT] ?: return
        startActivity(
            Intent(this, TypingCalibrationActivity::class.java)
                .putExtra(TypingCalibrationActivity.EXTRA_ZONES, floatArrayOf(l.nearMm, l.farMm, l.bottomMm, l.topMm, r.nearMm, r.farMm, r.bottomMm, r.topMm))
                .putExtra(TypingCalibrationActivity.EXTRA_START, floatArrayOf(fit.codeUnitMm, fit.generalUnitMm, fit.rowHeightMm))
        )
        finish()
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
        val left = zones.strokes.getValue(Side.LEFT)
        val right = zones.strokes.getValue(Side.RIGHT)
        lines.add(getString(R.string.reach_progress, left.size, right.size, ThumbZones.STROKES))
        if (zones.lastInvalid) lines.add(getString(R.string.reach_invalid))
        val summaries = mapOf(Side.LEFT to ThumbZones.summarize(left), Side.RIGHT to ThumbZones.summarize(right))
        for ((side, list) in listOf(Side.LEFT to left, Side.RIGHT to right)) {
            val summary = summaries[side] ?: continue
            val name = getString(if (side == Side.LEFT) R.string.reach_left else R.string.reach_right)
            val z = summary.zone
            lines.add(getString(R.string.reach_zone, name, z.nearMm, z.farMm, z.bottomMm, z.topMm))
            if (list.size == ThumbZones.STROKES && !summary.consistent) {
                lines.add(getString(R.string.reach_inconsistent, name, summary.spreadMm))
            }
        }
        val l = summaries[Side.LEFT]
        val r = summaries[Side.RIGHT]
        pendingFit = if (l != null && r != null && left.size == ThumbZones.STROKES && right.size == ThumbZones.STROKES &&
            l.consistent && r.consistent
        ) {
            ThumbZones.fit(
                l.zone, r.zone, Layouts.split.size,
                Layouts.splitLeftUnits to Layouts.splitRightUnits,
                Layouts.generalSplitLeftUnits to Layouts.generalSplitRightUnits,
            )
        } else {
            null
        }
        pendingFit?.let { lines.add(getString(R.string.reach_result, it.codeUnitMm, it.generalUnitMm, it.rowHeightMm)) }
        status.text = lines.joinToString("\n")
        next.isEnabled = pendingFit != null
        preview.text = getString(if (zones.previewLayer == Layer.CODE) R.string.reach_preview_code else R.string.reach_preview_general)
        zones.summaries = summaries.mapNotNull { (k, v) -> v?.let { k to it.zone } }.toMap()
        zones.fit = pendingFit
        zones.invalidate()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

@SuppressLint("ViewConstructor")
private class ZoneView(
    context: Context,
    private val prefs: Prefs,
    private val palette: Palette,
    private val onChange: () -> Unit,
) : View(context) {
    val strokes = mapOf(Side.LEFT to ArrayList<Zone>(), Side.RIGHT to ArrayList<Zone>())
    var summaries: Map<Side, Zone> = emptyMap()
    var fit: ThumbZones.Fit? = null
    var previewLayer = Layer.CODE
    var lastInvalid = false
    var bottomInset = 0
    private val active = HashMap<Int, Pair<Side, ArrayList<PointF>>>()
    private val drawn = ArrayList<Pair<Side, List<PointF>>>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private val pxPerMmX: Float get() = Dpi.physical(resources.displayMetrics, true) / KeyboardView.MM_PER_INCH
    private val pxPerMmY: Float get() = Dpi.physical(resources.displayMetrics, false) / KeyboardView.MM_PER_INCH
    private val baseline: Float get() = height - bottomInset.toFloat()

    fun reset() {
        strokes.values.forEach { it.clear() }
        active.clear()
        drawn.clear()
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
                active[event.getPointerId(i)] = side to arrayListOf(PointF(event.getX(i), event.getY(i)))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                val points = active[event.getPointerId(i)]?.second ?: continue
                for (h in 0 until event.historySize) points.add(PointF(event.getHistoricalX(i, h), event.getHistoricalY(i, h)))
                points.add(PointF(event.getX(i), event.getY(i)))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> finish(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_CANCEL -> active.clear()
        }
        invalidate()
        return true
    }

    private fun finish(pointer: Int) {
        val (side, points) = active.remove(pointer) ?: return
        val samples = points.map { ThumbZones.Sample(it.x / pxPerMmX, (baseline - it.y) / pxPerMmY) }
        val zone = ThumbZones.strokeZone(samples, side, width / pxPerMmX)
        lastInvalid = zone == null
        if (zone != null) {
            val list = strokes.getValue(side)
            if (list.size >= ThumbZones.STROKES) list.removeAt(0)
            list.add(zone)
            drawn.add(side to points)
            while (drawn.count { it.first == side } > ThumbZones.STROKES) drawn.remove(drawn.first { it.first == side })
        }
        onChange()
    }

    private fun previewPlacement(): TypingCalibration.Placement? {
        val f = fit ?: return null
        val l = summaries[Side.LEFT] ?: return null
        val r = summaries[Side.RIGHT] ?: return null
        val frame = TypingCalibration.Frame(
            width / pxPerMmX,
            resources.displayMetrics.heightPixels * KeyboardView.MAX_HEIGHT_SHARE / pxPerMmY - KeyboardView.STRIP_MM,
            KeyboardView.SIDE_MM,
            KeyboardView.GHOST_UNITS,
        )
        val unit = if (previewLayer == Layer.GENERAL) f.generalUnitMm else f.codeUnitMm
        return TypingCalibration.place(TypingCalibration.anchors(l, r), frame, previewLayer, unit, f.rowHeightMm)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.background)
        if (width == 0) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.3f * pxPerMmX
        for ((side, points) in drawn + active.values.map { it.first to it.second }) {
            paint.color = if (side == Side.LEFT) palette.accent else palette.locked
            paint.alpha = 70
            for (k in 1 until points.size) canvas.drawLine(points[k - 1].x, points[k - 1].y, points[k].x, points[k].y, paint)
        }
        for ((side, zone) in summaries) {
            zoneRect(side, zone)
            paint.style = Paint.Style.FILL
            paint.color = if (side == Side.LEFT) palette.accent else palette.locked
            paint.alpha = 45
            canvas.drawRect(rect, paint)
        }
        val placed = previewPlacement()
        val base = LayoutBuilder.spec(prefs, LayoutKind.SPLIT, previewLayer, width.toFloat(), pxPerMmX, pxPerMmY, 0f, placed?.rowMm ?: prefs.rowHeightMm)
        val spec = if (placed == null) base else base.copy(
            splitMarginLeftPx = placed.leftMm * pxPerMmX,
            splitMarginRightPx = placed.rightMm * pxPerMmX,
            splitUnitMm = placed.unitMm,
        )
        val geometry = LayoutBuilder.build(LayoutKind.SPLIT, previewLayer, spec)
        val lift = (placed?.liftMm ?: prefs.splitLiftMm) * pxPerMmY
        val rowsHeight = Layouts.rows(LayoutKind.SPLIT, previewLayer) * spec.rowHeightMm * pxPerMmY
        canvas.save()
        canvas.translate(0f, baseline - lift - rowsHeight)
        val radius = KeyboardView.RADIUS_MM * pxPerMmX
        paint.textAlign = Paint.Align.CENTER
        for (key in geometry.keys) {
            if (key.ghost) continue
            rect.set(key.face.left, key.face.top, key.face.right, key.face.bottom)
            paint.style = Paint.Style.FILL
            paint.color = palette.key
            paint.alpha = 175
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.15f * pxPerMmX
            paint.color = palette.text
            paint.alpha = 120
            canvas.drawRoundRect(rect, radius, radius, paint)
            val label = LayoutBuilder.previewLabel(context, key.def, previewLayer)
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
        canvas.restore()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.4f * pxPerMmX
        for ((side, zone) in summaries) {
            zoneRect(side, zone)
            paint.color = if (side == Side.LEFT) palette.accent else palette.locked
            paint.alpha = 255
            canvas.drawRect(rect, paint)
        }
        paint.alpha = 255
    }

    private fun zoneRect(side: Side, zone: Zone) {
        val near = zone.nearMm * pxPerMmX
        val far = zone.farMm * pxPerMmX
        val left = if (side == Side.LEFT) near else width - far
        val right = if (side == Side.LEFT) far else width - near
        rect.set(left, baseline - zone.topMm * pxPerMmY, right, baseline - zone.bottomMm * pxPerMmY)
    }
}
