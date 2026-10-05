package dev.pods.app.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * Rounded rectangle with Apple-style continuous corners: the curve starts
 * further along each edge and eases in, instead of a plain circular arc.
 */
class SquircleShape(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val w = size.width
        val h = size.height
        // Curve length along each edge, and how far the control points sit from the corner.
        val p = min(r * 1.6f, min(w, h) / 2f)
        val c = p * 0.155f
        val path = Path().apply {
            moveTo(p, 0f)
            lineTo(w - p, 0f)
            cubicTo(w - c, 0f, w, c, w, p)
            lineTo(w, h - p)
            cubicTo(w, h - c, w - c, h, w - p, h)
            lineTo(p, h)
            cubicTo(c, h, 0f, h - c, 0f, h - p)
            lineTo(0f, p)
            cubicTo(0f, c, c, 0f, p, 0f)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is SquircleShape && other.radius == radius

    override fun hashCode(): Int = radius.hashCode()
}
