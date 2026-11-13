package com.example.processing.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.example.core.model.ImageFilterMode
import kotlin.math.max
import kotlin.math.min

/**
 * On-device document image enhancement engine:
 * - Brightness, Contrast, Sharpness (unsharp mask)
 * - Shadow Removal & Background Cleanup (local illumination normalization)
 * - Text Enhancement, High Quality Scan, Grayscale, Black & White
 */
object ImageEnhancementEngine {

    fun enhance(
        source: Bitmap,
        filterMode: ImageFilterMode,
        brightness: Float = 0f,
        contrast: Float = 1.0f,
        sharpness: Float = 0.25f
    ): Bitmap {
        // 1. Apply filter preset + color matrix
        var working = applyColorPreset(source, filterMode, brightness, contrast)

        // 2. Apply shadow removal / background cleanup / adaptive B&W when selected
        when (filterMode) {
            ImageFilterMode.SHADOW_REMOVAL,
            ImageFilterMode.BACKGROUND_CLEANUP -> {
                val cleaned = removeShadowsAndCleanBackground(working, aggressiveWhite = filterMode == ImageFilterMode.BACKGROUND_CLEANUP)
                if (cleaned !== working) working.recycle()
                working = cleaned
            }
            ImageFilterMode.BLACK_AND_WHITE -> {
                val bw = applyAdaptiveBlackAndWhite(working)
                if (bw !== working) working.recycle()
                working = bw
            }
            ImageFilterMode.TEXT_ENHANCE,
            ImageFilterMode.HIGH_QUALITY -> {
                val cleaned = removeShadowsAndCleanBackground(working, aggressiveWhite = false)
                if (cleaned !== working) working.recycle()
                working = cleaned
            }
            else -> Unit
        }

        // 3. Apply sharpness boost if requested
        if (sharpness > 0.05f && filterMode != ImageFilterMode.ORIGINAL) {
            val sharpened = applyUnsharpMask(working, sharpness)
            if (sharpened !== working) working.recycle()
            working = sharpened
        }

        return working
    }

    private fun applyColorPreset(
        source: Bitmap,
        mode: ImageFilterMode,
        brightness: Float,
        contrast: Float
    ): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()

        when (mode) {
            ImageFilterMode.ORIGINAL -> Unit
            ImageFilterMode.COLOR_ENHANCE -> {
                val sat = ColorMatrix().apply { setSaturation(1.22f) }
                cm.postConcat(sat)
                cm.postConcat(contrastBrightnessMatrix(1.18f, 10f))
            }
            ImageFilterMode.HIGH_QUALITY -> {
                val sat = ColorMatrix().apply { setSaturation(1.12f) }
                cm.postConcat(sat)
                cm.postConcat(contrastBrightnessMatrix(1.28f, 12f))
            }
            ImageFilterMode.TEXT_ENHANCE -> {
                val sat = ColorMatrix().apply { setSaturation(0.75f) }
                cm.postConcat(sat)
                cm.postConcat(contrastBrightnessMatrix(1.42f, 16f))
            }
            ImageFilterMode.SHADOW_REMOVAL,
            ImageFilterMode.BACKGROUND_CLEANUP -> {
                cm.postConcat(contrastBrightnessMatrix(1.15f, 8f))
            }
            ImageFilterMode.GRAYSCALE,
            ImageFilterMode.BLACK_AND_WHITE -> {
                val gray = ColorMatrix().apply { setSaturation(0f) }
                cm.postConcat(gray)
                cm.postConcat(contrastBrightnessMatrix(1.25f, 10f))
            }
        }

