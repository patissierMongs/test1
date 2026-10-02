package io.github.patissiermongs.foldkey.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
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
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.input.OffsetModel
import io.github.patissiermongs.foldkey.layout.KeyStyle
import io.github.patissiermongs.foldkey.layout.KeyboardGeometry
import io.github.patissiermongs.foldkey.layout.LayoutKind
import io.github.patissiermongs.foldkey.layout.Layouts
import io.github.patissiermongs.foldkey.ui.Dpi
import io.github.patissiermongs.foldkey.ui.KeyboardView
import io.github.patissiermongs.foldkey.ui.LayoutBuilder
import io.github.patissiermongs.foldkey.ui.Palette
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class OffsetMapActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var map: OffsetMapView
    private lateinit var summary: TextView
    private val slotButtons = ArrayList<Pair<Button, OffsetSlot>>()

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
            text = getString(R.string.offsets_intro)
            textSize = 14f
            setPadding(0, dp(6), 0, dp(6))
        })
        val slots = prefs.offsetSlots().mapNotNull { OffsetSlot.parse(it) }
        if (slots.isEmpty()) {
            column.addView(TextView(this).apply { text = getString(R.string.offsets_empty); textSize = 15f })
        }
        for (slot in slots) {
            val b = Button(this).apply {
                text = slot.title(this@OffsetMapActivity)
                isAllCaps = false
                setOnClickListener { select(slot) }
            }
            slotButtons.add(b to slot)
            column.addView(b)
        }
        summary = TextView(this).apply { textSize = 14f; setPadding(0, dp(8), 0, dp(4)) }
        column.addView(summary)
        map = OffsetMapView(this, prefs, palette)
        column.addView(map, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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

    private fun select(slot: OffsetSlot) {
        for ((b, s) in slotButtons) b.isEnabled = s != slot
        map.slot = slot
        val model = map.model()
        val n = (0 until model.zones).sumOf { model.samples(it) }
        if (n == 0) {
            summary.text = getString(R.string.offsets_empty)
            return
        }
        var x = 0f
        var y = 0f
        for (z in 0 until model.zones) {
            val (cx, cy) = model.meanMm(z)
            x += cx * model.samples(z)
            y += cy * model.samples(z)
        }
        summary.text = resources.getQuantityString(R.plurals.offsets_summary, n, n, x / n, y / n)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

data class OffsetSlot(val name: String, val kind: LayoutKind, val layer: Layer, val widthMm: Int) {
    fun title(context: Context): String = context.getString(
        R.string.offsets_slot,
        context.getString(
            when (kind) {
                LayoutKind.SPLIT -> R.string.offsets_kind_split
                LayoutKind.FULL -> R.string.offsets_kind_full
                LayoutKind.COMPACT -> R.string.offsets_kind_compact
            }
        ),
        context.getString(if (layer == Layer.GENERAL) R.string.offsets_layer_general else R.string.offsets_layer_code),
        widthMm,
    )

    companion object {
        private val PATTERN = Regex("^(full|split|compact)(${KeyboardView.SPLIT_SLOT_SUFFIX}|${KeyboardView.GENERAL_SLOT_SUFFIX})?_(\\d+)mm$")

        fun parse(name: String): OffsetSlot? {
            val m = PATTERN.matchEntire(name) ?: return null
            val kind = LayoutKind.valueOf(m.groupValues[1].uppercase())
            val layer = if (m.groupValues[2] == KeyboardView.GENERAL_SLOT_SUFFIX) Layer.GENERAL else Layer.CODE
            return OffsetSlot(name, kind, layer, m.groupValues[3].toInt())
        }
    }
}

@SuppressLint("ViewConstructor")
private class OffsetMapView(context: Context, private val prefs: Prefs, private val palette: Palette) : View(context) {
    var slot: OffsetSlot? = null
        set(value) {
            field = value
            content = null
            requestLayout()
            invalidate()
        }
    private var content: Pair<KeyboardGeometry, OffsetModel>? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val labelled = HashSet<Int>()
    private val pxPerMmX: Float get() = Dpi.physical(resources.displayMetrics, true) / KeyboardView.MM_PER_INCH
    private val pxPerMmY: Float get() = Dpi.physical(resources.displayMetrics, false) / KeyboardView.MM_PER_INCH

    private fun geometry(s: OffsetSlot): KeyboardGeometry {
        val spec = LayoutBuilder.spec(prefs, s.kind, s.layer, s.widthMm * pxPerMmX, pxPerMmX, pxPerMmY, 0f, prefs.rowHeightMm)
        return LayoutBuilder.build(s.kind, s.layer, spec)
    }

    private fun content(): Pair<KeyboardGeometry, OffsetModel>? {
        content?.let { return it }
        val s = slot ?: return null
        val g = geometry(s)
        return (g to OffsetModel(g.zoneCount).also { it.load(prefs.offsets(s.name)) }).also { content = it }
    }

    fun model(): OffsetModel = content()?.second ?: OffsetModel(0)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val s = slot
        val h = if (s == null) 0 else {
            val scale = min(1f, w / (s.widthMm * pxPerMmX))
            (Layouts.rows(s.kind, s.layer) * prefs.rowHeightMm * pxPerMmY * scale + 4f * pxPerMmY).toInt()
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.background)
        val s = slot ?: return
        val (g, model) = content() ?: return
        val fullWidth = s.widthMm * pxPerMmX
        val scale = min(1f, width / fullWidth)
        canvas.save()
        canvas.translate((width - fullWidth * scale) / 2f, 2f * pxPerMmY)
        canvas.scale(scale, scale)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.2f * pxPerMmX
        paint.color = palette.hint
        paint.alpha = 90
        for (q in 1 until g.zoneCount / Layouts.rows(s.kind, s.layer).coerceAtLeast(1)) {
            val x = fullWidth * q / ZONE_COLUMNS
            canvas.drawLine(x, 0f, x, g.heightPx, paint)
        }
        paint.alpha = 255
        val radius = KeyboardView.RADIUS_MM * pxPerMmX
        paint.textAlign = Paint.Align.CENTER
        for (key in g.keys) {
            if (key.ghost) continue
            paint.style = Paint.Style.FILL
            paint.color = if (key.def.style == KeyStyle.NORMAL) palette.key else palette.modKey
            rect.set(key.face.left, key.face.top, key.face.right, key.face.bottom)
            canvas.drawRoundRect(rect, radius, radius, paint)
            val label = LayoutBuilder.previewLabel(context, key.def, s.layer)
            if (label.isNotEmpty()) {
                paint.color = palette.hint
                paint.textSize = key.face.height * if (label.length == 1) 0.34f else 0.22f
                canvas.drawText(label, key.face.centerX, key.face.top + key.face.height * 0.3f, paint)
            }
        }
        val unitMm = g.unitPx / pxPerMmX
        labelled.clear()
        for (key in g.keys) {
            if (key.ghost || key.pad || key.def.style != KeyStyle.NORMAL) continue
            val n = model.samples(key.zone)
            if (n == 0) continue
            val (dx, dy) = model.correctionMm(key.zone, unitMm, prefs.rowHeightMm)
            val x0 = key.face.centerX
            val y0 = key.face.centerY + key.face.height * 0.12f
            val x1 = x0 + dx * ARROW_SCALE * pxPerMmX
            val y1 = y0 + dy * ARROW_SCALE * pxPerMmY
            paint.color = palette.accent
            paint.alpha = (90 + 165 * min(1f, n / WARMUP)).toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.45f * pxPerMmX
            canvas.drawLine(x0, y0, x1, y1, paint)
            if (hypot(x1 - x0, y1 - y0) > 0.6f * pxPerMmX) {
                val angle = atan2(y1 - y0, x1 - x0)
                val head = 1.0f * pxPerMmX
                for (side in listOf(-1, 1)) {
                    val a = angle + Math.PI.toFloat() + side * 0.5f
                    canvas.drawLine(x1, y1, x1 + head * cos(a), y1 + head * sin(a), paint)
                }
            }
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x0, y0, 0.45f * pxPerMmX, paint)
            if (labelled.add(key.zone)) {
                paint.alpha = 255
                paint.textSize = 1.9f * pxPerMmY
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(n.toString(), key.face.left + 0.6f * pxPerMmX, key.face.bottom - 0.6f * pxPerMmY, paint)
                paint.textAlign = Paint.Align.CENTER
            }
        }
        paint.alpha = 255
        canvas.restore()
    }

    companion object {
        const val ARROW_SCALE = 4f
        const val ZONE_COLUMNS = 4
        const val WARMUP = 20f
    }
}
