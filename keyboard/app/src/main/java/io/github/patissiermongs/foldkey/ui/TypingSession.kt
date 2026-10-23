package io.github.patissiermongs.foldkey.ui

import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.hangul.Dubeolsik
import io.github.patissiermongs.foldkey.hangul.HangulComposer
import io.github.patissiermongs.foldkey.hangul.Jamo
import io.github.patissiermongs.foldkey.input.OffsetModel
import io.github.patissiermongs.foldkey.input.ThumbZones
import io.github.patissiermongs.foldkey.input.ThumbZones.Side
import io.github.patissiermongs.foldkey.input.TypingCalibration
import io.github.patissiermongs.foldkey.input.TypingCalibration.Analysis
import io.github.patissiermongs.foldkey.input.TypingCalibration.Anchors
import io.github.patissiermongs.foldkey.input.TypingCalibration.Frame
import io.github.patissiermongs.foldkey.input.TypingCalibration.Placement
import io.github.patissiermongs.foldkey.input.TypingCalibration.Sample
import io.github.patissiermongs.foldkey.input.TypingPrompts
import io.github.patissiermongs.foldkey.layout.Key
import io.github.patissiermongs.foldkey.layout.KeyboardGeometry
import kotlin.math.abs
import kotlin.math.min

class TypingSession(private val plan: Plan, private val build: (Layer, Placement) -> KeyboardGeometry) {
    data class Plan(
        val layers: List<Layer>,
        val start: Map<Layer, Float>,
        val startRowMm: Float,
        val anchors: Map<Layer, Anchors>,
        val widthPx: Float,
        val pxPerMmX: Float,
        val pxPerMmY: Float,
        val maxHeightMm: Float,
    ) {
        val frame: Frame get() = Frame(widthPx / pxPerMmX, maxHeightMm, KeyboardView.SIDE_MM, KeyboardView.GHOST_UNITS)
    }

    class Round(val placement: Placement, val unitMm: Float) {
        val samples = ArrayList<Sample>()
        var taps = 0
        var hits = 0
        var analysis: Analysis? = null

        val hitRate: Float get() = if (taps == 0) 0f else hits.toFloat() / taps
    }

    class LayerResult(val layer: Layer, val rounds: List<Round>, val unitMm: Float?, val rowMm: Float?, val analysis: Analysis?)

    data class Calibration(val placements: Map<Layer, Placement>, val offsets: Map<Layer, OffsetModel>)

    enum class Outcome { IGNORED, HIT, MISS, ROUND, LAYER, DONE }

    val results = ArrayList<LayerResult>()
    private var layerIndex = 0
    private var rounds = ArrayList<Round>()
    private var correction: OffsetModel? = null
    private var lines: List<String> = emptyList()
    private var lineIndex = 0
    private var lineKeys: List<Char> = emptyList()
    private var done = 0
    var charIndex = 0
        private set
    lateinit var geometry: KeyboardGeometry
        private set

    init {
        if (plan.layers.isNotEmpty()) startLayer()
    }

    val finished: Boolean get() = layerIndex >= plan.layers.size
    val layer: Layer get() = plan.layers[min(layerIndex, plan.layers.lastIndex)]
    val round: Round get() = rounds.last()
    val roundNumber: Int get() = rounds.size
    val progress: Int get() = done
    val line: String get() = lines.getOrElse(lineIndex) { "" }
    val expected: Char? get() = lineKeys.getOrNull(charIndex)

    val typed: String
        get() {
            val text = line
            if (text.none { Jamo.keyJamo(it) != null }) return text.take(charIndex)
            val composer = HangulComposer()
            val out = StringBuilder()
            for (k in lineKeys.take(charIndex)) out.append(composer.input(Dubeolsik.jamo(k, false) ?: k))
            return out.append(composer.composing).toString()
        }

    fun place(layer: Layer, unitMm: Float, rowMm: Float): Placement =
        TypingCalibration.place(plan.anchors.getValue(layer), plan.frame, layer, unitMm, rowMm)

    fun tap(xPx: Float, yPx: Float): Outcome {
        if (finished) return Outcome.IGNORED
        val g = geometry
        val target = expected ?: return Outcome.IGNORED
        val raw = g.keyAt(xPx, yPx) ?: return Outcome.IGNORED
        val hitKey = resolve(xPx, yPx) ?: raw
        val hit = matches(hitKey, target)
        if (target != ' ') {
            val instance = g.keys.filter { matches(it, target) }.minByOrNull {
                val dx = it.face.centerX - xPx
                val dy = it.face.centerY - yPx
                dx * dx + dy * dy
            }
            if (instance != null) {
                round.samples.add(
                    Sample(
                        side(instance),
                        instance.zone,
                        (xPx - instance.face.centerX) / plan.pxPerMmX,
                        (yPx - instance.face.centerY) / plan.pxPerMmY,
                    )
                )
            }
            round.taps++
            if (hit) round.hits++
        }
        if (!hit) return Outcome.MISS
        if (target != ' ') done++
        charIndex++
        if (charIndex >= lineKeys.size) {
            if (done >= ROUND_KEYS) return endRound()
            lineIndex = (lineIndex + 1) % lines.size
            loadLine()
        }
        return Outcome.HIT
    }

