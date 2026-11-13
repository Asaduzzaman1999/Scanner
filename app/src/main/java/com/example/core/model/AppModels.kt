package com.example.core.model

import kotlinx.serialization.Serializable

/**
 * Supported document scanning templates with tailored aspect ratios,
 * default PDF page sizes, folder routing, and filename prefixes.
 */
@Serializable
enum class DocumentTemplate(
    val id: String,
    val aspectRatio: Float, // width / height
    val defaultPageSize: PageSizeOption,
    val defaultOrientation: PageOrientation,
    val defaultFolder: String,
    val filePrefix: String,
    val defaultFilter: ImageFilterMode,
    val isDualSide: Boolean = false
) {
    NORMAL(
        id = "normal",
        aspectRatio = 0.707f,
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Scanned Documents",
        filePrefix = "Scan",
        defaultFilter = ImageFilterMode.COLOR_ENHANCE
    ),
    NID(
        id = "nid",
        aspectRatio = 1.586f, // ISO/IEC 7810 ID-1 card
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "NID",
        filePrefix = "NID",
        defaultFilter = ImageFilterMode.COLOR_ENHANCE,
        isDualSide = true
    ),
    PASSPORT(
        id = "passport",
        aspectRatio = 1.42f,
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Passport",
        filePrefix = "Passport",
        defaultFilter = ImageFilterMode.COLOR_ENHANCE
    ),
    PASSPORT_PHOTO(
        id = "passport_photo",
        aspectRatio = 0.778f, // 35x45mm
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Passport",
        filePrefix = "PassportPhoto",
        defaultFilter = ImageFilterMode.ORIGINAL
    ),
    DRIVING_LICENSE(
        id = "driving_license",
        aspectRatio = 1.586f,
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Scanned Documents",
        filePrefix = "DrivingLicense",
        defaultFilter = ImageFilterMode.COLOR_ENHANCE,
        isDualSide = true
    ),
    STUDENT_ID(
        id = "student_id",
        aspectRatio = 1.586f,
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Certificates",
        filePrefix = "StudentID",
        defaultFilter = ImageFilterMode.COLOR_ENHANCE,
        isDualSide = true
    ),
    BIRTH_CERTIFICATE(
        id = "birth_certificate",
        aspectRatio = 0.707f,
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Certificates",
        filePrefix = "BirthCert",
        defaultFilter = ImageFilterMode.TEXT_ENHANCE
    ),
    ACADEMIC_CERTIFICATE(
        id = "academic_certificate",
        aspectRatio = 1.414f,
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.LANDSCAPE,
        defaultFolder = "Certificates",
        filePrefix = "Certificate",
        defaultFilter = ImageFilterMode.COLOR_ENHANCE
    ),
    MARKSHEET(
        id = "marksheet",
        aspectRatio = 0.707f,
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Certificates",
        filePrefix = "Marksheet",
        defaultFilter = ImageFilterMode.TEXT_ENHANCE
    ),
    TRANSCRIPT(
        id = "transcript",
        aspectRatio = 0.707f,
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Certificates",
        filePrefix = "Transcript",
        defaultFilter = ImageFilterMode.TEXT_ENHANCE
    ),
    BUSINESS_CARD(
        id = "business_card",
        aspectRatio = 1.75f,
        defaultPageSize = PageSizeOption.A5,
        defaultOrientation = PageOrientation.LANDSCAPE,
        defaultFolder = "Scanned Documents",
        filePrefix = "BusinessCard",
        defaultFilter = ImageFilterMode.COLOR_ENHANCE
    ),
    RECEIPT(
        id = "receipt",
        aspectRatio = 0.55f,
        defaultPageSize = PageSizeOption.A5,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Receipts",
        filePrefix = "Receipt",
        defaultFilter = ImageFilterMode.BLACK_AND_WHITE
    ),
    INVOICE(
        id = "invoice",
        aspectRatio = 0.707f,
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Receipts",
        filePrefix = "Invoice",
        defaultFilter = ImageFilterMode.TEXT_ENHANCE
    ),
    LEGAL_DOCUMENT(
        id = "legal_document",
        aspectRatio = 0.607f, // 8.5 x 14
        defaultPageSize = PageSizeOption.LEGAL,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Scanned Documents",
        filePrefix = "LegalDoc",
        defaultFilter = ImageFilterMode.TEXT_ENHANCE
    ),
    A4_DOCUMENT(
        id = "a4_document",
        aspectRatio = 0.707f,
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Scanned Documents",
        filePrefix = "A4Doc",
        defaultFilter = ImageFilterMode.HIGH_QUALITY
    ),
    WHITEBOARD(
        id = "whiteboard",
        aspectRatio = 1.5f,
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.LANDSCAPE,
        defaultFolder = "Scanned Documents",
        filePrefix = "Whiteboard",
        defaultFilter = ImageFilterMode.SHADOW_REMOVAL
    ),
    NOTES(
        id = "notes",
        aspectRatio = 0.75f,
        defaultPageSize = PageSizeOption.A4,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Scanned Documents",
        filePrefix = "Notes",
        defaultFilter = ImageFilterMode.BACKGROUND_CLEANUP
    ),
    BOOK_PAGES(
        id = "book_pages",
        aspectRatio = 0.68f,
        defaultPageSize = PageSizeOption.A5,
        defaultOrientation = PageOrientation.PORTRAIT,
        defaultFolder = "Scanned Documents",
        filePrefix = "BookPage",
        defaultFilter = ImageFilterMode.TEXT_ENHANCE
    )
}

