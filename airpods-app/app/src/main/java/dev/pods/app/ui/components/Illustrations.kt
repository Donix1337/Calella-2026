package dev.pods.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import dev.pods.app.ble.PodKind
import dev.pods.app.ui.theme.Pods
import kotlin.math.min

private val White = Color(0xFFFFFFFF)
private val Pearl = Color(0xFFF5F5F7)
private val Shade = Color(0xFFE1E1E6)
private val DeepShade = Color(0xFFCBCBD1)
private val Outline = Color(0x14000000)
private val Mesh = Color(0xFF2C2C2E)

/**
 * A single AirPod drawn in code, so it stays crisp at any size. Drawn in a
 * 100 x 160 unit box; the right bud is the mirror image of the left.
 */
@Composable
fun BudIllustration(left: Boolean, kind: PodKind, modifier: Modifier = Modifier) {
    val dark = Pods.colors.isDark
    Canvas(modifier) {
        val s = min(size.width / 100f, size.height / 160f)
        val ox = (size.width - 100f * s) / 2f
        val oy = size.height - 160f * s
        translate(ox, oy) {
            scale(scaleX = if (left) 1f else -1f, scaleY = 1f, pivot = Offset(50f * s, 80f * s)) {
                drawBud(s, kind, dark)
            }
        }
    }
}

private fun DrawScope.drawBud(s: Float, kind: PodKind, dark: Boolean) {
    fun u(v: Float) = v * s
    val hasTip = kind == PodKind.PRO
    val classic = kind == PodKind.CLASSIC

    // Soft contact shadow.
    drawShadow(center = Offset(u(34f), u(153f)), radius = u(26f), dark = dark)

    // Stem: a capsule leaning slightly outward, shaded like a cylinder.
    val stemTop = if (classic) 46f else 56f
    val stemBottom = if (classic) 150f else 146f
    val stemWidth = if (classic) 19f else 22f
    val stemLeft = 32f - stemWidth / 2f
    rotate(degrees = 7f, pivot = Offset(u(32f), u(stemTop + 6f))) {
        val stemRect = RoundRect(
            left = u(stemLeft), top = u(stemTop), right = u(stemLeft + stemWidth), bottom = u(stemBottom),
            cornerRadius = CornerRadius(u(stemWidth / 2f)),
        )
        val stemPath = Path().apply { addRoundRect(stemRect) }
        drawPath(
            stemPath,
            Brush.horizontalGradient(
                0f to Shade, 0.28f to White, 0.62f to Pearl, 1f to DeepShade,
                startX = u(stemLeft), endX = u(stemLeft + stemWidth),
            ),
        )
        drawPath(stemPath, Outline, style = Stroke(u(0.8f)))
        // Microphone grille at the tip of the stem.
        drawOval(
            color = Color(0xFFADADB4),
            topLeft = Offset(u(32f - stemWidth * 0.27f), u(stemBottom - 8f)),
            size = Size(u(stemWidth * 0.54f), u(5f)),
        )
        // Force sensor flat.
        drawRoundRect(
            color = Color(0x0F000000),
            topLeft = Offset(u(30f), u(stemTop + 30f)),
            size = Size(u(4f), u(26f)),
            cornerRadius = CornerRadius(u(2f)),
        )
    }

    // Silicone ear tip, tucked behind the head.
    if (hasTip) {
        rotate(degrees = -18f, pivot = Offset(u(72f), u(52f))) {
            val tip = Rect(u(55f), u(31f), u(89f), u(73f))
            drawOval(
                brush = Brush.radialGradient(
                    0f to Pearl, 0.7f to Shade, 1f to DeepShade,
                    center = Offset(u(66f), u(44f)), radius = u(30f),
                ),
                topLeft = tip.topLeft, size = tip.size,
            )
            drawOval(Outline, topLeft = tip.topLeft, size = tip.size, style = Stroke(u(0.8f)))
            // The opening of the tip.
            drawOval(
                color = Color(0x2E000000),
                topLeft = Offset(u(74f), u(43f)),
                size = Size(u(10f), u(18f)),
            )
        }
    }

    // Head: a smooth pebble with a highlight on the top left.
    val head = if (classic) Rect(u(12f), u(10f), u(66f), u(60f)) else Rect(u(8f), u(14f), u(74f), u(76f))
    rotate(degrees = -10f, pivot = head.center) {
        drawOval(
            brush = Brush.radialGradient(
                0f to White, 0.55f to Pearl, 1f to Shade,
                center = Offset(head.left + head.width * 0.36f, head.top + head.height * 0.3f),
                radius = head.width * 0.78f,
            ),
            topLeft = head.topLeft, size = head.size,
        )
        drawOval(Outline, topLeft = head.topLeft, size = head.size, style = Stroke(u(0.8f)))
        // Vent mesh.
        drawOval(
            color = Mesh.copy(alpha = 0.88f),
            topLeft = Offset(head.left + head.width * 0.22f, head.top + head.height * 0.13f),
            size = Size(head.width * 0.22f, head.height * 0.14f),
        )
        if (!classic) {
            // Inward-facing speaker grille, visible as a darker crescent.
            drawOval(
                color = Color(0x1A000000),
                topLeft = Offset(head.left + head.width * 0.62f, head.top + head.height * 0.42f),
                size = Size(head.width * 0.2f, head.height * 0.3f),
            )
        }
    }
}