    fun skipLayer(): Outcome {
        if (finished) return Outcome.DONE
        results.add(LayerResult(layer, rounds.toList(), null, null, null))
        return nextLayer()
    }

    fun calibration(): Calibration {
        val chosen = results.filter { it.unitMm != null && it.rowMm != null }
        val row = chosen.maxOfOrNull { it.rowMm!! } ?: plan.startRowMm
        val placements = chosen.associate { it.layer to place(it.layer, it.unitMm!!, row) }
        val offsets = HashMap<Layer, OffsetModel>()
        for (r in chosen) {
            val analysis = r.analysis ?: continue
            offsets[r.layer] = seed(analysis, build(r.layer, placements.getValue(r.layer)))
        }
        return Calibration(placements, offsets)
    }

    private fun startLayer() {
        rounds = ArrayList()
        correction = null
        startRound(plan.start.getValue(layer), plan.startRowMm)
    }

    private fun startRound(unitMm: Float, rowMm: Float) {
        val placement = place(layer, unitMm, rowMm)
        geometry = build(layer, placement)
        rounds.add(Round(placement, geometry.unitPx / plan.pxPerMmX))
        lines = TypingPrompts.round(layer, rounds.size - 1, ROUND_KEYS)
        lineIndex = 0
        done = 0
        loadLine()
    }

    private fun loadLine() {
        lineKeys = TypingPrompts.keys(line) ?: emptyList()
        charIndex = 0
    }

    private fun endRound(): Outcome {
        val r = round
        r.analysis = TypingCalibration.analyze(r.samples)
        val a = r.analysis
        val nextUnit = if (a == null) r.unitMm else TypingCalibration.grow(r.unitMm, a.pitchMm, ThumbZones.UNIT_MIN_MM, ThumbZones.UNIT_MAX_MM)
        val nextRow = if (a == null) r.placement.rowMm else TypingCalibration.grow(r.placement.rowMm, a.rowMm, ThumbZones.ROW_MIN_MM, ThumbZones.ROW_MAX_MM)
        val grows = nextUnit > r.unitMm * GROW_SHARE || nextRow > r.placement.rowMm * GROW_SHARE
        if (a != null && grows && rounds.size < MAX_ROUNDS) {
            correction = seed(a, geometry)
            startRound(nextUnit, nextRow)
            return Outcome.ROUND
        }
        val analysis = a ?: rounds.dropLast(1).lastOrNull { it.analysis != null }?.analysis
        results.add(LayerResult(layer, rounds.toList(), nextUnit, nextRow, analysis))
        return nextLayer()
    }

    private fun nextLayer(): Outcome {
        layerIndex++
        if (finished) return Outcome.DONE
        startLayer()
        return Outcome.LAYER
    }

    private fun seed(analysis: Analysis, g: KeyboardGeometry): OffsetModel {
        val model = OffsetModel(g.zoneCount)
        val zoneSides = g.keys.filter { !it.ghost }.groupBy { it.zone }.mapValues { (_, keys) -> side(keys.first()) }
        for ((zone, side) in zoneSides) {
            val spread = analysis.spread[side] ?: continue
            val (ox, oy) = analysis.offset(zone, side)
            model.seed(zone, ox, oy, min(model.warmup, spread.count))
        }
        return model
    }

    private fun resolve(x: Float, y: Float): Key? {
        val g = geometry
        g.keys.firstOrNull { k ->
            !k.ghost &&
                abs(x - k.face.centerX) <= k.face.width * KeyboardView.ANCHOR_SHARE &&
                abs(y - k.face.centerY) <= k.face.height * KeyboardView.ANCHOR_SHARE
        }?.let { return it }
        val raw = g.keyAt(x, y)
        val model = correction ?: return raw
        val (cx, cy) = model.correctionMm(g.zoneAt(x, y), g.unitPx / plan.pxPerMmX, round.placement.rowMm)
        val corrected = g.keyAt(x - cx * plan.pxPerMmX, y - cy * plan.pxPerMmY)
        if (corrected != null && !(corrected.ghost && raw != null && !raw.ghost)) return corrected
        return raw
    }

    private fun matches(key: Key, c: Char): Boolean {
        val action = key.def.action
        return if (c == ' ') action == KeyAction.Space else (action as? KeyAction.Char)?.base == c
    }

    private fun side(key: Key): Side = if (key.face.centerX < plan.widthPx / 2f) Side.LEFT else Side.RIGHT

    companion object {
        const val ROUND_KEYS = 60
        const val MAX_ROUNDS = 3
        const val GROW_SHARE = 1.05f
    }
}
