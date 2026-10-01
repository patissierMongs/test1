package io.github.patissiermongs.foldkey.layout

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
    val panel: Box? = null,
) {
    fun keyAt(x: Float, y: Float): Key? = keys.firstOrNull { it.touch.contains(x, y) }

    fun zoneAt(x: Float, y: Float): Int {
        val k = keys.firstOrNull { it.touch.contains(x, y) } ?: nearest(x, y) ?: return 0
        return k.zone
    }

    fun nearest(x: Float, y: Float): Key? = keys.filter { !it.ghost }.minByOrNull {
        val dx = it.face.centerX - x
        val dy = it.face.centerY - y
        dx * dx + dy * dy
    }

    companion object {
        fun full(rows: List<RowDef>, spec: GeometrySpec): KeyboardGeometry {
            val units = rows.maxOf { it.units }
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
                val rowWidth = row.units * unit
                var x = (spec.widthPx - rowWidth) / 2f
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
            return KeyboardGeometry(keys, height, unit, rows.size * spec.zonesPerRow)
        }

        fun split(rows: List<SplitRow>, spec: GeometrySpec): KeyboardGeometry {
            val leftUnits = rows.maxOf { it.left.units }
            val rightUnits = rows.maxOf { it.right.units }
            val side = spec.sidePaddingMm * spec.pxPerMmX + spec.sideInsetPx
            val available = spec.widthPx - 2f * side
            val wanted = spec.splitUnitMm * spec.pxPerMmX
            val fit = available / (leftUnits + rightUnits + 2f * spec.ghostUnits)
            val unit = minOf(wanted, fit)
            val rowH = spec.rowHeightMm * spec.pxPerMmY
            val gapX = spec.gapMm * spec.pxPerMmX / 2f
            val gapY = spec.gapMm * spec.pxPerMmY / 2f
            val keys = ArrayList<Key>()
            val leftEdge = side
            val rightEdge = spec.widthPx - side
            val leftInner = leftEdge + leftUnits * unit
            val rightInner = rightEdge - rightUnits * unit
            val gap = rightInner - leftInner
            var ghostUnits = spec.ghostUnits
            var panel: Box? = null
            if (spec.panelMinPx > 0f) {
                if (gap - 2f * spec.panelGhostUnits * unit >= spec.panelMinPx) {
                    if (gap - 2f * ghostUnits * unit < spec.panelMinPx) ghostUnits = spec.panelGhostUnits
                    val g = ghostUnits * unit
                    panel = Box(leftInner + g, spec.topPx, rightInner - g, spec.topPx + rows.size * rowH)
                }
            }
            rows.forEachIndexed { r, row ->
                val top = spec.topPx + r * rowH
                val bottom = top + rowH
                val touchTop = if (r == 0) spec.topPx else top
                val touchBottom = if (r == rows.lastIndex) bottom + spec.liftPx else bottom
                var x = leftEdge + (leftUnits - row.left.units) * unit
                row.left.keys.forEachIndexed { i, def ->
                    val left = x
                    val right = x + def.width * unit
                    val touchLeft = if (i == 0) 0f else left
                    keys.add(
                        Key(
                            def,
                            Box(left + gapX, top + gapY, right - gapX, bottom - gapY),
                            Box(touchLeft, touchTop, right, touchBottom),
                            r,
                            r * spec.zonesPerRow + zoneColumn((left + right) / 2f, spec.widthPx, spec.zonesPerRow),
                        )
                    )
                    x = right
                }
                val leftLast = row.left.keys.last()
                val rightFirst = row.right.keys.first()
                if (ghostUnits > 0f && rightFirst.style == KeyStyle.NORMAL && leftLast.style == KeyStyle.NORMAL) {
                    val gl = leftInner
                    val gr = minOf(leftInner + ghostUnits * unit, (leftInner + rightInner) / 2f)
                    keys.add(Key(rightFirst, Box(gl, top, gr, bottom), Box(gl, touchTop, gr, touchBottom), r, r * spec.zonesPerRow + 1, ghost = true))
                    val hr = rightInner
                    val hl = maxOf(rightInner - ghostUnits * unit, (leftInner + rightInner) / 2f)
                    keys.add(Key(leftLast, Box(hl, top, hr, bottom), Box(hl, touchTop, hr, touchBottom), r, r * spec.zonesPerRow + 2, ghost = true))
                }
                x = rightInner
                row.right.keys.forEachIndexed { i, def ->
                    val left = x
                    val right = x + def.width * unit
                    val touchRight = if (i == row.right.keys.lastIndex) spec.widthPx else right
                    keys.add(
                        Key(
                            def,
                            Box(left + gapX, top + gapY, right - gapX, bottom - gapY),
                            Box(left, touchTop, touchRight, touchBottom),
                            r,
                            r * spec.zonesPerRow + zoneColumn((left + right) / 2f, spec.widthPx, spec.zonesPerRow),
                        )
                    )
                    x = right
                }
            }
            val height = spec.topPx + rows.size * rowH + spec.liftPx + spec.bottomPaddingPx
            return KeyboardGeometry(keys, height, unit, rows.size * spec.zonesPerRow, panel)
        }

        private fun zoneColumn(cx: Float, width: Float, zones: Int): Int =
            (cx / width * zones).toInt().coerceIn(0, zones - 1)
    }
}
