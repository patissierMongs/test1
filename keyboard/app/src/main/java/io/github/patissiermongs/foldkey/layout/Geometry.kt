package io.github.patissiermongs.foldkey.layout

import io.github.patissiermongs.foldkey.engine.KeyAction

data class SplitRow(val left: RowDef, val right: RowDef)

data class GeometrySpec(
    val widthPx: Float,
    val pxPerMmX: Float,
    val pxPerMmY: Float,
    val topPx: Float,
    val rowHeightMm: Float,
    val gapMm: Float,
    val sidePaddingMm: Float,
    val sideInsetPx: Float = 0f,
    val splitMarginPx: Float = 0f,
    val bottomPaddingPx: Float,
    val maxUnitMm: Float,
    val splitUnitMm: Float,
    val ghostUnits: Float,
    val zonesPerRow: Int = 4,
    val liftPx: Float = 0f,
    val panelMinPx: Float = 0f,
    val panelGhostUnits: Float = 0.5f,
)

class KeyboardGeometry(
    val keys: List<Key>,
    val heightPx: Float,
    val unitPx: Float,
    val zoneCount: Int,
    val panel: List<Box> = emptyList(),
    val fnKeys: List<Key> = keys,
) {
    fun layer(fn: Boolean): List<Key> = if (fn) fnKeys else keys

    fun keyAt(x: Float, y: Float, fn: Boolean = false): Key? = layer(fn).firstOrNull { it.touch.contains(x, y) }

    fun zoneAt(x: Float, y: Float, fn: Boolean = false): Int = (keyAt(x, y, fn) ?: nearest(x, y, fn))?.zone ?: 0

    fun nearest(x: Float, y: Float, fn: Boolean = false): Key? = layer(fn).filter { !it.ghost }.minByOrNull {
        val dx = it.face.centerX - x
        val dy = it.face.centerY - y
        dx * dx + dy * dy
    }

    companion object {
        private const val EDGE_SLACK_PX = 0.5f

        fun full(rows: List<RowDef>, spec: GeometrySpec, pad: List<List<KeyDef>> = emptyList()): KeyboardGeometry {
            val units = rows.maxOf { it.span }
            val side = spec.sidePaddingMm * spec.pxPerMmX + spec.sideInsetPx
            val available = spec.widthPx - 2f * side
            val unit = minOf(available / units, spec.maxUnitMm * spec.pxPerMmX)
            val rowH = spec.rowHeightMm * spec.pxPerMmY
            val gapX = spec.gapMm * spec.pxPerMmX / 2f
            val gapY = spec.gapMm * spec.pxPerMmY / 2f
            val keys = ArrayList<Key>()
            rows.forEachIndexed { r, row ->
                val top = spec.topPx + r * rowH
                val bottom = top + rowH
                val rowWidth = row.span * unit
                var x = (spec.widthPx - rowWidth) / 2f + row.indent * unit
                val touchTop = if (r == 0) spec.topPx else top
                val touchBottom = bottom
                row.keys.forEachIndexed { i, def ->
                    val w = def.width * unit
                    val left = x
                    val right = x + w
                    val touchLeft = if (i == 0) 0f else left
                    val touchRight = if (i == row.keys.lastIndex) spec.widthPx else right
                    val zone = r * spec.zonesPerRow + zoneColumn((left + right) / 2f, spec.widthPx, spec.zonesPerRow)
                    keys.add(
                        Key(
                            def = def,
                            face = Box(left + gapX, top + gapY, right - gapX, bottom - gapY),
                            touch = Box(touchLeft, touchTop, touchRight, touchBottom),
                            row = r,
                            zone = zone,
                        )
                    )
                    x = right
                }
            }
            val height = spec.topPx + rows.size * rowH + spec.bottomPaddingPx
            return KeyboardGeometry(keys, height, unit, rows.size * spec.zonesPerRow, fnKeys = padLayer(keys, pad, spec, unit))
        }

        fun split(rows: List<SplitRow>, spec: GeometrySpec, pad: List<List<KeyDef>> = emptyList()): KeyboardGeometry {
            val units = rows.maxOf { it.left.span + it.right.span }
            val room = (spec.widthPx - (units + 2f * spec.ghostUnits) * spec.splitUnitMm * spec.pxPerMmX) / 2f
            val side = maxOf(spec.sidePaddingMm * spec.pxPerMmX + spec.sideInsetPx, minOf(spec.splitMarginPx, room))
            val available = spec.widthPx - 2f * side
            val unit = minOf(spec.splitUnitMm * spec.pxPerMmX, available / (units + 2f * spec.ghostUnits))
            val rowH = spec.rowHeightMm * spec.pxPerMmY
            val gapX = spec.gapMm * spec.pxPerMmX / 2f
            val gapY = spec.gapMm * spec.pxPerMmY / 2f
            val gap = available - units * unit
            var ghost = spec.ghostUnits * unit
            val withPanel = spec.panelMinPx > 0f && gap - 2f * spec.panelGhostUnits * unit >= spec.panelMinPx
            if (withPanel && gap - 2f * ghost < spec.panelMinPx) ghost = spec.panelGhostUnits * unit
            ghost = minOf(ghost, gap / 2f)
            val keys = ArrayList<Key>()
            val panel = ArrayList<Box>()
            rows.forEachIndexed { r, row ->
                val top = spec.topPx + r * rowH
                val bottom = top + rowH
                val touchTop = if (r == 0) spec.topPx else top
                val touchBottom = if (r == rows.lastIndex) bottom + spec.liftPx else bottom
                fun place(def: KeyDef, left: Float, right: Float, touchLeft: Float, touchRight: Float) {
                    keys.add(
                        Key(
                            def,
                            Box(left + gapX, top + gapY, right - gapX, bottom - gapY),
                            Box(touchLeft, touchTop, touchRight, touchBottom),
                            r,
                            r * spec.zonesPerRow + zoneColumn((left + right) / 2f, spec.widthPx, spec.zonesPerRow),
                        )
                    )
                }
                var x = side + row.left.indent * unit
                row.left.keys.forEachIndexed { i, def ->
                    val right = x + def.width * unit
                    place(def, x, right, if (i == 0) 0f else x, right)
                    x = right
                }
                val innerLeft = x
                val innerRight = innerLeft + gap
                val leftLast = row.left.keys.last()
                val rightFirst = row.right.keys.first()
                val ghosts = ghost > 0f && rightFirst.style == KeyStyle.NORMAL && leftLast.style == KeyStyle.NORMAL
                if (ghosts) {
                    val gr = innerLeft + ghost
                    keys.add(Key(rightFirst, Box(innerLeft, top, gr, bottom), Box(innerLeft, touchTop, gr, touchBottom), r, r * spec.zonesPerRow + 1, ghost = true))
                    val hl = innerRight - ghost
                    keys.add(Key(leftLast, Box(hl, top, innerRight, bottom), Box(hl, touchTop, innerRight, touchBottom), r, r * spec.zonesPerRow + 2, ghost = true))
                }
                if (withPanel) {
                    val inset = if (ghosts) ghost else 0f
                    panel.add(Box(innerLeft + inset, top, innerRight - inset, bottom))
                }
                x = innerRight + row.right.indent * unit
                row.right.keys.forEachIndexed { i, def ->
                    val right = x + def.width * unit
                    place(def, x, right, x, if (i == row.right.keys.lastIndex) spec.widthPx else right)
                    x = right
                }
            }
            val height = spec.topPx + rows.size * rowH + spec.liftPx + spec.bottomPaddingPx
            return KeyboardGeometry(keys, height, unit, rows.size * spec.zonesPerRow, panel, padLayer(keys, pad, spec, unit))
        }

        private fun padLayer(keys: List<Key>, pad: List<List<KeyDef>>, spec: GeometrySpec, unit: Float): List<Key> {
            if (pad.isEmpty()) return keys
            val gapX = spec.gapMm * spec.pxPerMmX / 2f
            val gapY = spec.gapMm * spec.pxPerMmY / 2f
            val anchor = keys.firstOrNull { !it.ghost && (it.def.action as? KeyAction.Char)?.base == Layouts.PAD_ANCHOR }
                ?: return keys
            val hideFrom = anchor.face.left - gapX
            val end = keys.filter { !it.ghost }.maxOf { it.face.right + gapX }
            val covered = keys.filterTo(HashSet()) { it.row < pad.size && (it.ghost || it.face.right + gapX > hideFrom + EDGE_SLACK_PX) }
            val padKeys = ArrayList<Key>()
            pad.forEachIndexed { r, row ->
                val ref = keys.first { it.row == r && !it.ghost }
                val top = ref.face.top - gapY
                val bottom = ref.face.bottom + gapY
                val w = minOf(unit, (end - hideFrom) / row.size)
                val start = end - row.size * w
                row.forEachIndexed { c, def ->
                    val left = start + c * w
                    val right = left + w
                    padKeys.add(
                        Key(
                            def,
                            Box(left + gapX, top + gapY, right - gapX, bottom - gapY),
                            Box(left, ref.touch.top, if (c == row.lastIndex) spec.widthPx else right, ref.touch.bottom),
                            r,
                            r * spec.zonesPerRow + zoneColumn((left + right) / 2f, spec.widthPx, spec.zonesPerRow),
                            pad = true,
                        )
                    )
                }
            }
            return keys.filter { it !in covered } + padKeys
        }

        private fun zoneColumn(cx: Float, width: Float, zones: Int): Int =
            (cx / width * zones).toInt().coerceIn(0, zones - 1)
    }
}
