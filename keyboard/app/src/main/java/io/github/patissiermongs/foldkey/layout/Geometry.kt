package io.github.patissiermongs.foldkey.layout

import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Modifier

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
    val splitMarginLeftPx: Float = 0f,
    val splitMarginRightPx: Float = 0f,
    val bottomPaddingPx: Float,
    val maxUnitMm: Float,
    val maxWidthPx: Float = Float.MAX_VALUE,
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

        const val MIN_CENTER_UNITS = 0.5f

        const val MIN_CLIPPED_UNITS = 1f

        fun full(rows: List<RowDef>, spec: GeometrySpec, pad: List<PadCell> = emptyList()): KeyboardGeometry {
            val units = rows.maxOf { it.span }
            val side = spec.sidePaddingMm * spec.pxPerMmX + spec.sideInsetPx
            val available = minOf(spec.widthPx - 2f * side, spec.maxWidthPx)
            val unit = minOf(available / units, spec.maxUnitMm * spec.pxPerMmX)
            val start = (spec.widthPx - units * unit) / 2f
            val rowH = spec.rowHeightMm * spec.pxPerMmY
            val gapX = spec.gapMm * spec.pxPerMmX / 2f
            val gapY = spec.gapMm * spec.pxPerMmY / 2f
            val keys = ArrayList<Key>()
            rows.forEachIndexed { r, row ->
                val top = spec.topPx + r * rowH
                val bottom = top + rowH
                var x = start + row.indent * unit
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

        fun split(rows: List<SplitRow>, spec: GeometrySpec, pad: List<PadCell> = emptyList()): KeyboardGeometry {
            val units = rows.maxOf { it.left.span + it.right.span }
            val edge = spec.sidePaddingMm * spec.pxPerMmX + spec.sideInsetPx
            val side = maxOf(edge, spec.splitMarginLeftPx)
            val available = spec.widthPx - side - maxOf(edge, spec.splitMarginRightPx)
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
                val inward = (row.left.inward + row.right.inward) * unit
                val scale = if (inward > 0f) minOf(1f, maxOf(0f, gap - MIN_CENTER_UNITS * unit) / inward) else 1f
                val extLeft = row.left.inward * unit * scale
                val extRight = row.right.inward * unit * scale
                val stretchLeft = stretchIndex(row.left, row.left.keys.lastIndex)
                val stretchRight = stretchIndex(row.right, 0)
                var x = side + row.left.indent * unit
                row.left.keys.forEachIndexed { i, def ->
                    val trim = if (i == stretchLeft) row.left.inward * unit - extLeft else 0f
                    val right = x + def.width * unit - trim
                    place(def, x, right, if (i == 0) 0f else x, right)
                    x = right
                }
                val innerLeft = x - extLeft
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
                    panel.add(Box(innerLeft + maxOf(inset, extLeft), top, innerRight - maxOf(inset, extRight), bottom))
                }
                x = innerRight + row.right.indent * unit - extRight
                row.right.keys.forEachIndexed { i, def ->
                    val trim = if (i == stretchRight) row.right.inward * unit - extRight else 0f
                    val right = x + def.width * unit - trim
                    place(def, x, right, x, if (i == row.right.keys.lastIndex) spec.widthPx else right)
                    x = right
                }
            }
            val height = spec.topPx + rows.size * rowH + spec.liftPx + spec.bottomPaddingPx
            return KeyboardGeometry(keys, height, unit, rows.size * spec.zonesPerRow, panel, padLayer(keys, pad, spec, unit))
        }

        private fun stretchIndex(row: RowDef, fallback: Int): Int =
            row.keys.indexOfFirst { it.isSpace }.takeIf { it >= 0 } ?: fallback

        private fun padLayer(keys: List<Key>, pad: List<PadCell>, spec: GeometrySpec, unit: Float): List<Key> {
            if (pad.isEmpty()) return keys
            val gapX = spec.gapMm * spec.pxPerMmX / 2f
            val anchor = keys.firstOrNull { !it.ghost && (it.def.action as? KeyAction.Char)?.base == Layouts.PAD_ANCHOR }
                ?: return keys
            val rows = pad.maxOf { it.row + it.rowSpan }
            val columns = pad.maxOf { it.col + it.colSpan }
            val refs = (0 until rows).map { r -> keys.firstOrNull { it.row == r && !it.ghost } ?: return keys }
            val fn = keys.filter { it.row < rows && !it.ghost && it.def.action == KeyAction.Mod(Modifier.FN) }
            val start = maxOf(anchor.face.left - gapX, fn.maxOfOrNull { it.face.right + gapX } ?: 0f)
            val end = keys.filter { !it.ghost }.maxOf { it.face.right + gapX }
            val w = (end - start) / columns
            val kept = ArrayList<Key>()
            for (k in keys) {
                if (k.row >= rows) {
                    kept.add(k)
                    continue
                }
                if (k.ghost) continue
                if (k.face.right + gapX <= start + EDGE_SLACK_PX) {
                    kept.add(k)
                    continue
                }
                if (start - (k.face.left - gapX) >= MIN_CLIPPED_UNITS * unit - EDGE_SLACK_PX) {
                    val face = Box(k.face.left, k.face.top, start - gapX, k.face.bottom)
                    kept.add(Key(k.def, face, Box(k.touch.left, k.touch.top, start, k.touch.bottom), k.row, k.zone))
                }
            }
            val padKeys = pad.map { cell ->
                val left = start + cell.col * w
                val right = left + cell.colSpan * w
                val top = refs[cell.row]
                val bottom = refs[cell.row + cell.rowSpan - 1]
                Key(
                    cell.def,
                    Box(left + gapX, top.face.top, right - gapX, bottom.face.bottom),
                    Box(left, top.touch.top, if (cell.col + cell.colSpan == columns) spec.widthPx else right, bottom.touch.bottom),
                    cell.row,
                    cell.row * spec.zonesPerRow + zoneColumn((left + right) / 2f, spec.widthPx, spec.zonesPerRow),
                    pad = true,
                )
            }
            return kept + padKeys
        }

        private fun zoneColumn(cx: Float, width: Float, zones: Int): Int =
            (cx / width * zones).toInt().coerceIn(0, zones - 1)
    }
}
