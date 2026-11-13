package com.example.processing.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.example.core.model.NormalizedQuad
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Performs 4-point homography perspective unwarping, straightening, and rotation.
 */
object PerspectiveTransformer {

    fun warpAndTransform(
        source: Bitmap,
        quad: NormalizedQuad,
        rotationDegrees: Int = 0,
        straightenDegrees: Float = 0f,
        targetScalePercent: Int = 100
    ): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val x0 = quad.topLeft.x * w
        val y0 = quad.topLeft.y * h
        val x1 = quad.topRight.x * w
        val y1 = quad.topRight.y * h
        val x2 = quad.bottomRight.x * w
        val y2 = quad.bottomRight.y * h
        val x3 = quad.bottomLeft.x * w
        val y3 = quad.bottomLeft.y * h

        val topWidth = hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
        val bottomWidth = hypot((x2 - x3).toDouble(), (y2 - y3).toDouble()).toFloat()
        val leftHeight = hypot((x3 - x0).toDouble(), (y3 - y0).toDouble()).toFloat()
        val rightHeight = hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()

        val scale = (targetScalePercent.coerceIn(25, 100) / 100f)
        val outW = max(120, (max(topWidth, bottomWidth) * scale).roundToInt())
        val outH = max(120, (max(leftHeight, rightHeight) * scale).roundToInt())

        val srcPoints = floatArrayOf(x0, y0, x1, y1, x2, y2, x3, y3)
        val dstPoints = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )

        val polyMatrix = Matrix()
        polyMatrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val warped = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(warped)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, polyMatrix, paint)

        val totalAngle = (rotationDegrees % 360) + straightenDegrees
        if (totalAngle == 0f) return warped

        val rotMatrix = Matrix().apply { postRotate(totalAngle) }
        val rotated = Bitmap.createBitmap(warped, 0, 0, warped.width, warped.height, rotMatrix, true)
        if (rotated !== warped) warped.recycle()
        return rotated
    }
}
