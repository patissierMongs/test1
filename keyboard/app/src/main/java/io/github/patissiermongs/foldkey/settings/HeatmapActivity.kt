package io.github.patissiermongs.foldkey.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.patissiermongs.foldkey.R
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.input.OffsetModel
import io.github.patissiermongs.foldkey.input.PositionAdvice
import io.github.patissiermongs.foldkey.input.TapHeat
import io.github.patissiermongs.foldkey.layout.KeyStyle
import io.github.patissiermongs.foldkey.layout.KeyboardGeometry
import io.github.patissiermongs.foldkey.layout.LayoutKind
import io.github.patissiermongs.foldkey.layout.Layouts
import io.github.patissiermongs.foldkey.ui.Dpi
import io.github.patissiermongs.foldkey.ui.KeyboardView
import io.github.patissiermongs.foldkey.ui.LayoutBuilder
import io.github.patissiermongs.foldkey.ui.Palette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class HeatmapActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var map: HeatmapView
    private lateinit var summary: TextView
    private lateinit var advice: TextView
    private lateinit var apply: Button
    private lateinit var reset: Button
    private val slotButtons = ArrayList<Pair<Button, OffsetSlot>>()
    private var selected: OffsetSlot? = null
    private var pending: PositionAdvice.Advice? = null

    private val pxPerMmX: Float get() = Dpi.physical(resources.displayMetrics, true) / KeyboardView.MM_PER_INCH
    private val pxPerMmY: Float get() = Dpi.physical(resources.displayMetrics, false) / KeyboardView.MM_PER_INCH

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val palette = Palette.of(resources.configuration)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(20)
            setPadding(pad, pad, pad, pad)
        }
        column.addView(TextView(this).apply {
            text = getString(R.string.heat_intro)
            textSize = 14f
            setPadding(0, dp(6), 0, dp(6))
        })
        val slots = prefs.heatSlots().mapNotNull { OffsetSlot.parse(it) }.filter { heat(it).taps > 0 }
        if (slots.isEmpty()) {
            column.addView(TextView(this).apply { text = getString(R.string.heat_empty); textSize = 15f })
        }
        for (slot in slots) {
            val b = Button(this).apply {
                text = slot.title(this@HeatmapActivity)
                isAllCaps = false
                setOnClickListener { select(slot) }
            }
            slotButtons.add(b to slot)
            column.addView(b)
        }
        summary = TextView(this).apply { textSize = 14f; setPadding(0, dp(8), 0, dp(4)) }
        column.addView(summary)
        map = HeatmapView(this, prefs, palette)
        column.addView(map, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        advice = TextView(this).apply { textSize = 14f; setPadding(0, dp(8), 0, dp(4)) }
        column.addView(advice)
        apply = Button(this).apply {
            text = getString(R.string.heat_apply)
            isAllCaps = false
            isEnabled = false
            setOnClickListener { applyAdvice() }
        }
        column.addView(apply)
        reset = Button(this).apply {
            text = getString(R.string.heat_reset)
            isAllCaps = false
            isEnabled = false
            setOnClickListener { resetHeat() }
        }
        column.addView(reset)
        val scroll = ScrollView(this)
        scroll.addView(column)
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
        slots.firstOrNull()?.let { select(it) }
    }

    private fun heat(slot: OffsetSlot): TapHeat = TapHeat.parse(prefs.heat(slot.name), prefs.layoutSignature)

    private fun geometry(slot: OffsetSlot): KeyboardGeometry {
        val spec = LayoutBuilder.spec(prefs, slot.kind, slot.layer, slot.widthMm * pxPerMmX, pxPerMmX, pxPerMmY, 0f, prefs.rowHeightMm)
        return LayoutBuilder.build(slot.kind, slot.layer, spec)
    }

    private fun leftKeys(slot: OffsetSlot): Set<Char> =
        (if (slot.layer == Layer.GENERAL) Layouts.generalSplit else Layouts.split)
            .flatMap { it.left.keys }.mapNotNull { (it.action as? KeyAction.Char)?.base }.toSet()

    private fun select(slot: OffsetSlot) {
        selected = slot
        for ((b, s) in slotButtons) b.isEnabled = s != slot
        val heat = heat(slot)
        map.show(geometry(slot), heat, slot.widthMm * pxPerMmX)
        reset.isEnabled = heat.taps > 0
        val split = slot.kind == LayoutKind.SPLIT
        val a = PositionAdvice.advise(heat, if (split) leftKeys(slot) else emptySet())
        val lines = ArrayList<String>()
        lines.add(resources.getQuantityString(R.plurals.heat_taps, heat.taps, heat.taps))
        if (split) {
            a.left?.let { lines.add(resources.getQuantityString(R.plurals.heat_thumb, it.taps, getString(R.string.reach_left), it.taps, it.xMm, it.yMm)) }
            a.right?.let { lines.add(resources.getQuantityString(R.plurals.heat_thumb, it.taps, getString(R.string.reach_right), it.taps, it.xMm, it.yMm)) }
        }
        summary.text = lines.joinToString("\n")
        pending = null
        apply.isEnabled = false
        if (!split) {
            advice.text = getString(R.string.heat_split_only)
            return
        }
        val leftTaps = a.left?.taps ?: 0
        val rightTaps = a.right?.taps ?: 0
        if (leftTaps < PositionAdvice.MIN_TAPS || rightTaps < PositionAdvice.MIN_TAPS) {
            advice.text = resources.getQuantityString(R.plurals.heat_more_taps, PositionAdvice.MIN_TAPS, leftTaps, rightTaps, PositionAdvice.MIN_TAPS)
            return
        }
        val target = placement(slot, a)
        val sides = LayoutBuilder.sides(prefs, slot.layer)
        val steps = ArrayList<String>()
        val limits = ArrayList<String>()
        if (target.leftMm != edge(sides.leftMm)) {
            val moved = target.leftMm - edge(sides.leftMm)
            steps.add(getString(if (moved > 0f) R.string.heat_left_right else R.string.heat_left_left, abs(moved), edge(sides.leftMm), target.leftMm))
        } else if (a.leftMm != 0f) {
            limits.add(getString(R.string.heat_limit_left))
        }
        if (target.rightMm != edge(sides.rightMm)) {
            val moved = edge(sides.rightMm) - target.rightMm
            steps.add(getString(if (moved > 0f) R.string.heat_right_right else R.string.heat_right_left, abs(moved), edge(sides.rightMm), target.rightMm))
        } else if (a.rightMm != 0f) {
            limits.add(getString(R.string.heat_limit_right))
        }
        if (target.liftMm != prefs.splitLiftMm) {
            val moved = target.liftMm - prefs.splitLiftMm
            steps.add(getString(if (moved > 0f) R.string.heat_raise else R.string.heat_lower, abs(moved), prefs.splitLiftMm, target.liftMm))
        } else if (a.raiseMm != 0f) {
            limits.add(getString(R.string.heat_limit_lift))
        }
        if (steps.isEmpty() && limits.isEmpty()) {
            advice.text = getString(R.string.heat_keep)
            return
        }
        val notes = ArrayList<String>()
        if (steps.isNotEmpty()) notes.add(getString(R.string.heat_advice))
        steps.mapTo(notes) { getString(R.string.heat_step, it) }
        notes.addAll(limits)
        advice.text = notes.joinToString("\n")
        if (steps.isEmpty()) return
        pending = a
        apply.isEnabled = true
    }

    private data class Target(val leftMm: Float, val rightMm: Float, val liftMm: Float)

    private fun edge(mm: Float): Float = max(mm, KeyboardView.SIDE_MM)

    private fun tenth(mm: Float): Float = (mm * 10f).roundToInt() / 10f

    private fun placement(slot: OffsetSlot, a: PositionAdvice.Advice): Target {
        val sides = LayoutBuilder.sides(prefs, slot.layer)
        val sideMax = Prefs.SIDE_MAX / 10f
        return Target(
            leftMm = tenth((edge(sides.leftMm) + a.leftMm).coerceIn(KeyboardView.SIDE_MM, sideMax)),
            rightMm = tenth((edge(sides.rightMm) - a.rightMm).coerceIn(KeyboardView.SIDE_MM, sideMax)),
            liftMm = tenth((prefs.splitLiftMm + a.raiseMm).coerceIn(0f, Prefs.LIFT_MAX / 10f)),
        )
    }

    private fun applyAdvice() {
        val slot = selected ?: return
        val a = pending ?: return
        val target = placement(slot, a)
        val raised = target.liftMm - prefs.splitLiftMm
        val slots = Layer.entries.map { OffsetSlot(KeyboardView.slotName(LayoutKind.SPLIT, it, slot.widthMm), LayoutKind.SPLIT, it, slot.widthMm) }
        val before = slots.associateWith { geometry(it) }
        prefs.applyPlacement(slot.layer, target.leftMm, target.rightMm, target.liftMm)
        val offsets = HashMap<String, String>()
        for (s in slots) {
            val data = prefs.offsets(s.name) ?: continue
            val after = geometry(s)
            val model = OffsetModel(after.zoneCount).also { it.load(data) }
            PositionAdvice.shiftOffsets(model, before.getValue(s), after, pxPerMmX, pxPerMmY, raised)
            offsets[s.name] = model.serialize()
        }
        prefs.replaceTouchData(offsets, slots.map { it.name })
        recreate()
    }

    private fun resetHeat() {
        val slot = selected ?: return
        prefs.replaceTouchData(emptyMap(), listOf(slot.name))
        recreate()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

@SuppressLint("ViewConstructor")
private class HeatmapView(context: Context, private val prefs: Prefs, private val palette: Palette) : View(context) {
    private var geometry: KeyboardGeometry? = null
    private var heat = TapHeat()
    private var fullWidth = 0f
    private var image: Bitmap? = null
    private var peak = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val pxPerMmX: Float get() = Dpi.physical(resources.displayMetrics, true) / KeyboardView.MM_PER_INCH
    private val pxPerMmY: Float get() = Dpi.physical(resources.displayMetrics, false) / KeyboardView.MM_PER_INCH
    private val ramp: IntArray get() = if (palette == Palette.DARK) DARK_RAMP else LIGHT_RAMP

    fun show(g: KeyboardGeometry, heat: TapHeat, widthPx: Float) {
        geometry = g
        this.heat = heat
        fullWidth = widthPx
        image = null
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val g = geometry
        val h = if (g == null || fullWidth <= 0f) 0 else {
            val scale = min(1f, w / fullWidth)
            (g.heightPx * scale + LEGEND_MM * pxPerMmY + 4f * pxPerMmY).toInt()
        }
        setMeasuredDimension(w, h)
    }

    private fun density(g: KeyboardGeometry): Pair<Bitmap, Float> {
        val cols = ceil(fullWidth / pxPerMmX / CELL_MM).toInt().coerceAtLeast(1)
        val rows = ceil(g.heightPx / pxPerMmY / CELL_MM).toInt().coerceAtLeast(1)
        val field = FloatArray(cols * rows)
        for (key in g.keys) {
            if (key.ghost || key.pad || key.def.style != KeyStyle.NORMAL) continue
            val c = (key.def.action as? KeyAction.Char)?.base ?: continue
            val cloud = heat.cloud(c) ?: continue
            val n = cloud.n.toFloat()
            val a = (n * cloud.varXMm2 + PRIOR * SIGMA0 * SIGMA0) / (n + PRIOR)
            val b = (n * cloud.varYMm2 + PRIOR * SIGMA0 * SIGMA0) / (n + PRIOR)
            val cov = n * cloud.covMm2 / (n + PRIOR)
            val det = a * b - cov * cov
            if (det <= 0f) continue
            val mx = key.face.centerX / pxPerMmX + cloud.meanXMm
            val my = key.face.centerY / pxPerMmY + cloud.meanYMm
            val reach = 3f * sqrt(max(a, b))
            val norm = n / (2f * PI.toFloat() * sqrt(det))
            val c0 = ((mx - reach) / CELL_MM).toInt().coerceIn(0, cols - 1)
            val c1 = ((mx + reach) / CELL_MM).toInt().coerceIn(0, cols - 1)
            val r0 = ((my - reach) / CELL_MM).toInt().coerceIn(0, rows - 1)
            val r1 = ((my + reach) / CELL_MM).toInt().coerceIn(0, rows - 1)
            for (r in r0..r1) for (col in c0..c1) {
                val dx = (col + 0.5f) * CELL_MM - mx
                val dy = (r + 0.5f) * CELL_MM - my
                val q = (b * dx * dx - 2f * cov * dx * dy + a * dy * dy) / det
                field[r * cols + col] += norm * exp(-0.5f * q)
            }
        }
        val top = field.maxOrNull() ?: 0f
        val pixels = IntArray(field.size) { if (top <= 0f) 0 else color(field[it] / top) }
        return Bitmap.createBitmap(pixels, cols, rows, Bitmap.Config.ARGB_8888) to top
    }

    private fun color(t: Float): Int {
        if (t <= 0f) return 0
        val stops = ramp
        val x = t.coerceIn(0f, 1f) * (stops.size - 1)
        val i = min(x.toInt(), stops.size - 2)
        val f = x - i
        fun mix(shift: Int): Int {
            val a = (stops[i] shr shift) and 0xFF
            val b = (stops[i + 1] shr shift) and 0xFF
            return (a + (b - a) * f).roundToInt()
        }
        val alpha = (MAX_ALPHA * min(1f, t / FADE)).roundToInt()
        return (alpha shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.background)
        val g = geometry ?: return
        val scale = min(1f, width / fullWidth)
        canvas.save()
        canvas.translate((width - fullWidth * scale) / 2f, 2f * pxPerMmY)
        canvas.scale(scale, scale)
        val radius = KeyboardView.RADIUS_MM * pxPerMmX
        for (key in g.keys) {
            if (key.ghost) continue
            paint.style = Paint.Style.FILL
            paint.color = if (key.def.style == KeyStyle.NORMAL) palette.key else palette.modKey
            rect.set(key.face.left, key.face.top, key.face.right, key.face.bottom)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        val (bitmap, top) = image?.let { it to peak } ?: density(g).also { image = it.first; peak = it.second }
        rect.set(0f, 0f, bitmap.width * CELL_MM * pxPerMmX, bitmap.height * CELL_MM * pxPerMmY)
        canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
        paint.textAlign = Paint.Align.LEFT
        for (key in g.keys) {
            if (key.ghost) continue
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.15f * pxPerMmX
            paint.color = palette.hint
            rect.set(key.face.left, key.face.top, key.face.right, key.face.bottom)
            canvas.drawRoundRect(rect, radius, radius, paint)
            val arm = 0.7f * pxPerMmX
            canvas.drawLine(key.face.centerX - arm, key.face.centerY, key.face.centerX + arm, key.face.centerY, paint)
            canvas.drawLine(key.face.centerX, key.face.centerY - arm, key.face.centerX, key.face.centerY + arm, paint)
            val label = LayoutBuilder.previewLabel(key.def)
            if (label.isEmpty()) continue
            paint.textSize = key.face.height * 0.24f
            val x = key.face.left + 0.6f * pxPerMmX
            val y = key.face.top + paint.textSize + 0.3f * pxPerMmY
            paint.strokeWidth = 0.5f * pxPerMmX
            paint.color = palette.background
            canvas.drawText(label, x, y, paint)
            paint.style = Paint.Style.FILL
            paint.color = palette.text
            canvas.drawText(label, x, y, paint)
        }
        canvas.restore()
        drawLegend(canvas, (2f + LEGEND_GAP_MM) * pxPerMmY + g.heightPx * scale, top)
    }

    private fun drawLegend(canvas: Canvas, y: Float, top: Float) {
        val barW = min(width * 0.5f, 50f * pxPerMmX)
        val barH = 2f * pxPerMmY
        val left = (width - barW) / 2f
        val steps = 64
        paint.style = Paint.Style.FILL
        for (i in 0 until steps) {
            paint.color = color((i + 0.5f) / steps)
            canvas.drawRect(left + barW * i / steps, y, left + barW * (i + 1) / steps, y + barH, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.15f * pxPerMmX
        paint.color = palette.hint
        canvas.drawRect(left, y, left + barW, y + barH, paint)
        paint.style = Paint.Style.FILL
        paint.color = palette.hint
        paint.textSize = 2.6f * pxPerMmY
        val baseline = y + barH + paint.textSize + 0.6f * pxPerMmY
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(context.getString(R.string.heat_legend_low), left, baseline, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(context.getString(R.string.heat_legend_high, top), left + barW, baseline, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    companion object {
        const val CELL_MM = 0.4f
        const val SIGMA0 = 1.2f
        const val PRIOR = 5f
        const val MAX_ALPHA = 225f
        const val FADE = 0.2f
        const val LEGEND_MM = 9f
        const val LEGEND_GAP_MM = 2f
        val LIGHT_RAMP = intArrayOf(0xCDE2FB, 0x9EC5F4, 0x6DA7EC, 0x3987E5, 0x256ABF, 0x184F95, 0x0D366B)
        val DARK_RAMP = intArrayOf(0x0D366B, 0x184F95, 0x256ABF, 0x3987E5, 0x6DA7EC, 0x9EC5F4, 0xCDE2FB)
    }
}