@Serializable
enum class PageSizeOption(val widthPt: Float, val heightPt: Float, val label: String) {
    A4(595f, 842f, "A4"),
    A5(420f, 595f, "A5"),
    LETTER(612f, 792f, "Letter"),
    LEGAL(612f, 1008f, "Legal"),
    CUSTOM(595f, 842f, "Custom")
}

@Serializable
enum class PageOrientation {
    PORTRAIT,
    LANDSCAPE
}

@Serializable
enum class ImageFilterMode {
    ORIGINAL,
    COLOR_ENHANCE,
    HIGH_QUALITY,
    TEXT_ENHANCE,
    SHADOW_REMOVAL,
    BACKGROUND_CLEANUP,
    GRAYSCALE,
    BLACK_AND_WHITE
}

@Serializable
enum class CompressionPreset(val jpegQuality: Int, val maxDimensionPx: Int) {
    HIGH_QUALITY(92, 2400),
    BALANCED(75, 1650),
    SMALL_SIZE(50, 1100)
}

@Serializable
enum class ExportImageFormat(val extension: String, val mimeType: String) {
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp")
}

@Serializable
enum class AppLanguage {
    EN,
    BN
}

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Serializable
enum class SortOrder {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    SIZE_DESC
}

@Serializable
data class NormalizedPoint(
    val x: Float,
    val y: Float
)

@Serializable
data class NormalizedQuad(
    val topLeft: NormalizedPoint = NormalizedPoint(0.08f, 0.08f),
    val topRight: NormalizedPoint = NormalizedPoint(0.92f, 0.08f),
    val bottomRight: NormalizedPoint = NormalizedPoint(0.92f, 0.92f),
    val bottomLeft: NormalizedPoint = NormalizedPoint(0.08f, 0.92f)
) {
    fun asList(): List<NormalizedPoint> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        val FULL = NormalizedQuad(
            NormalizedPoint(0f, 0f),
            NormalizedPoint(1f, 0f),
            NormalizedPoint(1f, 1f),
            NormalizedPoint(0f, 1f)
        )
    }
}

@Serializable
data class ScanPageItem(
    val id: String,
    val originalImagePath: String,
    val processedImagePath: String,
    val quad: NormalizedQuad = NormalizedQuad(),
    val rotationDegrees: Int = 0,
    val straightenDegrees: Float = 0f,
    val filterMode: ImageFilterMode = ImageFilterMode.COLOR_ENHANCE,
    val brightness: Float = 0f,      // -50f .. +50f
    val contrast: Float = 1.0f,      // 0.5f .. 2.0f
    val sharpness: Float = 0.25f,    // 0f .. 1.0f
    val label: String = "",
    val targetScalePercent: Int = 100
)

@Serializable
data class NidLayoutOptions(
    val samePage: Boolean = true,
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val marginDp: Float = 24f,
    val centerAlign: Boolean = true,
    val autoResizeUniform: Boolean = true,
    val highQuality: Boolean = true,
    val drawGuideBorder: Boolean = true
)

@Serializable
data class PdfExportOptions(
    val fileName: String,
    val folderName: String = "Scanned Documents",
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val marginPt: Float = 24f,
    val compression: CompressionPreset = CompressionPreset.HIGH_QUALITY,
    val customWidthPt: Float = 595f,
    val customHeightPt: Float = 842f,
    val password: String? = null
)

/**
 * Future-extensible backup adapter interface.
 * v1 operates 100% offline with local storage; future releases can bind
 * Google Drive, OneDrive, or Dropbox implementations here.
 */
interface BackupStorageProvider {
    val providerName: String
    suspend fun isConnected(): Boolean
    suspend fun uploadDocument(localPath: String, remoteFolder: String): Result<String>
    suspend fun restoreDocument(remoteId: String, destinationPath: String): Result<Unit>
}