        // User fine-tune sliders
        if (contrast != 1.0f || brightness != 0f) {
            cm.postConcat(contrastBrightnessMatrix(contrast, brightness))
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun contrastBrightnessMatrix(contrast: Float, brightness: Float): ColorMatrix {
        val translate = (-0.5f * contrast + 0.5f) * 255f + brightness
        return ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    /**
     * Estimates uneven local illumination on a coarse grid and normalizes pixels
     * against local paper white, lifting desk shadows to crisp clean paper.
     */
    private fun removeShadowsAndCleanBackground(
        bitmap: Bitmap,
        aggressiveWhite: Boolean
    ): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val gridCols = 12
        val gridRows = 16
        val cellW = max(1, w / gridCols)
        val cellH = max(1, h / gridRows)
        val localMax = IntArray(gridCols * gridRows) { 180 }

        // Estimate local 90th-ish percentile white per cell by taking max luminance sample
        for (gy in 0 until gridRows) {
            val yStart = gy * cellH
            val yEnd = min(h, yStart + cellH)
            for (gx in 0 until gridCols) {
                val xStart = gx * cellW
                val xEnd = min(w, xStart + cellW)
                var peak = 110
                var y = yStart
                while (y < yEnd) {
                    val row = y * w
                    var x = xStart
                    while (x < xEnd) {
                        val c = pixels[row + x]
                        val lum = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
                        if (lum > peak) peak = lum
                        x += 3
                    }
                    y += 3
                }
                localMax[gy * gridCols + gx] = peak
            }
        }

        val whiteFloor = if (aggressiveWhite) 215 else 235
        for (y in 0 until h) {
            val gy = min(gridRows - 1, y / cellH)
            val row = y * w
            for (x in 0 until w) {
                val gx = min(gridCols - 1, x / cellW)
                val bgWhite = localMax[gy * gridCols + gx].coerceAtLeast(95)
                val c = pixels[row + x]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)

                val scale = 255f / bgWhite
                var nr = (r * scale).toInt().coerceIn(0, 255)
                var ng = (g * scale).toInt().coerceIn(0, 255)
                var nb = (b * scale).toInt().coerceIn(0, 255)

                val lum = (nr * 77 + ng * 150 + nb * 29) shr 8
                if (lum > whiteFloor) {
                    nr = 255
                    ng = 255
                    nb = 255
                }
                pixels[row + x] = Color.argb(255, nr, ng, nb)
            }
        }

        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun applyAdaptiveBlackAndWhite(bitmap: Bitmap): Bitmap {
        val cleaned = removeShadowsAndCleanBackground(bitmap, aggressiveWhite = true)
        val w = cleaned.width
        val h = cleaned.height
        val pixels = IntArray(w * h)
        cleaned.getPixels(pixels, 0, w, 0, 0, w, h)
        if (cleaned !== bitmap) cleaned.recycle()

        for (i in pixels.indices) {
            val c = pixels[i]
            val lum = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
            val out = if (lum > 165) 255 else if (lum < 110) 0 else ((lum - 110) * 255 / 55).coerceIn(0, 255)
            pixels[i] = Color.argb(255, out, out, out)
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun applyUnsharpMask(bitmap: Bitmap, amount: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return bitmap
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        System.arraycopy(src, 0, dst, 0, src.size)

        val strength = (amount * 1.1f).coerceIn(0.1f, 1.2f)
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val idx = row + x
                val c = src[idx]
                val up = src[idx - w]
                val down = src[idx + w]
                val left = src[idx - 1]
                val right = src[idx + 1]

                val rC = Color.red(c)
                val gC = Color.green(c)
                val bC = Color.blue(c)

                val avgR = (Color.red(up) + Color.red(down) + Color.red(left) + Color.red(right)) shr 2
                val avgG = (Color.green(up) + Color.green(down) + Color.green(left) + Color.green(right)) shr 2
                val avgB = (Color.blue(up) + Color.blue(down) + Color.blue(left) + Color.blue(right)) shr 2

                val nr = (rC + ((rC - avgR) * strength)).toInt().coerceIn(0, 255)
                val ng = (gC + ((gC - avgG) * strength)).toInt().coerceIn(0, 255)
                val nb = (bC + ((bC - avgB) * strength)).toInt().coerceIn(0, 255)
                dst[idx] = Color.argb(255, nr, ng, nb)
            }
        }
        return Bitmap.createBitmap(dst, w, h, Bitmap.Config.ARGB_8888)
    }
}
