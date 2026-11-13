package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.ads.AdMobConfig
import com.example.core.model.DocumentTemplate
import com.example.core.model.ImageFilterMode
import com.example.core.model.NidLayoutOptions
import com.example.core.model.NormalizedQuad
import com.example.core.model.PageOrientation
import com.example.core.model.PageSizeOption
import com.example.core.model.PdfExportOptions
import com.example.core.model.ScanPageItem
import com.example.data.local.FileStorageManager
import com.example.processing.pdf.PdfEditorEngine
import com.example.processing.pdf.PdfGenerator
import com.example.processing.vision.AutoEdgeDetector
import com.example.processing.vision.BitmapUtils
import com.example.processing.vision.ImageEnhancementEngine
import com.example.processing.vision.PerspectiveTransformer
import com.example.security.AppLockManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocScanRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun `test ads configuration uses official Google AdMob test IDs only`() {
        assertTrue(AdMobConfig.IS_TEST_ADS_MODE)
        assertTrue(AdMobConfig.BANNER_AD_UNIT_ID.startsWith("ca-app-pub-3940256099942544/"))
        assertTrue(AdMobConfig.INTERSTITIAL_AD_UNIT_ID.startsWith("ca-app-pub-3940256099942544/"))
        assertTrue(AdMobConfig.REWARDED_AD_UNIT_ID.startsWith("ca-app-pub-3940256099942544/"))
    }

    @Test
    fun `file storage creates PDF Maker directories and meaningful names`() {
        val storage = FileStorageManager(context)
        storage.ensureDefaultDirectories()

        FileStorageManager.DEFAULT_FOLDERS.forEach { folder ->
            assertTrue(File(storage.rootDir, folder).exists())
        }
        val nidName = storage.suggestFileName(DocumentTemplate.NID, 0)
        assertTrue(nidName.startsWith("NID_"))
        val scanName = storage.suggestFileName(DocumentTemplate.NORMAL, 0)
        assertEquals("Scan_001", scanName)
    }

    @Test
    fun `pin hashing and verification works reliably`() {
        val hash = AppLockManager.hashPin("4829")
        assertTrue(AppLockManager.verifyPin("4829", hash))
        assertFalse(AppLockManager.verifyPin("1234", hash))
    }

    @Test
    fun `auto edge detection, perspective warp, and image enhancement produce valid bitmap`() {
        val bmp = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.DKGRAY)
        val paperPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(50f, 60f, 350f, 440f, paperPaint)

        val quad = AutoEdgeDetector.detectDocumentQuad(bmp, DocumentTemplate.NORMAL)
        assertTrue(quad.topLeft.x < quad.topRight.x)
        assertTrue(quad.topLeft.y < quad.bottomLeft.y)

        val warped = PerspectiveTransformer.warpAndTransform(bmp, quad)
        assertTrue(warped.width >= 120 && warped.height >= 120)

        val enhanced = ImageEnhancementEngine.enhance(warped, ImageFilterMode.TEXT_ENHANCE)
        assertEquals(warped.width, enhanced.width)
        bmp.recycle()
        warped.recycle()
        enhanced.recycle()
    }

    @Test
    fun `nid dual side same-page pdf generation and editor operations succeed`() = runBlocking {
        val storage = FileStorageManager(context)
        storage.ensureDefaultDirectories()

        val frontFile = storage.createTempImageFile("nid_front")
        val backFile = storage.createTempImageFile("nid_back")
        val sample = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        BitmapUtils.saveBitmapAsJpeg(sample, frontFile, 90)
        BitmapUtils.saveBitmapAsJpeg(sample, backFile, 90)
        sample.recycle()

        val frontPage = ScanPageItem("f", frontFile.absolutePath, frontFile.absolutePath, NormalizedQuad.FULL)
        val backPage = ScanPageItem("b", backFile.absolutePath, backFile.absolutePath, NormalizedQuad.FULL)
        val outPdf = storage.createUniquePdfFile("NID", "Test_NID")

        val genResult = PdfGenerator.generateNidDualSidePdf(
            frontPage = frontPage,
            backPage = backPage,
            outputFile = outPdf,
            thumbnailOutputFile = null,
            layout = NidLayoutOptions(samePage = true, pageSize = PageSizeOption.A4, orientation = PageOrientation.PORTRAIT)
        )
        assertTrue(genResult.isSuccess)
        assertEquals(1, genResult.getOrNull()?.pageCount)
        assertTrue(outPdf.exists() && outPdf.length() > 500L)

        // Add another page, add page numbers, password protect, and verify unlock
        val added = PdfEditorEngine.addImagePage(outPdf, frontFile.absolutePath)
        assertTrue(added.isSuccess)
        assertEquals(2, added.getOrNull())

        val numRes = PdfEditorEngine.addPageNumbers(outPdf)
        assertTrue(numRes.isSuccess)

        val lockRes = PdfEditorEngine.setPassword(outPdf, "secret123")
        assertTrue(lockRes.isSuccess)
        assertTrue(PdfEditorEngine.verifyPdfPassword(outPdf, "secret123"))
        assertFalse(PdfEditorEngine.verifyPdfPassword(outPdf, "wrong"))

        val unlockRes = PdfEditorEngine.removePassword(outPdf, "secret123")
        assertTrue(unlockRes.isSuccess)
    }

    @Test
    fun `pdf generation from pages supports custom sizes`() = runBlocking {
        val storage = FileStorageManager(context)
        val img = storage.createTempImageFile("custom_page")
        val sample = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        BitmapUtils.saveBitmapAsJpeg(sample, img, 90)
        sample.recycle()

        val outPdf = storage.createUniquePdfFile("Images", "Test_Multi")
        val res = PdfGenerator.generateFromPages(
            pages = listOf(
                ScanPageItem("1", img.absolutePath, img.absolutePath),
                ScanPageItem("2", img.absolutePath, img.absolutePath)
            ),
            outputFile = outPdf,
            thumbnailOutputFile = null,
            options = PdfExportOptions("Test_Multi", "Images", PageSizeOption.A5, PageOrientation.PORTRAIT)
        )
        assertTrue(res.isSuccess)
        assertEquals(2, res.getOrNull()?.pageCount)
    }
}