/**
 * The charging case. Landscape for AirPods Pro / 3 / 4, portrait for the
 * original AirPods. [lightColor] is the status light, or null when off.
 */
@Composable
fun CaseIllustration(kind: PodKind, lightColor: Color?, modifier: Modifier = Modifier) {
    val dark = Pods.colors.isDark
    Canvas(modifier) {
        val portrait = kind == PodKind.CLASSIC
        val unitW = if (portrait) 76f else 100f
        val unitH = if (portrait) 100f else 80f
        val s = min(size.width / unitW, size.height / unitH)
        val ox = (size.width - unitW * s) / 2f
        val oy = size.height - unitH * s
        translate(ox, oy) {
            drawCase(s, portrait, lightColor, dark)
        }
    }
}

private fun DrawScope.drawCase(s: Float, portrait: Boolean, lightColor: Color?, dark: Boolean) {
    fun u(v: Float) = v * s
    val w = if (portrait) 72f else 98f
    val h = if (portrait) 92f else 72f
    val left = if (portrait) 2f else 1f
    val top = 1f
    val radius = if (portrait) 20f else 24f
    val seam = if (portrait) 30f else 26f

    drawShadow(center = Offset(u(left + w / 2f), u(top + h + 4f)), radius = u(w * 0.48f), dark = dark)

    val body = RoundRect(
        left = u(left), top = u(top), right = u(left + w), bottom = u(top + h),
        cornerRadius = CornerRadius(u(radius)),
    )
    val bodyPath = Path().apply { addRoundRect(body) }
    drawPath(
        bodyPath,
        Brush.verticalGradient(
            0f to White, 0.5f to Pearl, 1f to Shade,
            startY = u(top), endY = u(top + h),
        ),
    )
    clipPath(bodyPath) {
        // Lid seam with a little highlight under it.
        drawLine(Color(0xFFCFCFD5), Offset(u(left), u(top + seam)), Offset(u(left + w), u(top + seam)), strokeWidth = u(1.2f))
        drawLine(White, Offset(u(left), u(top + seam + 1.3f)), Offset(u(left + w), u(top + seam + 1.3f)), strokeWidth = u(0.8f))
        // Gentle sheen across the lid.
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.7f), 1f to Color.Transparent,
                startY = u(top), endY = u(top + seam),
            ),
            topLeft = Offset(u(left), u(top)),
            size = Size(u(w), u(seam)),
        )
    }
    drawPath(bodyPath, Outline, style = Stroke(u(0.8f)))

    // Status light.
    val light = Offset(u(left + w / 2f), u(top + seam + (h - seam) * 0.42f))
    if (lightColor != null) {
        drawCircle(
            Brush.radialGradient(
                0f to lightColor.copy(alpha = 0.45f), 1f to Color.Transparent,
                center = light, radius = u(7f),
            ),
            radius = u(7f), center = light,
        )
        drawCircle(lightColor, radius = u(2.1f), center = light)
    } else {
        drawCircle(Color(0xFFD7D7DC), radius = u(1.9f), center = light)
    }
}

private fun DrawScope.drawShadow(center: Offset, radius: Float, dark: Boolean) {
    scale(scaleX = 1f, scaleY = 0.2f, pivot = center) {
        drawCircle(
            Brush.radialGradient(
                0f to Color.Black.copy(alpha = if (dark) 0.55f else 0.18f), 1f to Color.Transparent,
                center = center, radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}
