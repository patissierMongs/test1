package io.github.patissiermongs.foldkey.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import io.github.patissiermongs.foldkey.R
import io.github.patissiermongs.foldkey.engine.Command
import io.github.patissiermongs.foldkey.engine.Gesture
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.Lang
import io.github.patissiermongs.foldkey.engine.ModState
import io.github.patissiermongs.foldkey.engine.Modifier
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.input.OffsetModel
import io.github.patissiermongs.foldkey.input.TouchParams
import io.github.patissiermongs.foldkey.input.TouchSink
import io.github.patissiermongs.foldkey.input.TouchTracker
import io.github.patissiermongs.foldkey.layout.ActionResolver
import io.github.patissiermongs.foldkey.layout.Box
import io.github.patissiermongs.foldkey.layout.GeometrySpec
import io.github.patissiermongs.foldkey.layout.Key
import io.github.patissiermongs.foldkey.layout.KeyLabels
import io.github.patissiermongs.foldkey.layout.KeyStyle
import io.github.patissiermongs.foldkey.layout.LabelState
import io.github.patissiermongs.foldkey.layout.LayoutKind
import io.github.patissiermongs.foldkey.layout.KeyboardGeometry
import io.github.patissiermongs.foldkey.layout.Layouts
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val engine: KeyboardEngine,
    private val prefs: Prefs,
    private val feedback: Feedback,
) : View(context), TouchSink {

    private var palette = Palette.of(resources.configuration)
    private var geometry: KeyboardGeometry? = null
    private val tracker = TouchTracker(this, TouchParams(1f, 1f, 1f))
    private var offsets = OffsetModel(1)
    private var offsetSlot = ""
    private var offsetsDirty = false
    private var pending: Triple<Key, Float, Float>? = null
    private var lastFireTime = Long.MIN_VALUE / 2
    private var lastWasBackspace = false
    private val active = LinkedHashMap<Int, Pair<Key, Gesture>>()
    private var preedit = ""
    private var bottomInset = 0
    private var sideInset = 0
    private val stripButtons = ArrayList<Pair<Box, Command>>()
    private var stripPointer = -1
    private var stripCommand: Command? = null
    private var rowMm = 9.5f
    private var currentKind = LayoutKind.FULL
    private var lastDetent = 0L
    private val echoes = ArrayDeque<String>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val tickRunnable = Runnable { onTick() }

    private val pxPerMmX: Float get() = physicalDpi(true) / MM_PER_INCH
    private val pxPerMmY: Float get() = physicalDpi(false) / MM_PER_INCH
    private val stripHeightPx: Float get() = STRIP_MM * pxPerMmY

    init {
        isHapticFeedbackEnabled = true
        setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val cutout = insets.getInsets(WindowInsets.Type.displayCutout())
            val side = max(max(bars.left, bars.right), max(cutout.left, cutout.right))
            if (bars.bottom != bottomInset || side != sideInset) {
                bottomInset = bars.bottom
                sideInset = side
                geometry = null
                requestLayout()
            }
            insets
        }
    }

    internal val layoutKeys: List<Key> get() = geometry().keys

    var showSwitchKey: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                layoutStrip()
                invalidate()
            }
        }

    fun echo(token: String) {
        echoes.addLast(token)
        while (echoes.size > MAX_ECHOES) echoes.removeFirst()
        invalidate()
    }

    fun setPreedit(text: String) {
        preedit = text
        invalidate()
    }

    fun reload() {
        palette = Palette.of(resources.configuration)
        geometry = null
        requestLayout()
        invalidate()
    }

    fun saveState() {
        confirmPending()
        if (offsetsDirty && offsetSlot.isNotEmpty()) {
            prefs.saveOffsets(offsetSlot, offsets.serialize())
            offsetsDirty = false
        }
    }

    fun cancelTouches() {
        tracker.cancelAll(SystemClock.uptimeMillis())
        active.clear()
        stripPointer = -1
        removeCallbacks(tickRunnable)
        invalidate()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        saveState()
        reload()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val kind = layoutKind(width)
        val rows = Layouts.rows(kind)
        val height = stripHeightPx + rows * rowHeightMm(rows) * pxPerMmY + bottomInset
        setMeasuredDimension(width, height.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        geometry = null
    }

    internal fun layoutKind(widthPx: Int): LayoutKind = when {
        widthPx / pxPerMmX < COMPACT_MAX_MM -> LayoutKind.COMPACT
        prefs.isSplit(isLandscape()) -> LayoutKind.SPLIT
        else -> LayoutKind.FULL
    }

    private fun isLandscape(): Boolean = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun rowHeightMm(rows: Int): Float {
        val budget = resources.displayMetrics.heightPixels * MAX_HEIGHT_SHARE - stripHeightPx - bottomInset
        val cap = budget / rows / pxPerMmY
        return if (cap > 0f) min(prefs.rowHeightMm, cap) else prefs.rowHeightMm
    }

    private fun geometry(): KeyboardGeometry {
        geometry?.let { return it }
        val kind = layoutKind(width)
        rowMm = rowHeightMm(Layouts.rows(kind))
        val spec = GeometrySpec(
            widthPx = width.toFloat(),
            pxPerMmX = pxPerMmX,
            pxPerMmY = pxPerMmY,
            topPx = stripHeightPx,
            rowHeightMm = rowMm,
            gapMm = GAP_MM,
            sidePaddingMm = SIDE_MM,
            sideInsetPx = sideInset.toFloat(),
            bottomPaddingPx = bottomInset.toFloat(),
            maxUnitMm = MAX_UNIT_MM,
            splitUnitMm = prefs.splitUnitMm,
            ghostUnits = if (kind == LayoutKind.SPLIT) GHOST_UNITS else 0f,
        )
        val g = when (kind) {
            LayoutKind.SPLIT -> KeyboardGeometry.split(Layouts.split, spec)
            LayoutKind.FULL -> KeyboardGeometry.full(Layouts.full, spec)
            LayoutKind.COMPACT -> KeyboardGeometry.full(Layouts.compact, spec)
        }
        tracker.params = TouchParams(
            swipeThresholdPx = SWIPE_MM * pxPerMmY,
            cursorStartPx = CURSOR_START_MM * pxPerMmX,
            cursorStepPx = CURSOR_STEP_MM * pxPerMmX,
            longPressMs = prefs.longPressMs,
        )
        val slot = "${kind.name.lowercase()}_${(width / pxPerMmX).toInt()}mm"
        if (slot != offsetSlot || offsets.zones != g.zoneCount) {
            saveState()
            offsets = OffsetModel(g.zoneCount)
            offsets.load(prefs.offsets(slot))
            offsetSlot = slot
        }
        currentKind = kind
        layoutStrip()
        geometry = g
        return g
    }

    private fun layoutStrip() {
        stripButtons.clear()
        val h = stripHeightPx
        val commands = Layouts.stripCommands.filter {
            !(it == Command.SWITCH_IME && !showSwitchKey) && !(it == Command.TOGGLE_SPLIT && currentKind == LayoutKind.COMPACT)
        }
        val available = width - SIDE_MM * pxPerMmX - STRIP_STATUS_MM * pxPerMmX
        val w = min(STRIP_BUTTON_MM * pxPerMmX, available / commands.size)
        var right = width - SIDE_MM * pxPerMmX
        for (cmd in commands.reversed()) {
            stripButtons.add(Box(right - w, 0f, right, h) to cmd)
            right -= w
        }
    }

    override fun onDraw(canvas: Canvas) {
        val g = geometry()
        canvas.drawColor(palette.background)
        drawStrip(canvas)
        val pressedDefs = active.values.map { it.first.def }
        for (key in g.keys) {
            if (key.ghost) continue
            drawKey(canvas, key, pressedDefs.any { it === key.def })
        }
        val tapPreview = when (prefs.popupMode) {
            Prefs.POPUP_ON -> true
            Prefs.POPUP_OFF -> false
            else -> resources.configuration.smallestScreenWidthDp < LARGE_SCREEN_DP
        }
        for ((_, pair) in active) {
            if (pair.second != Gesture.TAP || tapPreview) drawPopup(canvas, pair.first, pair.second)
        }
    }

    private fun drawStrip(canvas: Canvas) {
        val h = stripHeightPx
        fill.color = palette.strip
        canvas.drawRect(0f, 0f, width.toFloat(), h, fill)
        val textSize = h * 0.42f
        var x = SIDE_MM * pxPerMmX + 1.5f * pxPerMmX
        label.textAlign = Paint.Align.LEFT
        label.typeface = Typeface.DEFAULT_BOLD
        label.textSize = textSize
        label.color = palette.accent
        val baseline = h / 2f - (label.descent() + label.ascent()) / 2f
        val langText = if (engine.lang == Lang.HANGUL) "한" else "EN"
        canvas.drawText(langText, x, baseline, label)
        x += label.measureText(langText) + 3f * pxPerMmX
        label.typeface = Typeface.DEFAULT
        for (m in Modifier.entries) {
            val st = engine.modifiers.state(m)
            if (st == ModState.OFF && !engine.modifiers.isHeld(m)) continue
            val name = when (m) {
                Modifier.SHIFT -> "Shift"
                Modifier.CTRL -> "Ctrl"
                Modifier.ALT -> "Alt"
                Modifier.FN -> "Fn"
            } + if (st == ModState.LOCKED) "🔒" else ""
            label.color = if (st == ModState.LOCKED) palette.locked else palette.accent
            canvas.drawText(name, x, baseline, label)
            x += label.measureText(name) + 2.5f * pxPerMmX
        }
        if (preedit.isNotEmpty()) {
            label.color = palette.text
            label.textSize = textSize * 1.15f
            x += 2f * pxPerMmX
            canvas.drawText(preedit, x, baseline, label)
            val tw = label.measureText(preedit)
            fill.color = palette.accent
            canvas.drawRect(x, h * 0.82f, x + tw, h * 0.82f + 0.35f * pxPerMmY, fill)
            x += tw
            label.textSize = textSize
        }
        val limit = (stripButtons.minOfOrNull { it.first.left } ?: width.toFloat()) - 2f * pxPerMmX
        if (echoes.isNotEmpty()) {
            label.color = palette.hint
            label.typeface = Typeface.MONOSPACE
            label.textSize = textSize * 0.85f
            val start = x + 3f * pxPerMmX
            var shown = echoes.toList()
            while (shown.isNotEmpty() && start + label.measureText(shown.joinToString(" ")) > limit) shown = shown.drop(1)
            if (shown.isNotEmpty()) canvas.drawText(shown.joinToString(" "), start, baseline, label)
            label.typeface = Typeface.DEFAULT
            label.textSize = textSize
        }
        label.textAlign = Paint.Align.CENTER
        for ((box, cmd) in stripButtons) {
            val text = when (cmd) {
                Command.PASTE -> context.getString(R.string.strip_paste)
                Command.TOGGLE_SPLIT -> context.getString(if (currentKind == LayoutKind.SPLIT) R.string.strip_full else R.string.strip_split)
                Command.SETTINGS -> context.getString(R.string.strip_settings)
                Command.HIDE -> context.getString(R.string.strip_hide)
                Command.SWITCH_IME -> context.getString(R.string.strip_switch)
            }
            label.color = if (stripPointer >= 0 && stripCommand == cmd) palette.accent else palette.hint
            label.textSize = textSize * 0.9f
            val limit = box.width * 0.9f
            val measured = label.measureText(text)
            if (measured > limit) label.textSize *= limit / measured
            canvas.drawText(text, box.centerX, baseline, label)
        }
    }

    private fun drawKey(canvas: Canvas, key: Key, pressed: Boolean) {
        val def = key.def
        val face = key.face
        var bg = if (def.style == KeyStyle.NORMAL || def.style == KeyStyle.SPACE) palette.key else palette.modKey
        var fg = palette.text
        val action = def.action
        val modState: ModState? = when (action) {
            is KeyAction.Mod -> if (engine.modifiers.isHeld(action.modifier)) ModState.ONESHOT else engine.modifiers.state(action.modifier)
            KeyAction.EscCtrl -> if (engine.modifiers.isHeld(Modifier.CTRL) && pressed) ModState.ONESHOT else null
            else -> null
        }
        when {
            pressed -> bg = palette.pressed
            modState == ModState.ONESHOT -> { bg = palette.accent; fg = palette.accentText }
            modState == ModState.LOCKED -> { bg = palette.locked; fg = palette.accentText }
        }
        val radius = RADIUS_MM * pxPerMmX
        rect.set(face.left, face.top + SHADOW_MM * pxPerMmY, face.right, face.bottom + SHADOW_MM * pxPerMmY)
        fill.color = palette.shadow
        canvas.drawRoundRect(rect, radius, radius, fill)
        rect.set(face.left, face.top, face.right, face.bottom)
        fill.color = bg
        canvas.drawRoundRect(rect, radius, radius, fill)

        val state = labelState()
        val main = KeyLabels.main(def, state)
        val single = main.codePointCount(0, main.length) <= 1
        label.color = fg
        label.textAlign = Paint.Align.CENTER
        label.typeface = if (action is KeyAction.Char && !action.isLetter) Typeface.MONOSPACE else Typeface.DEFAULT
        label.textSize = if (single) face.height * 0.44f else face.height * 0.28f
        val maxWidth = face.width * 0.86f
        if (label.measureText(main) > maxWidth) label.textSize *= maxWidth / label.measureText(main)
        val cy = face.centerY + face.height * (if (def.downLabel != null && !state.fn) -0.03f else 0.06f)
        canvas.drawText(main, face.centerX, cy - (label.descent() + label.ascent()) / 2f, label)

        hint.color = if (fg == palette.text) palette.hint else fg
        hint.textSize = face.height * 0.22f
        hint.typeface = Typeface.DEFAULT
        val top = KeyLabels.top(def, state)
        if (top != null) {
            hint.textAlign = Paint.Align.RIGHT
            canvas.drawText(top, face.right - face.width * 0.1f, face.top + face.height * 0.26f, hint)
        }
        val bottom = KeyLabels.bottom(def, state)
        if (bottom != null) {
            val latin = def.downLabel == null
            hint.textAlign = if (latin) Paint.Align.RIGHT else Paint.Align.LEFT
            val hx = if (latin) face.right - face.width * 0.1f else face.left + face.width * 0.1f
            canvas.drawText(bottom, hx, face.bottom - face.height * 0.1f, hint)
        }
        if (def.style == KeyStyle.SPACE && main.isEmpty()) {
            fill.color = palette.hint
            val barW = min(face.width * 0.25f, 12f * pxPerMmX)
            canvas.drawRect(face.centerX - barW / 2f, face.bottom - face.height * 0.28f, face.centerX + barW / 2f, face.bottom - face.height * 0.28f + 0.3f * pxPerMmY, fill)
        }
    }

    private fun labelState(): LabelState = LabelState(
        lang = engine.lang,
        shiftLetters = engine.modifiers.shiftForLetters(),
        shiftSymbols = engine.modifiers.shiftForSymbols(),
        fn = engine.modifiers.isActive(Modifier.FN),
        latinHints = prefs.latinHints,
        swipeDownCtrl = prefs.swipeDownCtrl,
    )

    private fun drawPopup(canvas: Canvas, key: Key, gesture: Gesture) {
        val def = key.def
        if (def.isModifier || def.isSpace) return
        if (def.action !is KeyAction.Char && gesture == Gesture.TAP) return
        val text = KeyLabels.popup(def, gesture, labelState()) ?: return
        val face = key.face
        val w = max(face.width * 1.25f, 9f * pxPerMmX)
        val h = face.height * 1.15f
        var top = face.top - h - 1.2f * pxPerMmY
        if (top < 0f) top = 0f
        val left = (face.centerX - w / 2f).coerceIn(0f, width - w)
        rect.set(left, top, left + w, top + h)
        fill.color = palette.shadow
        canvas.drawRoundRect(rect.left, rect.top + SHADOW_MM * pxPerMmY, rect.right, rect.bottom + SHADOW_MM * pxPerMmY, RADIUS_MM * pxPerMmX * 1.5f, RADIUS_MM * pxPerMmX * 1.5f, fill)
        fill.color = palette.popup
        canvas.drawRoundRect(rect, RADIUS_MM * pxPerMmX * 1.5f, RADIUS_MM * pxPerMmX * 1.5f, fill)
        label.color = palette.popupText
        label.textAlign = Paint.Align.CENTER
        label.typeface = Typeface.DEFAULT_BOLD
        label.textSize = if (text.codePointCount(0, text.length) <= 1) h * 0.55f else h * 0.34f
        canvas.drawText(text, rect.centerX(), rect.centerY() - (label.descent() + label.ascent()) / 2f, label)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val t = event.eventTime
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val id = event.getPointerId(i)
                val x = event.getX(i)
                val y = event.getY(i)
                if (y < stripHeightPx) {
                    val cmd = stripButtons.firstOrNull { it.first.contains(x, y) }?.second
                    if (cmd != null && stripPointer < 0) {
                        stripPointer = id
                        stripCommand = cmd
                        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                } else {
                    tracker.down(id, x, y, t)
                }
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                val id = event.getPointerId(i)
                if (id != stripPointer) tracker.move(id, event.getX(i), event.getY(i), t)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                val id = event.getPointerId(i)
                val canceled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    (event.flags and MotionEvent.FLAG_CANCELED) != 0
                if (canceled) {
                    if (id == stripPointer) {
                        stripPointer = -1
                        stripCommand = null
                    } else {
                        tracker.cancel(id, t)
                    }
                } else if (id == stripPointer) {
                    val cmd = stripCommand
                    val hitCmd = stripButtons.firstOrNull { it.first.contains(event.getX(i), event.getY(i)) }?.second
                    stripPointer = -1
                    stripCommand = null
                    if (cmd != null && cmd == hitCmd) engine.perform(KeyAction.Cmd(cmd))
                } else {
                    tracker.up(id, event.getX(i), event.getY(i), t)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                tracker.cancelAll(t)
                stripPointer = -1
            }
        }
        scheduleTick()
        invalidate()
        return true
    }

    private fun onTick() {
        tracker.tick(SystemClock.uptimeMillis())
        scheduleTick()
        invalidate()
    }

    private fun scheduleTick() {
        removeCallbacks(tickRunnable)
        val deadline = tracker.nextDeadline() ?: return
        postDelayed(tickRunnable, max(0L, deadline - SystemClock.uptimeMillis()))
    }

    override fun hit(x: Float, y: Float, t: Long): Key? {
        val g = geometry()
        if (y < stripHeightPx) return null
        g.keys.firstOrNull { k ->
            !k.ghost && k.def.style == KeyStyle.NORMAL &&
                abs(x - k.face.centerX) <= k.face.width * ANCHOR_SHARE &&
                abs(y - k.face.centerY) <= k.face.height * ANCHOR_SHARE
        }?.let { return it }
        if (prefs.adaptive && !lastWasBackspace && t - lastFireTime <= ADAPT_MAX_GAP_MS) {
            val (cx, cy) = offsets.correctionMm(g.zoneAt(x, y), g.unitPx / pxPerMmX, rowMm)
            val corrected = g.keyAt(x - cx * pxPerMmX, y - cy * pxPerMmY)
            if (corrected != null) return corrected
        }
        return g.keyAt(x, y)
    }

    override fun keyDown(pointer: Int, key: Key, t: Long) {
        active[pointer] = key to Gesture.TAP
        feedback.press(this, key)
    }

    override fun modifierDown(key: Key, t: Long) = engine.press(key.def.action, t)

    override fun modifierUp(key: Key, t: Long) = engine.release(key.def.action, t)

    override fun fire(key: Key, gesture: Gesture, t: Long) {
        val r = ActionResolver.resolve(key.def, gesture, engine.modifiers.isActive(Modifier.FN), prefs.swipeDownCtrl)
        lastWasBackspace = r.action == KeyAction.Backspace
        lastFireTime = t
        if (lastWasBackspace) pending = null else confirmPending()
        engine.perform(r.action, r.forceShift, r.forceCtrl)
    }

    override fun variant(pointer: Int, key: Key, gesture: Gesture) {
        val previous = active[pointer]?.second
        active[pointer] = key to gesture
        if (gesture != Gesture.TAP && gesture != previous) feedback.detent(this)
    }

    override fun released(pointer: Int) {
        active.remove(pointer)
    }

    override fun cursor(steps: Int) {
        engine.moveCursor(steps)
        val now = SystemClock.uptimeMillis()
        if (now - lastDetent >= DETENT_MIN_INTERVAL_MS) {
            lastDetent = now
            feedback.detent(this)
        }
    }

    override fun cursorEnd() = engine.endCursorMove()

    override fun sample(key: Key, x: Float, y: Float) {
        if (prefs.adaptive && key.def.style == KeyStyle.NORMAL && !key.ghost) pending = Triple(key, x, y)
    }

    private fun confirmPending() {
        val p = pending ?: return
        pending = null
        val (key, x, y) = p
        val added = offsets.add(
            key.zone,
            (x - key.face.centerX) / pxPerMmX,
            (y - key.face.centerY) / pxPerMmY,
            key.touch.width / pxPerMmX,
            rowMm,
        )
        if (added) offsetsDirty = true
    }

    private fun physicalDpi(horizontal: Boolean): Float {
        val dm: DisplayMetrics = resources.displayMetrics
        val reported = if (horizontal) dm.xdpi else dm.ydpi
        val nominal = dm.densityDpi.toFloat()
        return if (reported > 0f && abs(reported - nominal) / nominal < 0.35f) reported else nominal
    }

    companion object {
        const val MM_PER_INCH = 25.4f
        const val STRIP_MM = 6.5f
        const val STRIP_BUTTON_MM = 17f
        const val STRIP_STATUS_MM = 16f
        const val GAP_MM = 0.9f
        const val SIDE_MM = 0.5f
        const val RADIUS_MM = 1.0f
        const val SHADOW_MM = 0.25f
        const val MAX_UNIT_MM = 11.5f
        const val GHOST_UNITS = 1f
        const val SWIPE_MM = 4.0f
        const val CURSOR_START_MM = 3.0f
        const val CURSOR_STEP_MM = 2.5f
        const val ANCHOR_SHARE = 0.25f
        const val COMPACT_MAX_MM = 110f
        const val MAX_HEIGHT_SHARE = 0.5f
        const val LARGE_SCREEN_DP = 600
        const val DETENT_MIN_INTERVAL_MS = 40L
        const val MAX_ECHOES = 8
        const val ADAPT_MAX_GAP_MS = 1000L
    }
}
