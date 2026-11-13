package com.example.processing.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.core.model.CompressionPreset
import com.example.core.model.NidLayoutOptions
import com.example.core.model.PageOrientation
import com.example.core.model.PageSizeOption
import com.example.core.model.PdfExportOptions
import com.example.core.model.ScanPageItem
import com.example.processing.vision.BitmapUtils
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

data class GeneratedPdfResult(
    val pdfFile: File,
    val thumbnailFile: File?,
    val pageCount: Int,
    val fileSizeBytes: Long
)

object PdfGenerator {

    fun resolvePageRect(
        size: PageSizeOption,
        orientation: PageOrientation,
        customW: Float = 595f,
        customH: Float = 842f
    ): PDRectangle {
        val baseW = if (size == PageSizeOption.CUSTOM) customW else size.widthPt
        val baseH = if (size == PageSizeOption.CUSTOM) customH else size.heightPt
        return if (orientation == PageOrientation.LANDSCAPE) {
            PDRectangle(maxOf(baseW, baseH), minOf(baseW, baseH))
        } else {
            PDRectangle(minOf(baseW, baseH), maxOf(baseW, baseH))
        }
    }

    /**
     * Generates a multi-page PDF sequentially (one image decoded into RAM at a time)
     * to prevent OOM crashes on low-end/mid-range devices during large batch scans.
     */
    suspend fun generateFromPages(
        pages: List<ScanPageItem>,
        outputFile: File,
        thumbnailOutputFile: File?,
        options: PdfExportOptions,
        onProgress: (Float) -> Unit = {}
    ): Result<GeneratedPdfResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(pages.isNotEmpty()) { "At least one page is required to create a PDF." }
            outputFile.parentFile?.mkdirs()

            val pageRect = resolvePageRect(
                options.pageSize,
                options.orientation,
                options.customWidthPt,
                options.customHeightPt
            )
            val jpegQuality = options.compression.jpegQuality / 100f
            val maxDim = options.compression.maxDimensionPx

            PDDocument().use { document ->
                pages.forEachIndexed { index, pageItem ->
                    val imgPath = pageItem.processedImagePath.takeIf { File(it).exists() }
                        ?: pageItem.originalImagePath
                    val bmp = BitmapUtils.decodeSampledBitmap(imgPath, maxDimension = maxDim)
                        ?: error("Failed to decode image for page ${index + 1}")

                    try {
                        val pdPage = PDPage(pageRect)
                        document.addPage(pdPage)

                        val availW = (pageRect.width - 2f * options.marginPt).coerceAtLeast(40f)
                        val availH = (pageRect.height - 2f * options.marginPt).coerceAtLeast(40f)
                        val scaleFactor = (pageItem.targetScalePercent.coerceIn(30, 100) / 100f)
                        val fitScale = min(availW / bmp.width, availH / bmp.height) * scaleFactor
                        val drawW = bmp.width * fitScale
                        val drawH = bmp.height * fitScale
                        val x = (pageRect.width - drawW) / 2f
                        val y = (pageRect.height - drawH) / 2f

                        val pdImage = JPEGFactory.createFromImage(document, bmp, jpegQuality)
                        PDPageContentStream(document, pdPage).use { cs ->
                            cs.drawImage(pdImage, x, y, drawW, drawH)
                        }
                    } finally {
                        bmp.recycle()
                    }
                    onProgress((index + 1).toFloat() / pages.size)
                }

                if (!options.password.isNullOrBlank()) {
                    val ap = AccessPermission()
                    val policy = StandardProtectionPolicy(options.password, options.password, ap).apply {
                        encryptionKeyLength = 128
                    }
                    document.protect(policy)
                }

                document.save(outputFile)
            }

            val thumb = if (thumbnailOutputFile != null && options.password.isNullOrBlank()) {
                renderFirstPageThumbnail(outputFile, thumbnailOutputFile)
            } else null

