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
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import io.github.patissiermongs.foldkey.R
import io.github.patissiermongs.foldkey.engine.Command
import io.github.patissiermongs.foldkey.engine.Gesture
import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.KeyboardEngine
import io.github.patissiermongs.foldkey.engine.Lang
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.engine.ModState
import io.github.patissiermongs.foldkey.engine.Modifier
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.input.OffsetModel
import io.github.patissiermongs.foldkey.input.TapHeat
import io.github.patissiermongs.foldkey.input.TouchParams
import io.github.patissiermongs.foldkey.input.TouchSink
import io.github.patissiermongs.foldkey.input.TouchTracker
import io.github.patissiermongs.foldkey.layout.ActionResolver
import io.github.patissiermongs.foldkey.layout.Box
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
    private var geometryLayer = Layer.CODE
    private val tracker = TouchTracker(this, TouchParams(1f, 1f, 1f))
    private var offsets = OffsetModel(1)
    private var offsetSlot = ""
    private var offsetsDirty = false
    private var offsetsEpoch = -1
    private var heat = TapHeat()
    private var heatDirty = false
    private val samples = ArrayDeque<Tap>()
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
    private var clipPointer = -1
    private var clipIndex = -1
    private var clipDownText: String? = null
    private var clipDownY = 0f
    private var clipScrollAtDown = 0
    private var clipDragged = false
    private var clipLongPressed = false
    private var clipScroll = 0
    private var clipMenu: String? = null
    private val clipLongPress = Runnable { onClipLongPress() }

    var clipSource: () -> List<Clip> = { emptyList() }
    var onClipEdit: (ClipEdit, String) -> Unit = { _, _ -> }
    var pinsFull: () -> Boolean = { false }

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

    internal val fnLayoutKeys: List<Key> get() = geometry().fnKeys

    private val fnOn: Boolean get() = engine.modifiers.isActive(Modifier.FN)

    var showEditCommands: Boolean = true
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

    val hasPanel: Boolean get() = width > 0 && geometry().panel.isNotEmpty()

    internal val clipSlots: List<Box> get() = clipBoxes(geometry().panel)

    internal val openClipMenu: String? get() = clipMenu

    internal val clipOffset: Int get() = clipScroll

    internal fun stripBox(command: Command): Box? = stripButtons.firstOrNull { it.second == command }?.first

    fun reload() {
        palette = Palette.of(resources.configuration)
        geometry = null
        requestLayout()
        invalidate()
    }

    fun saveState() {
        dropStaleOffsets()
        while (samples.isNotEmpty()) learn(samples.removeFirst())
        if (offsetsDirty && offsetSlot.isNotEmpty()) {
            prefs.saveOffsets(offsetSlot, offsets.serialize())
            offsetsDirty = false
        }
        if (heatDirty && offsetSlot.isNotEmpty()) {
            prefs.saveHeat(offsetSlot, heat.serialize())
            heatDirty = false
        }
    }

    fun cancelTouches() {
        tracker.cancelAll(SystemClock.uptimeMillis())
        active.clear()
        stripPointer = -1
        resetClipTouch()
        clipMenu = null
        clipScroll = 0
        removeCallbacks(tickRunnable)
        invalidate()
    }

    private fun resetClipTouch() {
        removeCallbacks(clipLongPress)
        clipPointer = -1
        clipIndex = -1
        clipDownText = null
        clipDragged = false
        clipLongPressed = false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        saveState()
        reload()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestApplyInsets()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val kind = layoutKind(width)
        val rows = Layouts.rows(kind, engine.layer)
        val lift = liftPx(kind)
        val height = stripHeightPx + rows * rowHeightMm(rows, lift) * pxPerMmY + lift + bottomInset
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

    private fun liftPx(kind: LayoutKind): Float = if (kind == LayoutKind.SPLIT) prefs.splitLiftMm * pxPerMmY else 0f

    private fun rowHeightMm(rows: Int, lift: Float): Float {
        val budget = resources.displayMetrics.heightPixels * MAX_HEIGHT_SHARE - stripHeightPx - bottomInset - lift
        val cap = budget / rows / pxPerMmY
        return if (cap > 0f) min(prefs.rowHeightMm, cap) else prefs.rowHeightMm
    }

    private fun geometry(): KeyboardGeometry {
        val layer = engine.layer
        geometry?.let { if (geometryLayer == layer) return it }
        val kind = layoutKind(width)
        val lift = liftPx(kind)
        rowMm = rowHeightMm(Layouts.rows(kind, layer), lift)
        val panelWanted = kind == LayoutKind.SPLIT && prefs.centerClipboard
        val spec = LayoutBuilder.spec(
            prefs, kind, layer, width.toFloat(), pxPerMmX, pxPerMmY, stripHeightPx, rowMm,
            bottomPx = bottomInset.toFloat(),
            sideInsetPx = sideInset.toFloat(),
            liftPx = lift,
            panelMinPx = if (panelWanted) PANEL_MIN_MM * pxPerMmX else 0f,
        )
        dropStaleOffsets()
        val g = LayoutBuilder.build(kind, layer, spec)
        tracker.params = TouchParams(
            swipeThresholdPx = SWIPE_MM * pxPerMmY,
            cursorStartPx = CURSOR_START_MM * pxPerMmX,
            cursorStepPx = CURSOR_STEP_MM * pxPerMmX,
            cursorRowStepPx = CURSOR_ROW_STEP_MM * pxPerMmY,
            longPressMs = prefs.longPressMs,
            longPressRepeats = prefs.longPressAction == Prefs.LONG_PRESS_REPEAT,
            holdMs = prefs.longPressMs.takeIf { it > 0L } ?: HOLD_MS,
        )
        val slot = offsetSlot(kind, layer, width.toFloat(), pxPerMmX)
        if (slot != offsetSlot || offsets.zones != g.zoneCount) {
            saveState()
            offsets = OffsetModel(g.zoneCount)
            offsets.load(prefs.offsets(slot))
            offsetSlot = slot
            heat = TapHeat.parse(prefs.heat(slot), prefs.layoutSignature)
            heatDirty = false
        } else if (heat.signature != prefs.layoutSignature) {
            saveState()
            heat = TapHeat(prefs.layoutSignature)
            heatDirty = false
        }
        currentKind = kind
        layoutStrip()
        geometry = g
        geometryLayer = layer
        return g
    }

    private fun layoutStrip() {
        stripButtons.clear()
        val h = stripHeightPx
        val commands = Layouts.stripCommands.filter { it !in EDIT_COMMANDS || showEditCommands }
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
        val selecting = active.values.any { it.first.def.isSpace && it.second == Gesture.LONG }
        for (key in g.layer(fnOn)) {
            if (key.ghost) continue
            drawKey(canvas, key, pressedDefs.any { it === key.def }, selecting)
        }
        if (g.panel.isNotEmpty()) drawPanel(canvas, g.panel)
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
            } + if (st == ModState.LOCKED && !m.latches) "🔒" else ""
            label.color = if (st == ModState.LOCKED && !m.latches) palette.locked else palette.accent
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
                Command.SELECT_ALL -> context.getString(R.string.strip_select_all)
                Command.COPY -> context.getString(R.string.strip_copy)
                Command.CUT -> context.getString(R.string.strip_cut)
                Command.PASTE -> context.getString(R.string.strip_paste)
                Command.SETTINGS -> context.getString(R.string.strip_settings)
                Command.HIDE -> context.getString(R.string.strip_hide)
                Command.TOGGLE_LAYER -> layerLabel()
            }
            label.color = if (stripPointer >= 0 && stripCommand == cmd) palette.accent else palette.hint
            label.textSize = textSize * 0.9f
            val limit = box.width * 0.9f
            val measured = label.measureText(text)
            if (measured > limit) label.textSize *= limit / measured
            canvas.drawText(text, box.centerX, baseline, label)
        }
    }

    private fun clipBoxes(panel: List<Box>): List<Box> {
        if (!prefs.centerClipboard || panel.isEmpty()) return emptyList()
        return panel.dropLast(1).flatMap { b ->
            val h = b.height / CLIP_SLOTS_PER_ROW
            List(CLIP_SLOTS_PER_ROW) { i -> Box(b.left, b.top + i * h, b.right, b.top + (i + 1) * h) }
        }
    }

    private fun clipAt(x: Float, y: Float): Int {
        val boxes = clipBoxes(geometry().panel)
        if (boxes.isEmpty()) return -1
        val count = clipSource().size
        clampClipScroll(count, boxes.size)
        boxes.forEachIndexed { i, b -> if (clipScroll + i < count && b.contains(x, y)) return clipScroll + i }
        return -1
    }

    private fun clampClipScroll(count: Int, slots: Int) {
        clipScroll = clipScroll.coerceIn(0, max(0, count - slots))
    }

    private fun clipBox(index: Int): Box? = clipBoxes(geometry().panel).getOrNull(index - clipScroll)

    private fun onClipLongPress() {
        if (clipPointer < 0 || clipDragged) return
        val text = clipDownText ?: return
        if (clipSource().none { it.text == text }) return
        clipLongPressed = true
        clipMenu = text
        feedback.longPress(this)
        invalidate()
    }

    private fun tapClip(index: Int, x: Float) {
        val clip = clipSource().getOrNull(index) ?: return
        if (clip.text != clipDownText) return
        val menu = clipMenu
        if (menu == null) {
            engine.pasteText(clip.text)
            return
        }
        clipMenu = null
        if (menu != clip.text) return
        val box = clipBox(index) ?: return
        when {
            x >= box.centerX -> onClipEdit(ClipEdit.DELETE, clip.text)
            clip.pinned -> onClipEdit(ClipEdit.UNPIN, clip.text)
            !pinsFull() -> onClipEdit(ClipEdit.PIN, clip.text)
        }
    }

    private fun drawPanel(canvas: Canvas, panel: List<Box>) {
        val inset = GAP_MM * pxPerMmX / 2f
        val radius = RADIUS_MM * pxPerMmX
        val boxes = clipBoxes(panel)
        if (boxes.isNotEmpty()) {
            val clips = clipSource()
            clampClipScroll(clips.size, boxes.size)
            if (clipMenu != null && clips.none { it.text == clipMenu }) clipMenu = null
            hint.textAlign = Paint.Align.LEFT
            hint.typeface = Typeface.DEFAULT
            hint.textSize = CLIP_TEXT_MM * pxPerMmY
            if (clips.isEmpty()) {
                hint.color = palette.hint
                val b = boxes[0]
                drawFitted(canvas, context.getString(R.string.panel_clip_empty), b.left + 2f * inset, b.centerY, b.width - 4f * inset, hint)
            }
            for ((slot, b) in boxes.withIndex()) {
                val index = clipScroll + slot
                val clip = clips.getOrNull(index) ?: break
                rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
                if (clip.text == clipMenu) {
                    drawClipMenu(canvas, clip, inset, radius)
                    continue
                }
                val pressed = clipPointer >= 0 && clipIndex == index && !clipDragged
                fill.color = if (pressed) palette.pressed else palette.modKey
                canvas.drawRoundRect(rect, radius, radius, fill)
                hint.color = palette.text
                val shown = if (clip.pinned) PIN_MARK + preview(clip.text) else preview(clip.text)
                drawFitted(canvas, shown, rect.left + 2f * inset, rect.centerY(), rect.width() - 4f * inset, hint)
            }
            if (clips.size > boxes.size) drawClipScrollbar(canvas, boxes, clips.size, inset)
        }
    }

    private fun drawClipMenu(canvas: Canvas, clip: Clip, inset: Float, radius: Float) {
        val pinText = context.getString(
            when {
                clip.pinned -> R.string.clip_unpin
                pinsFull() -> R.string.clip_pins_full
                else -> R.string.clip_pin
            }
        )
        val left = rect.left
        val right = rect.right
        val middle = rect.centerX()
        rect.set(left, rect.top, middle - inset, rect.bottom)
        drawMenuButton(canvas, pinText, clip.pinned || !pinsFull(), inset, radius)
        rect.set(middle + inset, rect.top, right, rect.bottom)
        drawMenuButton(canvas, context.getString(R.string.clip_delete), true, inset, radius)
    }

    private fun drawMenuButton(canvas: Canvas, text: String, enabled: Boolean, inset: Float, radius: Float) {
        fill.color = palette.pressed
        canvas.drawRoundRect(rect, radius, radius, fill)
        val size = hint.textSize
        val width = rect.width() - 2f * inset
        val measured = hint.measureText(text)
        if (measured > width && measured > 0f) hint.textSize = size * width / measured
        hint.textAlign = Paint.Align.CENTER
        hint.color = if (enabled) palette.accent else palette.hint
        canvas.drawText(text, rect.centerX(), rect.centerY() - (hint.descent() + hint.ascent()) / 2f, hint)
        hint.textAlign = Paint.Align.LEFT
        hint.textSize = size
    }

    private fun drawClipScrollbar(canvas: Canvas, boxes: List<Box>, count: Int, inset: Float) {
        val width = CLIP_BAR_MM * pxPerMmX
        val from = boxes.size.toFloat() * clipScroll / count
        val to = boxes.size.toFloat() * (clipScroll + boxes.size).coerceAtMost(count) / count
        fill.color = palette.hint
        for ((i, b) in boxes.withIndex()) {
            val start = max(from, i.toFloat()) - i
            val end = min(to, i + 1f) - i
            if (end <= start) continue
            val top = b.top + inset
            val track = b.height - 2f * inset
            val x = b.right - inset / 2f - width
            canvas.drawRect(x, top + track * start, x + width, top + track * end, fill)
        }
    }

    private fun preview(text: String): String =
        text.take(PREVIEW_CHARS).trim().replace(Regex("[ \\t]*\\r?\\n\\s*"), " ⏎ ").replace('\t', ' ')

    private fun drawFitted(canvas: Canvas, text: String, x: Float, centerY: Float, maxWidth: Float, paint: Paint) {
        var shown = text
        if (maxWidth <= 0f) return
        if (paint.measureText(shown) > maxWidth) {
            var n = paint.breakText(text, true, maxWidth - paint.measureText("…"), null)
            if (n > 0 && Character.isHighSurrogate(text[n - 1])) n--
            shown = text.substring(0, n) + "…"
        }
        canvas.drawText(shown, x, centerY - (paint.descent() + paint.ascent()) / 2f, paint)
    }

    private fun drawKey(canvas: Canvas, key: Key, pressed: Boolean, selecting: Boolean) {
        val def = key.def
        val face = key.face
        var bg = if (def.style == KeyStyle.NORMAL || def.style == KeyStyle.SPACE) palette.key else palette.modKey
        var fg = palette.text
        val action = def.action
        val modState: ModState? = when (action) {
            is KeyAction.Mod -> {
                val m = action.modifier
                if (engine.modifiers.isHeld(m) || m.latches && engine.modifiers.isLocked(m)) ModState.ONESHOT else engine.modifiers.state(m)
            }
            KeyAction.EscCtrl -> if (engine.modifiers.isHeld(Modifier.CTRL) && pressed) ModState.ONESHOT else null
            else -> null
        }
        when {
            pressed && selecting && def.isSpace -> { bg = palette.accent; fg = palette.accentText }
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
        val bottom = KeyLabels.bottom(def, state)
        val latin = KeyLabels.latin(def, state)
        val single = main.codePointCount(0, main.length) <= 1
        label.color = fg
        label.textAlign = Paint.Align.CENTER
        label.typeface = if (action is KeyAction.Char && !action.isLetter) Typeface.MONOSPACE else Typeface.DEFAULT
        label.textSize = if (single) face.height * 0.44f else face.height * 0.28f
        val maxWidth = face.width * 0.86f
        if (label.measureText(main) > maxWidth) label.textSize *= maxWidth / label.measureText(main)
        val cy = face.centerY + face.height * (if (bottom != null) -0.03f else 0.06f)
        canvas.drawText(main, face.centerX, cy - (label.descent() + label.ascent()) / 2f, label)

        hint.color = if (fg == palette.text) palette.hint else fg
        hint.textSize = face.height * 0.22f
        hint.typeface = Typeface.DEFAULT
        val top = KeyLabels.top(def, state)
        if (top != null) {
            hint.textAlign = Paint.Align.RIGHT
            canvas.drawText(top, face.right - face.width * 0.1f, face.top + face.height * 0.26f, hint)
        }
        if (bottom != null) {
            hint.textAlign = Paint.Align.LEFT
            canvas.drawText(bottom, face.left + face.width * 0.1f, face.bottom - face.height * 0.1f, hint)
        }
        if (latin != null) {
            hint.textAlign = Paint.Align.RIGHT
            canvas.drawText(latin, face.right - face.width * 0.1f, face.bottom - face.height * 0.1f, hint)
        }
        if (def.style == KeyStyle.SPACE && main.isEmpty()) {
            fill.color = if (fg == palette.text) palette.hint else fg
            val barW = min(face.width * 0.25f, 12f * pxPerMmX)
            canvas.drawRect(face.centerX - barW / 2f, face.bottom - face.height * 0.28f, face.centerX + barW / 2f, face.bottom - face.height * 0.28f + 0.3f * pxPerMmY, fill)
        }
    }

    private fun labelState(): LabelState = LabelState(
        lang = engine.lang,
        shiftText = engine.modifiers.shiftForText(),
        shiftJamo = engine.modifiers.shiftHeldOrOnce(),
        fn = engine.modifiers.isActive(Modifier.FN),
        latinHints = prefs.latinHints,
        swipeDownCtrl = prefs.swipeDownCtrl,
    )

    private fun layerLabel(): String =
        context.getString(if (engine.layer == Layer.GENERAL) R.string.key_layer_code else R.string.key_layer_general)

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
                    clipMenu = null
                    val cmd = stripButtons.firstOrNull { it.first.contains(x, y) }?.second
                    if (cmd != null && stripPointer < 0) {
                        stripPointer = id
                        stripCommand = cmd
                        feedback.button(this)
                    }
                } else {
                    val clip = clipAt(x, y)
                    if (clip < 0) {
                        clipMenu = null
                        tracker.down(id, x, y, t)
                    } else if (clipPointer < 0) {
                        clipPointer = id
                        clipIndex = clip
                        clipDownText = clipSource().getOrNull(clip)?.text
                        clipDownY = y
                        clipScrollAtDown = clipScroll
                        clipDragged = false
                        clipLongPressed = false
                        if (clipMenu == null) postDelayed(clipLongPress, clipLongPressMs())
                        feedback.button(this)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                val id = event.getPointerId(i)
                if (id == clipPointer) {
                    dragClips(event.getY(i))
                } else if (id != stripPointer) {
                    tracker.move(id, event.getX(i), event.getY(i), t)
                }
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
                    } else if (id == clipPointer) {
                        resetClipTouch()
                    } else {
                        tracker.cancel(id, t)
                    }
                } else if (id == clipPointer) {
                    val index = clipIndex
                    val x = event.getX(i)
                    val hit = clipAt(x, event.getY(i))
                    val tapped = !clipDragged && !clipLongPressed
                    if (tapped && index >= 0 && index == hit) {
                        tracker.flushPending(t)
                        tapClip(index, x)
                    }
                    resetClipTouch()
                } else if (id == stripPointer) {
                    val cmd = stripCommand
                    val hitCmd = stripButtons.firstOrNull { it.first.contains(event.getX(i), event.getY(i)) }?.second
                    stripPointer = -1
                    stripCommand = null
                    if (cmd != null && cmd == hitCmd) {
                        tracker.flushPending(t)
                        val layer = engine.layer
                        engine.perform(KeyAction.Cmd(cmd))
                        if (engine.layer != layer) reload()
                    }
                } else {
                    tracker.up(id, event.getX(i), event.getY(i), t)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                tracker.cancelAll(t)
                stripPointer = -1
                resetClipTouch()
            }
        }
        scheduleTick()
        invalidate()
        return true
    }

    private fun dragClips(y: Float) {
        val panel = geometry().panel ?: return
        val boxes = clipBoxes(panel)
        if (boxes.isEmpty()) return
        val dy = y - clipDownY
        if (!clipDragged && abs(dy) >= CLIP_DRAG_MM * pxPerMmY) {
            clipDragged = true
            removeCallbacks(clipLongPress)
        }
        if (!clipDragged || clipLongPressed) return
        val step = boxes.first().height
        clipScroll = clipScrollAtDown - (dy / step).toInt()
        clampClipScroll(clipSource().size, boxes.size)
    }

    private fun clipLongPressMs(): Long = prefs.longPressMs.takeIf { it > 0L } ?: CLIP_LONG_PRESS_MS

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
        dropStaleOffsets()
        val fn = fnOn
        g.layer(fn).firstOrNull { k ->
            !k.ghost &&
                abs(x - k.face.centerX) <= k.face.width * ANCHOR_SHARE &&
                abs(y - k.face.centerY) <= k.face.height * ANCHOR_SHARE
        }?.let { return it }
        val raw = g.keyAt(x, y, fn)
        if (prefs.adaptive && !lastWasBackspace && t - lastFireTime <= ADAPT_MAX_GAP_MS) {
            val (cx, cy) = offsets.correctionMm(g.zoneAt(x, y, fn), g.unitPx / pxPerMmX, rowMm)
            val corrected = g.keyAt(x - cx * pxPerMmX, y - cy * pxPerMmY, fn)
            if (corrected != null && !(corrected.ghost && raw != null && !raw.ghost)) return corrected
        }
        return raw
    }

    override fun keyDown(pointer: Int, key: Key, t: Long) {
        active[pointer] = key to Gesture.TAP
        feedback.press(this, key)
    }

    override fun modifierDown(key: Key, t: Long) = engine.press(key.def.action, t)

    override fun modifierUp(key: Key, t: Long) = engine.release(key.def.action, t)

    override fun modifierCancel(key: Key, t: Long) = engine.cancel(key.def.action)

    override fun fire(key: Key, gesture: Gesture, t: Long) {
        val r = ActionResolver.resolve(key.def, gesture, prefs.swipeDownCtrl)
        lastWasBackspace = r.action == KeyAction.Backspace
        lastFireTime = t
        if (lastWasBackspace) samples.removeLastOrNull()
        val layer = engine.layer
        engine.perform(r.action, r.forceShift, r.forceCtrl, repeat = gesture == Gesture.REPEAT)
        if (engine.layer != layer) reload()
    }

    override fun variant(pointer: Int, key: Key, gesture: Gesture) {
        val previous = active[pointer]?.second
        active[pointer] = key to gesture
        if (gesture != Gesture.TAP && gesture != previous) feedback.detent(this)
    }

    override fun released(pointer: Int) {
        active.remove(pointer)
    }

    override fun cursor(steps: Int, select: Boolean) {
        engine.moveCursor(steps, select)
        detent()
    }

    override fun cursorRows(steps: Int, select: Boolean) {
        engine.moveCursorRows(steps, select)
        detent()
    }

    private fun detent() {
        val now = SystemClock.uptimeMillis()
        if (now - lastDetent >= DETENT_MIN_INTERVAL_MS) {
            lastDetent = now
            feedback.detent(this)
        }
    }

    override fun cursorEnd() = engine.endCursorMove()

    override fun sample(key: Key, x: Float, y: Float) {
        if (key.def.style != KeyStyle.NORMAL || key.ghost || key.pad) return
        samples.addLast(Tap(key, x, y, engine.context.secret))
        while (samples.size > SAMPLE_DELAY) learn(samples.removeFirst())
    }

    private fun dropStaleOffsets() {
        val epoch = prefs.offsetsEpoch
        if (epoch == offsetsEpoch) return
        offsetsEpoch = epoch
        samples.clear()
        offsets = OffsetModel(offsets.zones).also { if (offsetSlot.isNotEmpty()) it.load(prefs.offsets(offsetSlot)) }
        offsetsDirty = false
        heat = TapHeat.parse(if (offsetSlot.isEmpty()) null else prefs.heat(offsetSlot), prefs.layoutSignature)
        heatDirty = false
    }

    private class Tap(val key: Key, val x: Float, val y: Float, val secret: Boolean)

    private fun learn(tap: Tap) {
        val key = tap.key
        val dx = (tap.x - key.face.centerX) / pxPerMmX
        val dy = (tap.y - key.face.centerY) / pxPerMmY
        if (prefs.adaptive && offsets.add(key.zone, dx, dy, key.touch.width / pxPerMmX, rowMm)) offsetsDirty = true
        val c = (key.def.action as? KeyAction.Char)?.base ?: return
        val halfWidth = (key.face.width / pxPerMmX + GAP_MM) / 2f
        if (!tap.secret && heat.add(c, dx, dy, halfWidth, rowMm / 2f)) heatDirty = true
    }

    private fun physicalDpi(horizontal: Boolean): Float = Dpi.physical(resources.displayMetrics, horizontal)

    companion object {
        const val MM_PER_INCH = 25.4f
        const val STRIP_MM = 6.5f
        const val STRIP_BUTTON_MM = 13f
        const val STRIP_STATUS_MM = 16f
        const val GAP_MM = 0.9f
        const val SIDE_MM = 0.5f
        const val RADIUS_MM = 1.0f
        const val SHADOW_MM = 0.25f
        const val MAX_UNIT_MM = 11.5f
        const val GHOST_UNITS = 1f
        const val PANEL_MIN_MM = 12f
        const val CLIP_SLOTS_PER_ROW = 2
        const val CLIP_TEXT_MM = 2.3f
        const val CLIP_DRAG_MM = 2.0f
        const val CLIP_BAR_MM = 0.6f
        const val CLIP_LONG_PRESS_MS = 400L
        const val HOLD_MS = 400L
        const val SPLIT_SLOT_SUFFIX = "_ansi"

        fun offsetSlot(kind: LayoutKind, layer: Layer, widthPx: Float, pxPerMmX: Float): String =
            slotName(kind, layer, (widthPx / pxPerMmX).toInt())

        fun slotName(kind: LayoutKind, layer: Layer, widthMm: Int): String {
            val suffix = when {
                layer == Layer.GENERAL -> GENERAL_SLOT_SUFFIX
                kind == LayoutKind.SPLIT -> SPLIT_SLOT_SUFFIX
                else -> ""
            }
            return "${kind.name.lowercase()}${suffix}_${widthMm}mm"
        }
        const val GENERAL_SLOT_SUFFIX = "_general"
        const val PIN_MARK = "📌 "
        val EDIT_COMMANDS = setOf(Command.SELECT_ALL, Command.COPY, Command.CUT)
        const val PREVIEW_CHARS = 200
        const val SWIPE_MM = 4.0f
        const val CURSOR_START_MM = 3.0f
        const val CURSOR_STEP_MM = 2.5f
        const val CURSOR_ROW_STEP_MM = 4.0f
        const val ANCHOR_SHARE = 0.25f
        const val COMPACT_MAX_MM = 110f
        const val MAX_HEIGHT_SHARE = 0.6f
        const val LARGE_SCREEN_DP = 600
        const val DETENT_MIN_INTERVAL_MS = 40L
        const val MAX_ECHOES = 8
        const val ADAPT_MAX_GAP_MS = 1000L
        const val SAMPLE_DELAY = 8
    }
}
