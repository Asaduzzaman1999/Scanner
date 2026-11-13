package com.example.processing.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.core.model.CompressionPreset
import com.example.core.model.NormalizedQuad
import com.example.processing.vision.BitmapUtils
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DrawPathSpec(
    val points: List<Pair<Float, Float>>, // normalized 0..1 coordinates
    val colorInt: Int,
    val strokeWidthRatio: Float = 0.006f,
    val isHighlight: Boolean = false
)

data class TextOverlaySpec(
    val text: String,
    val normX: Float,
    val normY: Float,
    val fontSizeSp: Float = 18f,
    val colorInt: Int = Color.BLACK
)

data class ImageOverlaySpec(
    val imagePath: String,
    val normX: Float,
    val normY: Float,
    val normWidth: Float
)

object PdfEditorEngine {

    suspend fun getPageCount(pdfFile: File): Int = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        }.getOrElse {
            runCatching {
                PDDocument.load(pdfFile).use { it.numberOfPages }
            }.getOrDefault(0)
        }
    }

    suspend fun isPasswordProtected(pdfFile: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(pdfFile).use { it.isEncrypted }
        }.getOrDefault(true)
    }

    suspend fun renderPageBitmap(
        pdfFile: File,
        pageIndex: Int,
        targetWidth: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val w = targetWidth.coerceIn(160, 2200)
                        val h = (page.height * w / page.width.coerceAtLeast(1)).coerceAtLeast(100)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }.getOrNull()
    }

    suspend fun rotatePage(
        pdfFile: File,
        pageIndex: Int,
        deltaDegrees: Int = 90,
        password: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = if (password.isNullOrBlank()) PDDocument.load(pdfFile) else PDDocument.load(pdfFile, password)
            doc.use { pd ->
                val page = pd.getPage(pageIndex)
                page.rotation = (page.rotation + deltaDegrees + 360) % 360
                saveDocumentAtomic(pd, pdfFile)
            }
        }
    }

    suspend fun deletePage(
        pdfFile: File,
        pageIndex: Int,
        password: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = if (password.isNullOrBlank()) PDDocument.load(pdfFile) else PDDocument.load(pdfFile, password)
            doc.use { pd ->
                require(pd.numberOfPages > 1) { "Cannot delete the only remaining page." }
                pd.removePage(pageIndex)
                saveDocumentAtomic(pd, pdfFile)
                pd.numberOfPages
            }
        }
    }

    suspend fun reorderPages(
        pdfFile: File,
        newOrderIndices: List<Int>,
        password: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val srcDoc = if (password.isNullOrBlank()) PDDocument.load(pdfFile) else PDDocument.load(pdfFile, password)
            srcDoc.use { src ->
                PDDocument().use { dst ->
                    newOrderIndices.forEach { idx ->
                        if (idx in 0 until src.numberOfPages) {
                            dst.importPage(src.getPage(idx))
                        }
                    }
                    saveDocumentAtomic(dst, pdfFile)
                }
            }
        }
    }

    suspend fun addImagePage(
        pdfFile: File,
        imagePath: String,
        insertIndex: Int? = null,
        password: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = BitmapUtils.decodeSampledBitmap(imagePath, 2000)
                ?: error("Unable to decode image")
            try {
                val doc = if (password.isNullOrBlank()) PDDocument.load(pdfFile) else PDDocument.load(pdfFile, password)
                doc.use { pd ->
                    val rect = PDRectangle.A4
                    val newPage = PDPage(rect)
                    val idx = (insertIndex ?: pd.numberOfPages).coerceIn(0, pd.numberOfPages)
                    if (idx >= pd.numberOfPages) {
                        pd.addPage(newPage)
                    } else {
                        pd.pages.insertBefore(newPage, pd.getPage(idx))
                    }
                    val margin = 24f
                    val availW = rect.width - 2 * margin
                    val availH = rect.height - 2 * margin
                    val scale = minOf(availW / bmp.width, availH / bmp.height)
                    val w = bmp.width * scale
                    val h = bmp.height * scale
                    val pdImg = JPEGFactory.createFromImage(pd, bmp, 0.90f)
                    PDPageContentStream(pd, newPage).use { cs ->
                        cs.drawImage(pdImg, (rect.width - w) / 2f, (rect.height - h) / 2f, w, h)
                    }
                    saveDocumentAtomic(pd, pdfFile)
                    pd.numberOfPages
                }
            } finally {
                bmp.recycle()
            }
        }
    }

    suspend fun replacePageWithImage(
        pdfFile: File,
        pageIndex: Int,
        imagePath: String,
        password: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = BitmapUtils.decodeSampledBitmap(imagePath, 2000)
                ?: error("Unable to decode image")
            try {
                val doc = if (password.isNullOrBlank()) PDDocument.load(pdfFile) else PDDocument.load(pdfFile, password)
                doc.use { pd ->
                    val targetPage = pd.getPage(pageIndex)
                    val rect = targetPage.mediaBox ?: PDRectangle.A4
                    val newPage = PDPage(rect)
                    pd.pages.insertBefore(newPage, targetPage)
                    pd.removePage(targetPage)

                    val margin = 20f
                    val availW = rect.width - 2 * margin
                    val availH = rect.height - 2 * margin
                    val scale = minOf(availW / bmp.width, availH / bmp.height)
                    val w = bmp.width * scale
                    val h = bmp.height * scale
                    val pdImg = JPEGFactory.createFromImage(pd, bmp, 0.90f)
                    PDPageContentStream(pd, newPage).use { cs ->
                        cs.drawImage(pdImg, (rect.width - w) / 2f, (rect.height - h) / 2f, w, h)
                    }
                    saveDocumentAtomic(pd, pdfFile)
                }
            } finally {
                bmp.recycle()
            }
        }
    }

    suspend fun cropPage(
        pdfFile: File,
        pageIndex: Int,
        quad: NormalizedQuad,
        password: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = if (password.isNullOrBlank()) PDDocument.load(pdfFile) else PDDocument.load(pdfFile, password)
            doc.use { pd ->
                val page = pd.getPage(pageIndex)
                val box = page.cropBox ?: page.mediaBox ?: PDRectangle.A4
                val leftFrac = minOf(quad.topLeft.x, quad.bottomLeft.x).coerceIn(0f, 0.85f)
                val rightFrac = maxOf(quad.topRight.x, quad.bottomRight.x).coerceIn(leftFrac + 0.1f, 1f)
                val topFrac = minOf(quad.topLeft.y, quad.topRight.y).coerceIn(0f, 0.85f)
                val bottomFrac = maxOf(quad.bottomLeft.y, quad.bottomRight.y).coerceIn(topFrac + 0.1f, 1f)

                val llx = box.lowerLeftX + leftFrac * box.width
                val urx = box.lowerLeftX + rightFrac * box.width
                val lly = box.lowerLeftY + (1f - bottomFrac) * box.height
                val ury = box.lowerLeftY + (1f - topFrac) * box.height
                page.cropBox = PDRectangle(llx, lly, urx - llx, ury - lly)
                saveDocumentAtomic(pd, pdfFile)
            }
        }
    }

    /**
     * Applies vector/raster overlays onto a PDF page:
     * - Freehand drawing & translucent highlight strokes
     * - Unicode-safe text annotations (supports both English & Bengali via Android Canvas rendering)
     * - Signature / Stamp / Image overlays
     */
    suspend fun applyPageOverlays(
        pdfFile: File,
        pageIndex: Int,
        strokes: List<DrawPathSpec>,
        texts: List<TextOverlaySpec>,
        images: List<ImageOverlaySpec>,
        password: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = if (password.isNullOrBlank()) PDDocument.load(pdfFile) else PDDocument.load(pdfFile, password)
            doc.use { pd ->
                val page = pd.getPage(pageIndex)
                val box = page.cropBox ?: page.mediaBox ?: PDRectangle.A4

                PDPageContentStream(pd, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    // 1. Freehand strokes & highlights
                    strokes.forEach { stroke ->
                        if (stroke.points.size >= 2) {
                            cs.saveGraphicsState()
                            if (stroke.isHighlight) {
                                val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.35f; strokingAlphaConstant = 0.35f }
                                cs.setGraphicsStateParameters(gs)
                            }
                            cs.setStrokingColor(
                                Color.red(stroke.colorInt),
                                Color.green(stroke.colorInt),
                                Color.blue(stroke.colorInt)
                            )
                            cs.setLineWidth(stroke.strokeWidthRatio * box.width)
                            cs.setLineCapStyle(1)
                            cs.setLineJoinStyle(1)

                            val first = stroke.points.first()
                            cs.moveTo(
                                box.lowerLeftX + first.first * box.width,
                                box.lowerLeftY + (1f - first.second) * box.height
                            )
                            for (i in 1 until stroke.points.size) {
                                val pt = stroke.points[i]
                                cs.lineTo(
                                    box.lowerLeftX + pt.first * box.width,
                                    box.lowerLeftY + (1f - pt.second) * box.height
                                )
                            }
                            cs.stroke()
                            cs.restoreGraphicsState()
                        }
                    }

                    // 2. Text overlays rendered cleanly as transparent crisp bitmaps (full Unicode/Bengali support)
                    texts.forEach { t ->
                        if (t.text.isNotBlank()) {
                            val bmp = renderUnicodeTextBitmap(t.text, t.fontSizeSp * 2.2f, t.colorInt)
                            try {
                                val pdImg = LosslessFactory.createFromImage(pd, bmp)
                                val drawW = (bmp.width / 2.2f).coerceAtMost(box.width * 0.85f)
                                val drawH = drawW * (bmp.height.toFloat() / bmp.width.coerceAtLeast(1))
                                val x = (box.lowerLeftX + t.normX * box.width).coerceIn(box.lowerLeftX, box.upperRightX - drawW)
                                val y = (box.lowerLeftY + (1f - t.normY) * box.height - drawH).coerceIn(box.lowerLeftY, box.upperRightY - drawH)
                                cs.drawImage(pdImg, x, y, drawW, drawH)
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }

                    // 3. Images / Signatures
                    images.forEach { img ->
                        val bmp = BitmapUtils.decodeSampledBitmap(img.imagePath, 1024)
                        if (bmp != null) {
                            try {
                                val pdImg = LosslessFactory.createFromImage(pd, bmp)
                                val drawW = (img.normWidth * box.width).coerceIn(48f, box.width * 0.9f)
                                val drawH = drawW * (bmp.height.toFloat() / bmp.width.coerceAtLeast(1))
                                val x = (box.lowerLeftX + img.normX * box.width).coerceIn(box.lowerLeftX, box.upperRightX - drawW)
                                val y = (box.lowerLeftY + (1f - img.normY) * box.height - drawH / 2f).coerceIn(box.lowerLeftY, box.upperRightY - drawH)
                                cs.drawImage(pdImg, x, y, drawW, drawH)
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }
                saveDocumentAtomic(pd, pdfFile)
            }
        }
    }

    suspend fun addPageNumbers(
        pdfFile: File,
        password: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = if (password.isNullOrBlank()) PDDocument.load(pdfFile) else PDDocument.load(pdfFile, password)
            doc.use { pd ->
                val total = pd.numberOfPages
                for (i in 0 until total) {
                    val page = pd.getPage(i)
                    val box = page.cropBox ?: page.mediaBox ?: PDRectangle.A4
                    val label = "Page ${i + 1} of $total"
                    PDPageContentStream(pd, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA, 10f)
                        cs.setNonStrokingColor(90, 100, 115)
                        cs.newLineAtOffset(box.lowerLeftX + box.width / 2f - 28f, box.lowerLeftY + 14f)
                        cs.showText(label)
                        cs.endText()
                    }
                }
                saveDocumentAtomic(pd, pdfFile)
            }
        }
    }

    suspend fun addDateStampToPage(
        pdfFile: File,
        pageIndex: Int,
        password: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())
        applyPageOverlays(
            pdfFile = pdfFile,
            pageIndex = pageIndex,
            strokes = emptyList(),
            texts = listOf(TextOverlaySpec(text = dateStr, normX = 0.68f, normY = 0.08f, fontSizeSp = 13f, colorInt = Color.DKGRAY)),
            images = emptyList(),
            password = password
        )
    }

    suspend fun addWatermark(
        pdfFile: File,
        watermarkText: String,
        password: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(watermarkText.isNotBlank()) { "Watermark text cannot be empty" }
            val doc = if (password.isNullOrBlank()) PDDocument.load(pdfFile) else PDDocument.load(pdfFile, password)
            doc.use { pd ->
                val bmp = renderUnicodeTextBitmap(watermarkText, 52f, Color.argb(65, 15, 118, 110), rotateDeg = -32f)
                try {
                    val pdImg = LosslessFactory.createFromImage(pd, bmp)
                    for (i in 0 until pd.numberOfPages) {
                        val page = pd.getPage(i)
                        val box = page.cropBox ?: page.mediaBox ?: PDRectangle.A4
                        val drawW = box.width * 0.72f
                        val drawH = drawW * (bmp.height.toFloat() / bmp.width.coerceAtLeast(1))
                        PDPageContentStream(pd, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            cs.drawImage(
                                pdImg,
                                box.lowerLeftX + (box.width - drawW) / 2f,
                                box.lowerLeftY + (box.height - drawH) / 2f,
                                drawW,
                                drawH
                            )
                        }
                    }
                    saveDocumentAtomic(pd, pdfFile)
                } finally {
                    bmp.recycle()
                }
            }
        }
    }

    suspend fun mergePdfs(
        sourceFiles: List<File>,
        outputFile: File
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            require(sourceFiles.size >= 2) { "Select at least two PDFs to merge." }
            outputFile.parentFile?.mkdirs()
            val merger = PDFMergerUtility()
            merger.destinationFileName = outputFile.absolutePath
            sourceFiles.forEach { merger.addSource(it) }
            merger.mergeDocuments(null)
            getPageCount(outputFile)
        }
    }

    suspend fun splitPdf(
        sourceFile: File,
        fromPage1Based: Int,
        toPage1Based: Int,
        outputFile: File,
        password: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = if (password.isNullOrBlank()) PDDocument.load(sourceFile) else PDDocument.load(sourceFile, password)
            doc.use { src ->
                val start = (fromPage1Based - 1).coerceIn(0, src.numberOfPages - 1)
                val end = (toPage1Based - 1).coerceIn(start, src.numberOfPages - 1)
                PDDocument().use { dst ->
                    for (i in start..end) {
                        dst.importPage(src.getPage(i))
                    }
                    outputFile.parentFile?.mkdirs()
                    dst.save(outputFile)
                    dst.numberOfPages
                }
            }
        }
    }

    suspend fun compressPdf(
        sourceFile: File,
        outputFile: File,
        preset: CompressionPreset,
        onProgress: (Float) -> Unit = {}
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            try {
                val count = renderer.pageCount
                PDDocument().use { outDoc ->
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val scale = when (preset) {
                                CompressionPreset.HIGH_QUALITY -> 2.0f
                                CompressionPreset.BALANCED -> 1.4f
                                CompressionPreset.SMALL_SIZE -> 1.0f
                            }
                            val w = (page.width * scale).toInt().coerceIn(320, preset.maxDimensionPx)
                            val h = (page.height * w / page.width.coerceAtLeast(1)).coerceAtLeast(240)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                            outDoc.addPage(pdPage)
                            val img = JPEGFactory.createFromImage(outDoc, bmp, preset.jpegQuality / 100f)
                            PDPageContentStream(outDoc, pdPage).use { cs ->
                                cs.drawImage(img, 0f, 0f, pdPage.mediaBox.width, pdPage.mediaBox.height)
                            }
                            bmp.recycle()
                        }
                        onProgress((i + 1).toFloat() / count.coerceAtLeast(1))
                    }
                    outputFile.parentFile?.mkdirs()
                    val tempOut = File(outputFile.parentFile, "${outputFile.name}.tmp")
                    outDoc.save(tempOut)
                    if (outputFile.exists()) outputFile.delete()
                    tempOut.renameTo(outputFile)
                }
                outputFile.length()
            } finally {
                renderer.close()
                pfd.close()
            }
        }
    }

    suspend fun setPassword(
        pdfFile: File,
        newPassword: String,
        currentPassword: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = if (currentPassword.isNullOrBlank()) PDDocument.load(pdfFile)
            else PDDocument.load(pdfFile, currentPassword)
            doc.use { pd ->
                val ap = AccessPermission()
                val policy = StandardProtectionPolicy(newPassword, newPassword, ap).apply {
                    encryptionKeyLength = 128
                }
                pd.protect(policy)
                saveDocumentAtomic(pd, pdfFile)
            }
        }
    }

    suspend fun removePassword(
        pdfFile: File,
        currentPassword: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(pdfFile, currentPassword).use { pd ->
                pd.isAllSecurityToBeRemoved = true
                saveDocumentAtomic(pd, pdfFile)
            }
        }
    }

    suspend fun verifyPdfPassword(pdfFile: File, password: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(pdfFile, password).use { true }
        }.getOrDefault(false)
    }

    private fun saveDocumentAtomic(doc: PDDocument, target: File) {
        val temp = File(target.parentFile, "${target.name}.saving")
        doc.save(temp)
        if (target.exists()) target.delete()
        temp.renameTo(target)
    }

    private fun renderUnicodeTextBitmap(
        text: String,
        textSizePx: Float,
        colorInt: Int,
        rotateDeg: Float = 0f
    ): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            textSize = textSizePx
            typeface = Typeface.DEFAULT_BOLD
        }
        val maxLineW = 900
        val desiredW = paint.measureText(text).toInt().coerceIn(80, maxLineW)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, desiredW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()

        val pad = 24
        val rawW = layout.width + pad * 2
        val rawH = layout.height + pad * 2
        val raw = Bitmap.createBitmap(rawW, rawH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(raw)
        canvas.translate(pad.toFloat(), pad.toFloat())
        layout.draw(canvas)

        if (rotateDeg == 0f) return raw
        val m = android.graphics.Matrix().apply { postRotate(rotateDeg) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (rotated !== raw) raw.recycle()
        return rotated
    }
}
