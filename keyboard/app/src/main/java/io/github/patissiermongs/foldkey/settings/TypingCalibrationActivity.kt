package io.github.patissiermongs.foldkey.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
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
import io.github.patissiermongs.foldkey.input.ThumbZones.Side
import io.github.patissiermongs.foldkey.input.ThumbZones.Zone
import io.github.patissiermongs.foldkey.input.TypingCalibration
import io.github.patissiermongs.foldkey.input.TypingPrompts
import io.github.patissiermongs.foldkey.layout.Key
import io.github.patissiermongs.foldkey.layout.KeyStyle
import io.github.patissiermongs.foldkey.layout.KeyboardGeometry
import io.github.patissiermongs.foldkey.layout.LayoutKind
import io.github.patissiermongs.foldkey.layout.Layouts
import io.github.patissiermongs.foldkey.ui.Dpi
import io.github.patissiermongs.foldkey.ui.KeyboardView
import io.github.patissiermongs.foldkey.ui.LayoutBuilder
import io.github.patissiermongs.foldkey.ui.Palette
import io.github.patissiermongs.foldkey.ui.TypingSession
import kotlin.math.max
import kotlin.math.roundToInt

class TypingCalibrationActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var palette: Palette
    private lateinit var board: TypingView
    private lateinit var status: TextView
    private lateinit var prompt: TextView
    private lateinit var typed: TextView
    private lateinit var skip: Button
    private lateinit var apply: Button
    private lateinit var preview: Button
    private var previewLayer = Layer.CODE
    lateinit var session: TypingSession
        private set
    private var zones: Pair<Zone, Zone>? = null
    private val pxPerMmX: Float get() = Dpi.physical(resources.displayMetrics, true) / KeyboardView.MM_PER_INCH
    private val pxPerMmY: Float get() = Dpi.physical(resources.displayMetrics, false) / KeyboardView.MM_PER_INCH

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        palette = Palette.of(resources.configuration)
        zones = intent.getFloatArrayExtra(EXTRA_ZONES)?.takeIf { it.size == 8 }?.let {
            Zone(it[0], it[1], it[2], it[3]) to Zone(it[4], it[5], it[6], it[7])
        }
        board = TypingView(this, palette) { x, y -> onTap(x, y) }
        board.zones = zones
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(text(getString(R.string.typing_intro), 14f))
        status = text("", 14f)
        panel.addView(status)
        prompt = text("", 24f).apply { typeface = Typeface.MONOSPACE }
        panel.addView(prompt)
        typed = text("", 24f).apply {
            typeface = Typeface.MONOSPACE
            setTextColor(palette.accent)
        }
        panel.addView(typed)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        skip = button(getString(R.string.typing_skip)) {
            session.skipLayer()
            board.flash(null, true)
            update()
        }
        buttons.addView(skip)
        buttons.addView(button(getString(R.string.typing_restart)) { restart() })
        apply = button(getString(R.string.typing_apply)) { applyResult() }
        buttons.addView(apply)
        preview = button("") {
            previewLayer = if (previewLayer == Layer.CODE) Layer.GENERAL else Layer.CODE
            update()
        }
        buttons.addView(preview)
        buttons.addView(button(getString(R.string.typing_close)) { finish() })
        panel.addView(buttons)
        val root = FrameLayout(this)
        root.addView(board, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val pad = dp(16)
            panel.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, pad)
            board.bottomInset = bars.bottom
            board.invalidate()
            insets
        }
        setContentView(root)
        restart()
    }

    private fun restart() {
        session = newSession()
        board.session = session
        board.flash(null, true)
        update()
    }

    private fun newSession(): TypingSession {
        val dm = resources.displayMetrics
        val px = pxPerMmX
        val py = pxPerMmY
        val width = dm.widthPixels.toFloat()
        val start = intent.getFloatArrayExtra(EXTRA_START)?.takeIf { it.size == 3 }
        val code = start?.get(0) ?: prefs.splitUnitMm
        val general = start?.get(1) ?: LayoutBuilder.splitUnitMm(prefs, Layer.GENERAL)
        val row = start?.get(2) ?: prefs.rowHeightMm
        val measured = zones?.let { (l, r) -> TypingCalibration.anchors(l, r) }
        val anchors = Layer.entries.associateWith { measured ?: currentAnchors(it, width, px, py) }
        val plan = TypingSession.Plan(
            layers = listOf(Layer.CODE, Layer.GENERAL),
            start = mapOf(Layer.CODE to code, Layer.GENERAL to general),
            startRowMm = row,
            anchors = anchors,
            widthPx = width,
            pxPerMmX = px,
            pxPerMmY = py,
            maxHeightMm = dm.heightPixels * KeyboardView.MAX_HEIGHT_SHARE / py - KeyboardView.STRIP_MM,
        )
        return TypingSession(plan) { layer, placement -> geometry(layer, placement, width, px, py) }
    }

    private fun geometry(layer: Layer, p: TypingCalibration.Placement, width: Float, px: Float, py: Float): KeyboardGeometry {
        val panel = prefs.centerEcho || prefs.centerClipboard
        val spec = LayoutBuilder.spec(
            prefs, LayoutKind.SPLIT, layer, width, px, py, 0f, p.rowMm,
            liftPx = p.liftMm * py,
            panelMinPx = if (panel) KeyboardView.PANEL_MIN_MM * px else 0f,
        ).copy(splitUnitMm = p.unitMm, splitMarginLeftPx = p.leftMm * px, splitMarginRightPx = p.rightMm * px)
        return LayoutBuilder.build(LayoutKind.SPLIT, layer, spec)
    }

    private fun currentAnchors(layer: Layer, width: Float, px: Float, py: Float): TypingCalibration.Anchors {
        val g = LayoutBuilder.build(LayoutKind.SPLIT, layer, LayoutBuilder.spec(prefs, LayoutKind.SPLIT, layer, width, px, py, 0f, prefs.rowHeightMm))
        val unit = g.unitPx / px
        val sides = LayoutBuilder.sides(prefs, layer)
        val general = layer == Layer.GENERAL
        val leftUnits = if (general) Layouts.generalSplitLeftUnits else Layouts.splitLeftUnits
        val rightUnits = if (general) Layouts.generalSplitRightUnits else Layouts.splitRightUnits
        return TypingCalibration.Anchors(
            max(sides.leftMm, KeyboardView.SIDE_MM) + leftUnits * unit / 2f,
            max(sides.rightMm, KeyboardView.SIDE_MM) + rightUnits * unit / 2f,
            prefs.splitLiftMm + Layouts.rows(LayoutKind.SPLIT, layer) * prefs.rowHeightMm / 2f,
        )
    }

    private fun onTap(x: Float, y: Float) {
        if (session.finished) return
        val gy = y - board.keyboardTop
        val key = session.geometry.keyAt(x, gy)
        when (session.tap(x, gy)) {
            TypingSession.Outcome.IGNORED -> return
            TypingSession.Outcome.HIT -> board.flash(key, true)
            TypingSession.Outcome.MISS -> board.flash(key, false)
            else -> board.flash(null, true)
        }
        update()
    }

    private fun update() {
        val finished = session.finished
        prompt.visibility = if (finished) View.GONE else View.VISIBLE
        typed.visibility = if (finished) View.GONE else View.VISIBLE
        skip.isEnabled = !finished
        apply.isEnabled = finished && session.results.any { it.unitMm != null }
        preview.visibility = View.GONE
        board.final = null
        if (finished) {
            status.text = resultText()
            val placements = session.calibration().placements
            if (previewLayer !in placements) previewLayer = placements.keys.firstOrNull() ?: Layer.CODE
            placements[previewLayer]?.let { p ->
                val dm = resources.displayMetrics
                board.final = TypingView.Final(previewLayer, geometry(previewLayer, p, dm.widthPixels.toFloat(), pxPerMmX, pxPerMmY), p)
            }
            if (placements.size > 1) {
                preview.visibility = View.VISIBLE
                preview.text = getString(if (previewLayer == Layer.CODE) R.string.reach_preview_code else R.string.reach_preview_general)
            }
        } else {
            status.text = getString(
                R.string.typing_status, layerName(session.layer), session.roundNumber, session.progress,
                TypingSession.ROUND_KEYS, percent(session.round.hitRate),
            )
            prompt.text = promptText()
            typed.text = session.typed
        }
        board.invalidate()
    }

    private fun promptText(): CharSequence {
        val line = session.line
        val text = SpannableString(line)
        var keys = 0
        var done = 0
        for (c in line) {
            keys += TypingPrompts.keys(c.toString())?.size ?: 1
            if (keys > session.charIndex) break
            done++
        }
        if (done > 0) text.setSpan(ForegroundColorSpan(palette.hint), 0, done, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (done < line.length) text.setSpan(UnderlineSpan(), done, done + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    private fun resultText(): String {
        val lines = ArrayList<String>()
        val c = session.calibration()
        for (r in session.results) {
            val name = layerName(r.layer)
            val p = c.placements[r.layer]
            if (p == null) {
                lines.add(getString(R.string.typing_result_skipped, name))
                continue
            }
            val first = r.rounds.first()
            lines.add(getString(R.string.typing_result_size, name, first.unitMm, p.unitMm, first.placement.rowMm, p.rowMm, p.leftMm, p.rightMm))
            lines.add(getString(R.string.typing_result_rounds, r.rounds.joinToString(" → ") { "${percent(it.hitRate)}%" }))
            val a = r.analysis ?: continue
            val left = a.spread[Side.LEFT]
            val right = a.spread[Side.RIGHT]
            lines.add(getString(R.string.typing_result_spread, left?.xMm ?: 0f, left?.yMm ?: 0f, right?.xMm ?: 0f, right?.yMm ?: 0f))
        }
        c.placements.values.firstOrNull()?.let { lines.add(getString(R.string.typing_result_common, it.rowMm, it.liftMm)) }
        return lines.joinToString("\n")
    }

    private fun applyResult() {
        val c = session.calibration()
        val width = resources.displayMetrics.widthPixels.toFloat()
        val offsets = c.offsets.entries.associate { (layer, model) ->
            KeyboardView.offsetSlot(LayoutKind.SPLIT, layer, width, pxPerMmX) to model.serialize()
        }
        prefs.applyCalibration(c.placements, offsets)
        finish()
    }

    private fun layerName(layer: Layer): String =
        getString(if (layer == Layer.GENERAL) R.string.offsets_layer_general else R.string.offsets_layer_code)

    private fun percent(rate: Float): Int = (rate * 100f).roundToInt()

    private fun text(value: String, size: Float): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(palette.text)
        setPadding(0, dp(3), 0, dp(3))
    }

    private fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ZONES = "zones"
        const val EXTRA_START = "start"
    }
}

