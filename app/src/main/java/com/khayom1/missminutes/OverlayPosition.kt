package com.khayom1.missminutes

/** Pixel coordinates for the floating character overlay. */
internal data class OverlayPosition(val x: Int, val y: Int)

/**
 * Keeps an overlay fully inside the available display bounds.
 * Negative and oversized dimensions are handled defensively.
 */
internal fun clampOverlayPosition(
    x: Int,
    y: Int,
    screenWidth: Int,
    screenHeight: Int,
    overlayWidth: Int,
    overlayHeight: Int
): OverlayPosition {
    val maxX = (screenWidth.coerceAtLeast(0) - overlayWidth.coerceAtLeast(0)).coerceAtLeast(0)
    val maxY = (screenHeight.coerceAtLeast(0) - overlayHeight.coerceAtLeast(0)).coerceAtLeast(0)
    return OverlayPosition(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
}
