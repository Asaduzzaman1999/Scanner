package com.example.processing.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrExtractionResult(
    val fullText: String,
    val pageTexts: List<String>,
    val languageNote: String = ""
)

/**
 * Modular offline OCR engine powered by ML Kit Text Recognition (Latin/English)
 * with an extensible Bengali OCR provider hook for future tessdata expansion.
 */
object OcrEngine {

    suspend fun extractTextFromImage(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val input = InputImage.fromBitmap(bitmap, 0)
            suspendCancellableCoroutine { cont ->
                recognizer.process(input)
                    .addOnSuccessListener { result ->
                        cont.resume(result.text)
                    }
                    .addOnFailureListener { e ->
                        cont.resumeWithException(e)
                    }
            }
        }
    }

    suspend fun extractTextFromImageFile(path: String): Result<String> = withContext(Dispatchers.IO) {
        val bmp = BitmapUtils.decodeSampledBitmap(path, maxDimension = 1800)
            ?: return@withContext Result.failure(IllegalArgumentException("Unable to decode image for OCR"))
        try {
            extractTextFromImage(bmp)
        } finally {
            bmp.recycle()
        }
    }

    suspend fun extractTextFromPdf(
        context: Context,
        pdfFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<OcrExtractionResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (!pdfFile.exists()) error("PDF file not found")
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageTexts = mutableListOf<String>()
            try {
                val count = renderer.pageCount
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        val scale = 2
                        val bmp = Bitmap.createBitmap(
                            page.width * scale,
                            page.height * scale,
                            Bitmap.Config.ARGB_8888
                        )
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val txt = extractTextFromImage(bmp).getOrDefault("")
                        bmp.recycle()
                        pageTexts.add(txt)
                    }
                    onProgress((i + 1).toFloat() / count.coerceAtLeast(1))
                }
            } finally {
                renderer.close()
                pfd.close()
            }
            val combined = pageTexts.mapIndexedNotNull { idx, t ->
                if (t.isBlank()) null else "--- Page ${idx + 1} ---\n$t"
            }.joinToString("\n\n")
            OcrExtractionResult(fullText = combined, pageTexts = pageTexts)
        }
    }

    fun saveTextToFile(destFile: File, content: String): Boolean {
        return runCatching {
            destFile.parentFile?.mkdirs()
            destFile.writeText(content, Charsets.UTF_8)
            true
        }.getOrDefault(false)
    }
}