            GeneratedPdfResult(
                pdfFile = outputFile,
                thumbnailFile = thumb,
                pageCount = pages.size,
                fileSizeBytes = outputFile.length()
            )
        }
    }

    /**
     * Generates a dedicated NID / Dual-Side ID Card PDF placing Front + Back
     * neatly aligned and uniformly resized on the same A4 page (or separate pages).
     */
    suspend fun generateNidDualSidePdf(
        frontPage: ScanPageItem,
        backPage: ScanPageItem,
        outputFile: File,
        thumbnailOutputFile: File?,
        layout: NidLayoutOptions,
        password: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<GeneratedPdfResult> = withContext(Dispatchers.IO) {
        if (!layout.samePage) {
            return@withContext generateFromPages(
                pages = listOf(frontPage, backPage),
                outputFile = outputFile,
                thumbnailOutputFile = thumbnailOutputFile,
                options = PdfExportOptions(
                    fileName = outputFile.nameWithoutExtension,
                    folderName = "NID",
                    pageSize = layout.pageSize,
                    orientation = layout.orientation,
                    marginPt = layout.marginDp,
                    compression = if (layout.highQuality) CompressionPreset.HIGH_QUALITY else CompressionPreset.BALANCED,
                    password = password
                ),
                onProgress = onProgress
            )
        }

        runCatching {
            outputFile.parentFile?.mkdirs()
            val pageRect = resolvePageRect(layout.pageSize, layout.orientation)
            val jpegQuality = if (layout.highQuality) 0.94f else 0.78f
            val maxDim = if (layout.highQuality) 2200 else 1500

            val frontPath = frontPage.processedImagePath.takeIf { File(it).exists() } ?: frontPage.originalImagePath
            val backPath = backPage.processedImagePath.takeIf { File(it).exists() } ?: backPage.originalImagePath

            val frontBmp = BitmapUtils.decodeSampledBitmap(frontPath, maxDim)
                ?: error("Could not decode NID front image")
            val backBmp = BitmapUtils.decodeSampledBitmap(backPath, maxDim)
                ?: run { frontBmp.recycle(); error("Could not decode NID back image") }

            try {
                PDDocument().use { document ->
                    val page = PDPage(pageRect)
                    document.addPage(page)

                    val margin = layout.marginDp.coerceIn(12f, 64f)
                    val gap = 24f
                    val usableW = pageRect.width - 2f * margin
                    val slotH = (pageRect.height - 2f * margin - gap) / 2f

                    // Standard NID card ratio ~ 1.586 (85.6mm x 53.98mm)
                    val cardWidth = min(usableW * 0.86f, slotH * 1.586f)
                    val cardHeight = if (layout.autoResizeUniform) cardWidth / 1.586f else slotH * 0.85f

                    val xPos = if (layout.centerAlign) (pageRect.width - cardWidth) / 2f else margin
                    val topSlotY = pageRect.height - margin - slotH + (slotH - cardHeight) / 2f
                    val bottomSlotY = margin + (slotH - cardHeight) / 2f

                    val frontImg = JPEGFactory.createFromImage(document, frontBmp, jpegQuality)
                    onProgress(0.5f)
                    val backImg = JPEGFactory.createFromImage(document, backBmp, jpegQuality)

                    PDPageContentStream(document, page).use { cs ->
                        cs.drawImage(frontImg, xPos, topSlotY, cardWidth, cardHeight)
                        cs.drawImage(backImg, xPos, bottomSlotY, cardWidth, cardHeight)

                        if (layout.drawGuideBorder) {
                            cs.setStrokingColor(203, 213, 225)
                            cs.setLineWidth(1.0f)
                            cs.addRect(xPos, topSlotY, cardWidth, cardHeight)
                            cs.stroke()
                            cs.addRect(xPos, bottomSlotY, cardWidth, cardHeight)
                            cs.stroke()
                        }
                    }

                    if (!password.isNullOrBlank()) {
                        val ap = AccessPermission()
                        val policy = StandardProtectionPolicy(password, password, ap).apply {
                            encryptionKeyLength = 128
                        }
                        document.protect(policy)
                    }

                    document.save(outputFile)
                    onProgress(1.0f)
                }
            } finally {
                frontBmp.recycle()
                backBmp.recycle()
            }

            val thumb = if (thumbnailOutputFile != null && password.isNullOrBlank()) {
                renderFirstPageThumbnail(outputFile, thumbnailOutputFile)
            } else null

            GeneratedPdfResult(
                pdfFile = outputFile,
                thumbnailFile = thumb,
                pageCount = 1,
                fileSizeBytes = outputFile.length()
            )
        }
    }

    fun renderFirstPageThumbnail(pdfFile: File, destThumb: File): File? {
        return runCatching {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            try {
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val targetW = 360
                    val targetH = (page.height * targetW / page.width.coerceAtLeast(1)).coerceAtLeast(120)
                    val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    BitmapUtils.saveBitmapAsJpeg(bmp, destThumb, quality = 82)
                    bmp.recycle()
                }
                destThumb
            } finally {
                renderer.close()
                pfd.close()
            }
        }.getOrNull()
    }
}
