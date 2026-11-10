package io.github.patissiermongs.foldkey.ui

import io.github.patissiermongs.foldkey.engine.KeyAction
import io.github.patissiermongs.foldkey.engine.Layer
import io.github.patissiermongs.foldkey.ime.Prefs
import io.github.patissiermongs.foldkey.ime.ScreenProfile
import io.github.patissiermongs.foldkey.layout.GeometrySpec
import io.github.patissiermongs.foldkey.layout.KeyDef
import io.github.patissiermongs.foldkey.layout.KeyboardGeometry
import io.github.patissiermongs.foldkey.layout.LayoutKind
import io.github.patissiermongs.foldkey.layout.Layouts
import kotlin.math.max

object LayoutBuilder {
    data class Sides(val leftMm: Float, val rightMm: Float)

    fun sides(prefs: Prefs, layer: Layer): Sides =
        if (layer == Layer.GENERAL) Sides(prefs.generalSideLeftMm, prefs.generalSideRightMm)
        else Sides(prefs.codeSideLeftMm, prefs.codeSideRightMm)

    data class Units(val leftMm: Float, val rightMm: Float, val halves: Boolean)

    fun codeSplitUnitMm(prefs: Prefs, profile: ScreenProfile): Float = prefs.keyWidthMm(profile, true) ?: KeyboardView.MAX_UNIT_MM

    fun codeSplitUnits(prefs: Prefs, profile: ScreenProfile): Units {
        if (!prefs.hasHalfWidths(profile)) return codeSplitUnitMm(prefs, profile).let { Units(it, it, false) }
        return Units(
            prefs.keyWidthLeftMm(profile, true) ?: KeyboardView.MAX_UNIT_MM,
            prefs.keyWidthRightMm(profile, true) ?: KeyboardView.MAX_UNIT_MM,
            true,
        )
    }

    fun splitUnitMm(prefs: Prefs, layer: Layer, profile: ScreenProfile): Float = splitUnits(prefs, layer, profile).leftMm

    fun splitUnits(prefs: Prefs, layer: Layer, profile: ScreenProfile): Units {
        val code = codeSplitUnits(prefs, profile)
        if (layer != Layer.GENERAL) return code
        if (prefs.hasGeneralHalfWidths) {
            val common = derivedGeneralUnits(prefs, code)
            return Units(prefs.generalUnitLeftMm ?: common.leftMm, prefs.generalUnitRightMm ?: common.rightMm, true)
        }
        prefs.generalUnitMm?.let { return Units(it, it, false) }
        return derivedGeneralUnits(prefs, code)
    }

    private fun derivedGeneralUnits(prefs: Prefs, code: Units): Units {
        val codeSides = sides(prefs, Layer.CODE)
        val generalSides = sides(prefs, Layer.GENERAL)
        fun edge(mm: Float) = max(mm, KeyboardView.SIDE_MM)
        val insetLeft = edge(generalSides.leftMm) - edge(codeSides.leftMm)
        val insetRight = edge(generalSides.rightMm) - edge(codeSides.rightMm)
        if (!code.halves) {
            val unit = Layouts.generalSplitUnitMm(code.leftMm, insetLeft, insetRight)
            return Units(unit, unit, false)
        }
        val (left, right) = Layouts.generalSplitUnitsMm(code.leftMm, code.rightMm, insetLeft, insetRight)
        return Units(left, right, true)
    }

    fun maxWidthPx(prefs: Prefs, kind: LayoutKind, profile: ScreenProfile, pxPerMmX: Float): Float {
        if (kind == LayoutKind.SPLIT) return Float.MAX_VALUE
        val keyMm = prefs.keyWidthMm(profile, false) ?: return Float.MAX_VALUE
        return Layouts.codeUnits(kind) * keyMm * pxPerMmX
    }

    fun spec(
        prefs: Prefs,
        kind: LayoutKind,
        layer: Layer,
        profile: ScreenProfile,
        widthPx: Float,
        pxPerMmX: Float,
        pxPerMmY: Float,
        topPx: Float,
        rowHeightMm: Float,
        bottomPx: Float = 0f,
        sideInsetPx: Float = 0f,
        liftPx: Float = 0f,
        panelMinPx: Float = 0f,
    ): GeometrySpec {
        val split = kind == LayoutKind.SPLIT
        val sides = sides(prefs, layer)
        val units = splitUnits(prefs, layer, profile)
        return GeometrySpec(
            widthPx = widthPx,
            pxPerMmX = pxPerMmX,
            pxPerMmY = pxPerMmY,
            topPx = topPx,
            rowHeightMm = rowHeightMm,
            gapMm = KeyboardView.GAP_MM,
            sidePaddingMm = KeyboardView.SIDE_MM,
            sideInsetPx = sideInsetPx,
            splitMarginLeftPx = if (split) sides.leftMm * pxPerMmX else 0f,
            splitMarginRightPx = if (split) sides.rightMm * pxPerMmX else 0f,
            bottomPaddingPx = bottomPx,
            maxUnitMm = KeyboardView.MAX_UNIT_MM,
            maxWidthPx = maxWidthPx(prefs, kind, profile, pxPerMmX),
            splitUnitMm = units.leftMm,
            splitUnitRightMm = if (units.halves) units.rightMm else Float.NaN,
            ghostUnits = if (split) KeyboardView.GHOST_UNITS else 0f,
            liftPx = liftPx,
            panelMinPx = panelMinPx,
        )
    }

    fun previewLabel(def: KeyDef): String {
        val action = def.action
        if (action is KeyAction.Char) return action.base.toString()
        return def.label.orEmpty()
    }

    fun build(kind: LayoutKind, layer: Layer, spec: GeometrySpec): KeyboardGeometry {
        val general = layer == Layer.GENERAL
        return when {
            general && kind == LayoutKind.SPLIT -> KeyboardGeometry.split(Layouts.generalSplit, spec)
            general -> KeyboardGeometry.full(Layouts.general, spec)
            kind == LayoutKind.SPLIT -> KeyboardGeometry.split(Layouts.split, spec, Layouts.pad)
            kind == LayoutKind.FULL -> KeyboardGeometry.full(Layouts.full, spec, Layouts.pad)
            else -> KeyboardGeometry.full(Layouts.compact, spec, Layouts.pad)
        }
    }
}
