package io.github.patissiermongs.foldkey.layout

import io.github.patissiermongs.foldkey.engine.KeyAction
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometrySweepTest {
    private fun spec(widthPx: Float, ppi: Float, unitMm: Float, lift: Float, panelMm: Float, inset: Float, ghost: Float, margin: Pair<Float, Float> = 0f to 0f) = GeometrySpec(
        widthPx = widthPx,
        pxPerMmX = ppi / 25.4f,
        pxPerMmY = ppi / 25.4f,
        topPx = 6.5f * ppi / 25.4f,
        rowHeightMm = 9.5f,
        gapMm = 0.9f,
        sidePaddingMm = 0.5f,
        sideInsetPx = inset,
        splitMarginLeftPx = margin.first * ppi / 25.4f,
        splitMarginRightPx = margin.second * ppi / 25.4f,
        bottomPaddingPx = 0f,
        maxUnitMm = 11.5f,
        splitUnitMm = unitMm,
        ghostUnits = ghost,
        liftPx = lift * ppi / 25.4f,
        panelMinPx = panelMm * ppi / 25.4f,
    )

    private fun name(k: Key): String = (if (k.ghost) "ghost:" else if (k.pad) "pad:" else "") +
        ((k.def.action as? KeyAction.Char)?.base?.toString() ?: k.def.label ?: k.def.action.toString())

    private fun check(tag: String, g: KeyboardGeometry, problems: MutableList<String>) {
        for (fn in listOf(false, true)) {
            val keys = g.layer(fn)
            for (k in keys.filter { !it.ghost }) {
                val hit = g.keyAt(k.face.centerX, k.face.centerY, fn)
                if (hit !== k) problems.add("$tag fn=$fn ${name(k)} centre -> ${hit?.let { name(it) }}")
            }
            val maxX = keys.maxOf { it.touch.right }
            var y = keys.minOf { it.touch.top } + 0.5f
            while (y < keys.maxOf { it.touch.bottom }) {
                var x = 0.5f
                while (x < maxX) {
                    val hits = keys.filter { it.touch.contains(x, y) }
                    if (hits.size > 1) {
                        problems.add("$tag fn=$fn overlap at ($x,$y): ${hits.map { name(it) }}")
                        break
                    }
                    x += 3f
                }
                y += 5f
            }
        }
    }

    @Test
    fun holesOutsideTheDesignedGaps() {
        val problems = ArrayList<String>()
        for ((layout, rows) in listOf("code" to Layouts.split, "general" to Layouts.generalSplit)) for (ppi in listOf(368f, 422f)) {
            val pxPerMm = ppi / 25.4f
            for (widthMm in (110..260 step 5)) for (setting in listOf(4f, 7f, 8.5f, 11f)) for (lift in listOf(0f, 7f)) for (panel in listOf(0f, 12f)) for (margin in MARGINS) {
                val w = widthMm * pxPerMm
                val unit = if (rows === Layouts.split) setting else Layouts.generalSplitUnitMm(setting, maxOf(0f, margin.first - 0.5f), maxOf(0f, margin.second - 0.5f))
                val g = KeyboardGeometry.split(rows, spec(w, ppi, unit, lift, panel, 0f, 1f, margin), if (rows === Layouts.split) Layouts.pad else emptyList())
                val keys = g.keys
                val rowsBottom = keys.maxOf { it.touch.bottom }
                var y = keys.minOf { it.touch.top } + 0.5f
                while (y < rowsBottom) {
                    val row = keys.firstOrNull { y >= it.touch.top && y < it.touch.bottom }?.row
                    if (row == null) {
                        problems.add("$layout split w=$widthMm unit=$unit lift=$lift panel=$panel margin=$margin: whole-row hole at y=$y")
                        y += 4f
                        continue
                    }
                    val real = keys.filter { it.row == row && !it.ghost }
                    val leftEnd = real.filter { k -> rows[row].left.keys.any { it === k.def } }.maxOf { it.touch.right }
                    val rightStart = real.filter { k -> rows[row].right.keys.any { it === k.def } }.minOf { it.touch.left }
                    val ghosts = keys.filter { it.row == row && it.ghost }
                    val holeFrom = ghosts.filter { it.touch.left < leftEnd + 1f }.maxOfOrNull { it.touch.right } ?: leftEnd
                    val holeTo = ghosts.filter { it.touch.right > rightStart - 1f }.minOfOrNull { it.touch.left } ?: rightStart
                    val rowEnd = real.maxOf { it.touch.right }
                    var x = 0.5f
                    while (x < w - 0.5f) {
                        val inDesignedGap = x >= holeFrom - 0.5f && x < holeTo + 0.5f
                        val inKnownStrip = row == 0 && x >= rowEnd - 0.5f
                        if (!inDesignedGap && !inKnownStrip && keys.none { it.touch.contains(x, y) }) {
                            problems.add("$layout split w=$widthMm unit=$unit lift=$lift panel=$panel margin=$margin row=$row hole at x=$x")
                            break
                        }
                        x += 2f
                    }
                    y += 4f
                }
            }
        }
        assertTrue("${problems.size} problems, e.g. ${problems.take(5)}", problems.isEmpty())
    }

    @Test
    fun sweepAllLayouts() {
        val problems = ArrayList<String>()
        for (ppi in listOf(368f, 422f, 300f)) {
            val pxPerMm = ppi / 25.4f
            for (widthMm in (110..260 step 5)) {
                for (unit in listOf(4f, 7f, 8.5f, 11f)) for (lift in listOf(0f, 7f)) for (panel in listOf(0f, 12f)) for (inset in listOf(0f, 60f)) {
                    val w = widthMm * pxPerMm
                    for (margin in MARGINS) {
                        val g = KeyboardGeometry.split(Layouts.split, spec(w, ppi, unit, lift, panel, inset, 1f, margin), Layouts.pad)
                        check("split ppi=$ppi w=${widthMm}mm unit=$unit lift=$lift panel=$panel inset=$inset margin=$margin", g, problems)
                        val gs = KeyboardGeometry.split(
                            Layouts.generalSplit,
                            spec(w, ppi, Layouts.generalSplitUnitMm(unit, maxOf(0f, margin.first - 0.5f), maxOf(0f, margin.second - 0.5f)), lift, panel, inset, 1f, margin),
                        )
                        check("general split ppi=$ppi w=${widthMm}mm unit=$unit lift=$lift panel=$panel inset=$inset margin=$margin", gs, problems)
                    }
                }
                val f = KeyboardGeometry.full(Layouts.full, spec(widthMm * pxPerMm, ppi, 8.5f, 0f, 0f, 0f, 0f), Layouts.pad)
                check("full ppi=$ppi w=${widthMm}mm", f, problems)
                val gf = KeyboardGeometry.full(Layouts.general, spec(widthMm * pxPerMm, ppi, 8.5f, 0f, 0f, 0f, 0f))
                check("general full ppi=$ppi w=${widthMm}mm", gf, problems)
            }
            for (widthMm in (50..110 step 5)) {
                val c = KeyboardGeometry.full(Layouts.compact, spec(widthMm * pxPerMm, ppi, 8.5f, 0f, 0f, 0f, 0f), Layouts.pad)
                check("compact ppi=$ppi w=${widthMm}mm", c, problems)
                val gc = KeyboardGeometry.full(Layouts.general, spec(widthMm * pxPerMm, ppi, 8.5f, 0f, 0f, 0f, 0f))
                check("general compact ppi=$ppi w=${widthMm}mm", gc, problems)
            }
        }
        assertTrue("${problems.size} problems, e.g. ${problems.take(5)}", problems.isEmpty())
    }

    companion object {
        private val MARGINS = listOf(0f to 0f, 14f to 14f, 25f to 25f, 13.7f to 17.4f)
    }
}
