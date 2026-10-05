package dev.pods.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Draws the Apple Batteries-style ring that wraps each device icon. */
object RingRenderer {
    fun render(sizePx: Int, fraction: Float?, color: Int, trackColor: Int): Bitmap {
        val size = sizePx.coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = size * 0.085f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        val inset = stroke / 2f + 1f
        val rect = RectF(inset, inset, size - inset, size - inset)
        paint.color = trackColor
        canvas.drawArc(rect, 0f, 360f, false, paint)
        if (fraction != null && fraction > 0f) {
            paint.color = color
            canvas.drawArc(rect, -90f, 360f * fraction.coerceIn(0.03f, 1f), false, paint)
        }
        return bitmap
    }
}