@SuppressLint("ViewConstructor")
private class TypingView(
    context: Context,
    private val palette: Palette,
    private val onTap: (Float, Float) -> Unit,
) : View(context) {
    class Final(val layer: Layer, val geometry: KeyboardGeometry, val placement: TypingCalibration.Placement)

    var session: TypingSession? = null
    var zones: Pair<Zone, Zone>? = null
    var final: Final? = null
    var bottomInset = 0
    private var flashKey: Key? = null
    private var flashHit = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val pxPerMmX: Float get() = Dpi.physical(resources.displayMetrics, true) / KeyboardView.MM_PER_INCH
    private val pxPerMmY: Float get() = Dpi.physical(resources.displayMetrics, false) / KeyboardView.MM_PER_INCH
    private val baseline: Float get() = height - bottomInset.toFloat()

    val keyboardTop: Float
        get() {
            val s = session ?: return baseline
            val p = s.round.placement
            return baseline - (p.liftMm + Layouts.rows(LayoutKind.SPLIT, s.layer) * p.rowMm) * pxPerMmY
        }

    fun flash(key: Key?, hit: Boolean) {
        flashKey = key
        flashHit = hit
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                onTap(event.getX(i), event.getY(i))
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.background)
        zones?.let { (l, r) ->
            zone(canvas, Side.LEFT, l)
            zone(canvas, Side.RIGHT, r)
        }
        val s = session ?: return
        if (s.finished) {
            val f = final ?: return
            val top = baseline - (f.placement.liftMm + Layouts.rows(LayoutKind.SPLIT, f.layer) * f.placement.rowMm) * pxPerMmY
            keys(canvas, f.geometry, f.layer, top)
            return
        }
        keys(canvas, s.geometry, s.layer, keyboardTop)
    }

    private fun keys(canvas: Canvas, geometry: KeyboardGeometry, layer: Layer, top: Float) {
        val radius = KeyboardView.RADIUS_MM * pxPerMmX
        canvas.save()
        canvas.translate(0f, top)
        paint.textAlign = Paint.Align.CENTER
        for (key in geometry.keys) {
            if (key.ghost) continue
            rect.set(key.face.left, key.face.top, key.face.right, key.face.bottom)
            paint.style = Paint.Style.FILL
            paint.color = when {
                key === flashKey -> if (flashHit) palette.accent else palette.locked
                key.def.style == KeyStyle.NORMAL -> palette.key
                else -> palette.modKey
            }
            canvas.drawRoundRect(rect, radius, radius, paint)
            val label = LayoutBuilder.previewLabel(context, key.def, layer)
            if (label.isNotEmpty()) {
                paint.color = if (key === flashKey) palette.accentText else palette.text
                paint.textSize = key.face.height * if (label.length == 1) 0.42f else 0.26f
                val max = key.face.width * 0.9f
                if (paint.measureText(label) > max) paint.textSize *= max / paint.measureText(label)
                canvas.drawText(label, key.face.centerX, key.face.centerY - (paint.descent() + paint.ascent()) / 2f, paint)
            }
        }
        canvas.restore()
    }

    private fun zone(canvas: Canvas, side: Side, z: Zone) {
        val near = z.nearMm * pxPerMmX
        val far = z.farMm * pxPerMmX
        rect.set(
            if (side == Side.LEFT) near else width - far,
            baseline - z.topMm * pxPerMmY,
            if (side == Side.LEFT) far else width - near,
            baseline - z.bottomMm * pxPerMmY,
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.3f * pxPerMmX
        paint.color = if (side == Side.LEFT) palette.accent else palette.locked
        paint.alpha = 140
        canvas.drawRect(rect, paint)
        paint.alpha = 255
    }
}
