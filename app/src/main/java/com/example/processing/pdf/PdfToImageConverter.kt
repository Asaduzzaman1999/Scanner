package com.example.processing.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.core.model.ExportImageFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ExportedImagesResult(
    val exportedFiles: List<File>,
    val shareUris: List<Uri>,
    val savedToGalleryCount: Int
)

object PdfToImageConverter {

    suspend fun convertPdfToImages(
        context: Context,
        pdfFile: File,
        pageIndicesZeroBased: List<Int>,
        format: ExportImageFormat,
        dpi: Int = 150,
        quality: Int = 90,
        saveToGallery: Boolean = true,
        outputDir: File,
        onProgress: (Float) -> Unit = {}
    ): Result<ExportedImagesResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(pdfFile.exists()) { "PDF file does not exist." }
            require(pageIndicesZeroBased.isNotEmpty()) { "Select at least one page." }
            outputDir.mkdirs()

            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val exportedFiles = mutableListOf<File>()
            val shareUris = mutableListOf<Uri>()
            var galleryCount = 0

            val scale = (dpi / 72f).coerceIn(1f, 4.2f)
            val baseName = pdfFile.nameWithoutExtension

            try {
                val total = pageIndicesZeroBased.size
                pageIndicesZeroBased.forEachIndexed { idx, pageIndex ->
                    if (pageIndex in 0 until renderer.pageCount) {
                        renderer.openPage(pageIndex).use { page ->
                            val w = (page.width * scale).toInt().coerceIn(300, 3200)
                            val h = (page.height * scale).toInt().coerceIn(300, 4200)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val fileName = "${baseName}_page_${pageIndex + 1}.${format.extension}"
                            val outFile = File(outputDir, fileName)
                            saveFormattedBitmap(bmp, outFile, format, quality)
                            exportedFiles.add(outFile)
                            shareUris.add(
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    outFile
                                )
                            )

                            if (saveToGallery) {
                                val inserted = saveBitmapToMediaStore(context, bmp, fileName, format, quality)
                                if (inserted != null) galleryCount++
                            }
                            bmp.recycle()
                        }
                    }
                    onProgress((idx + 1).toFloat() / total)
                }
            } finally {
                renderer.close()
                pfd.close()
            }

            ExportedImagesResult(
                exportedFiles = exportedFiles,
                shareUris = shareUris,
                savedToGalleryCount = galleryCount
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun saveFormattedBitmap(
        bmp: Bitmap,
        dest: File,
        format: ExportImageFormat,
        quality: Int
    ) {
        val compressFormat = when (format) {
            ExportImageFormat.JPG -> Bitmap.CompressFormat.JPEG
            ExportImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ExportImageFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
        }
        FileOutputStream(dest).use { out ->
            bmp.compress(compressFormat, quality.coerceIn(10, 100), out)
        }
    }

    @Suppress("DEPRECATION")
    private fun saveBitmapToMediaStore(
        context: Context,
        bmp: Bitmap,
        displayName: String,
        format: ExportImageFormat,
        quality: Int
    ): Uri? {
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DocScan PDF")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                val compressFormat = when (format) {
                    ExportImageFormat.JPG -> Bitmap.CompressFormat.JPEG
                    ExportImageFormat.PNG -> Bitmap.CompressFormat.PNG
                    ExportImageFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        Bitmap.CompressFormat.WEBP
                    }
                }
                bmp.compress(compressFormat, quality.coerceIn(10, 100), out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }.getOrNull()
    }
}
