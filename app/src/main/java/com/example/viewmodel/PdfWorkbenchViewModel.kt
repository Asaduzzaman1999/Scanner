package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DocScanApplication
import com.example.core.model.CompressionPreset
import com.example.core.model.ExportImageFormat
import com.example.core.model.ImageFilterMode
import com.example.core.model.NormalizedQuad
import com.example.core.model.PageOrientation
import com.example.core.model.PageSizeOption
import com.example.core.model.PdfExportOptions
import com.example.core.model.ScanPageItem
import com.example.data.local.DocumentEntity
import com.example.processing.pdf.DrawPathSpec
import com.example.processing.pdf.ExportedImagesResult
import com.example.processing.pdf.ImageOverlaySpec
import com.example.processing.pdf.PdfEditorEngine
import com.example.processing.pdf.PdfGenerator
import com.example.processing.pdf.PdfToImageConverter
import com.example.processing.pdf.TextOverlaySpec
import com.example.processing.vision.AutoEdgeDetector
import com.example.processing.vision.BitmapUtils
import com.example.processing.vision.ImageEnhancementEngine
import com.example.processing.vision.OcrEngine
import com.example.processing.vision.PerspectiveTransformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class PdfWorkbenchState(
    val document: DocumentEntity? = null,
    val pageCount: Int = 0,
    val currentPageIndex: Int = 0,
    val pagePreviewBitmap: Bitmap? = null,
    val thumbnails: List<Bitmap?> = emptyList(),
    val isPasswordLocked: Boolean = false,
    val activePassword: String? = null,
    val isBusy: Boolean = false,
    val progress: Float = 0f,
    val message: String? = null,
    val error: String? = null,
    val ocrText: String? = null,
    // Pending annotations on current page (with Undo/Redo)
    val pendingStrokes: List<DrawPathSpec> = emptyList(),
    val pendingTexts: List<TextOverlaySpec> = emptyList(),
    val pendingImages: List<ImageOverlaySpec> = emptyList(),
    val canUndoOverlay: Boolean = false,
    val canRedoOverlay: Boolean = false,
    // Images to PDF workbench
    val galleryImages: List<ScanPageItem> = emptyList(),
    // PDF to Images workbench
    val exportedImagesResult: ExportedImagesResult? = null
)

private data class OverlaySnapshot(
    val strokes: List<DrawPathSpec>,
    val texts: List<TextOverlaySpec>,
    val images: List<ImageOverlaySpec>
)

class PdfWorkbenchViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DocScanApplication
    private val repository = app.repository
    private val fileStorage = app.fileStorage

    private val _state = MutableStateFlow(PdfWorkbenchState())
    val state: StateFlow<PdfWorkbenchState> = _state.asStateFlow()

    private val overlayUndo = ArrayDeque<OverlaySnapshot>()
    private val overlayRedo = ArrayDeque<OverlaySnapshot>()

    fun loadDocument(docId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null, ocrText = null) }
            val doc = repository.getDocumentById(docId)
            if (doc == null || !File(doc.filePath).exists()) {
                _state.update { it.copy(isBusy = false, error = "Document file not found.") }
                return@launch
            }
            repository.markOpened(doc)
            val file = File(doc.filePath)
            val locked = PdfEditorEngine.isPasswordProtected(file)
            if (locked) {
                _state.update {
                    it.copy(
                        document = doc,
                        isPasswordLocked = true,
                        isBusy = false,
                        pageCount = doc.pageCount
                    )
                }
                return@launch
            }
            refreshDocumentViews(doc, 0, null)
        }
    }

    fun unlockPdfWithPassword(password: String) {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            val file = File(doc.filePath)
            val valid = PdfEditorEngine.verifyPdfPassword(file, password)
            if (!valid) {
                _state.update { it.copy(isBusy = false, error = "Incorrect PDF password.") }
            } else {
                // Create decrypted working copy in temp for PdfRenderer preview if needed
                _state.update { it.copy(isPasswordLocked = false, activePassword = password) }
                refreshDocumentViews(doc, 0, password)
            }
        }
    }

    private suspend fun refreshDocumentViews(doc: DocumentEntity, targetPage: Int, password: String?) {
        val file = File(doc.filePath)
        val count = PdfEditorEngine.getPageCount(file)
        val safeIdx = targetPage.coerceIn(0, (count - 1).coerceAtLeast(0))
        val preview = PdfEditorEngine.renderPageBitmap(file, safeIdx, 1080)
        val thumbs = (0 until count).map { idx ->
            PdfEditorEngine.renderPageBitmap(file, idx, 220)
        }
        val updatedDoc = doc.copy(pageCount = count, fileSizeBytes = file.length())
        repository.updateDocumentMetadata(updatedDoc)
        _state.update {
            it.copy(
                document = updatedDoc,
                pageCount = count,
                currentPageIndex = safeIdx,
                pagePreviewBitmap = preview,
                thumbnails = thumbs,
                isBusy = false,
                activePassword = password
            )
        }
    }

    fun selectPage(index: Int) {
        val doc = _state.value.document ?: return
        if (index !in 0 until _state.value.pageCount) return
        viewModelScope.launch {
            clearPendingOverlays()
            val bmp = PdfEditorEngine.renderPageBitmap(File(doc.filePath), index, 1080)
            _state.update { it.copy(currentPageIndex = index, pagePreviewBitmap = bmp) }
        }
    }

    fun rotateCurrentPage(deltaDegrees: Int = 90) {
        val doc = _state.value.document ?: return
        val idx = _state.value.currentPageIndex
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            PdfEditorEngine.rotatePage(File(doc.filePath), idx, deltaDegrees, _state.value.activePassword)
                .onSuccess { refreshDocumentViews(doc, idx, _state.value.activePassword) }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun deleteCurrentPage() {
        val doc = _state.value.document ?: return
        val idx = _state.value.currentPageIndex
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            PdfEditorEngine.deletePage(File(doc.filePath), idx, _state.value.activePassword)
                .onSuccess { newCount ->
                    val nextIdx = idx.coerceAtMost((newCount - 1).coerceAtLeast(0))
                    refreshDocumentViews(doc, nextIdx, _state.value.activePassword)
                }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val doc = _state.value.document ?: return
        val count = _state.value.pageCount
        if (fromIndex !in 0 until count || toIndex !in 0 until count || fromIndex == toIndex) return
        val order = (0 until count).toMutableList()
        val moved = order.removeAt(fromIndex)
        order.add(toIndex, moved)
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            PdfEditorEngine.reorderPages(File(doc.filePath), order, _state.value.activePassword)
                .onSuccess { refreshDocumentViews(doc, toIndex, _state.value.activePassword) }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun cropCurrentPage(quad: NormalizedQuad) {
        val doc = _state.value.document ?: return
        val idx = _state.value.currentPageIndex
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            PdfEditorEngine.cropPage(File(doc.filePath), idx, quad, _state.value.activePassword)
                .onSuccess { refreshDocumentViews(doc, idx, _state.value.activePassword) }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun addImagePageFromUri(uri: Uri) {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val temp = fileStorage.createTempImageFile("add_pdf_page")
            val copied = withContext(Dispatchers.IO) { BitmapUtils.copyUriToFile(app, uri, temp) }
            if (!copied) {
                _state.update { it.copy(isBusy = false, error = "Unable to read image") }
                return@launch
            }
            PdfEditorEngine.addImagePage(File(doc.filePath), temp.absolutePath, null, _state.value.activePassword)
                .onSuccess { newCount -> refreshDocumentViews(doc, newCount - 1, _state.value.activePassword) }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun replaceCurrentPageWithUri(uri: Uri) {
        val doc = _state.value.document ?: return
        val idx = _state.value.currentPageIndex
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val temp = fileStorage.createTempImageFile("replace_pdf_page")
            val copied = withContext(Dispatchers.IO) { BitmapUtils.copyUriToFile(app, uri, temp) }
            if (!copied) {
                _state.update { it.copy(isBusy = false, error = "Unable to read image") }
                return@launch
            }
            PdfEditorEngine.replacePageWithImage(File(doc.filePath), idx, temp.absolutePath, _state.value.activePassword)
                .onSuccess { refreshDocumentViews(doc, idx, _state.value.activePassword) }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    // ---- Canvas Overlays (Draw, Highlight, Text, Signature, Image) with Undo/Redo ----

    fun addStroke(stroke: DrawPathSpec) {
        pushOverlayUndo()
        _state.update {
            it.copy(
                pendingStrokes = it.pendingStrokes + stroke,
                canUndoOverlay = overlayUndo.isNotEmpty(),
                canRedoOverlay = overlayRedo.isNotEmpty()
            )
        }
    }

    fun addTextOverlay(spec: TextOverlaySpec) {
        pushOverlayUndo()
        _state.update {
            it.copy(
                pendingTexts = it.pendingTexts + spec,
                canUndoOverlay = overlayUndo.isNotEmpty(),
                canRedoOverlay = overlayRedo.isNotEmpty()
            )
        }
    }

    fun addSignatureBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val file = fileStorage.createTempImageFile("sig")
            withContext(Dispatchers.IO) {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            pushOverlayUndo()
            val spec = ImageOverlaySpec(imagePath = file.absolutePath, normX = 0.55f, normY = 0.78f, normWidth = 0.30f)
            _state.update {
                it.copy(
                    pendingImages = it.pendingImages + spec,
                    canUndoOverlay = overlayUndo.isNotEmpty(),
                    canRedoOverlay = overlayRedo.isNotEmpty()
                )
            }
        }
    }

    fun addImageOverlayFromUri(uri: Uri) {
        viewModelScope.launch {
            val file = fileStorage.createTempImageFile("img_overlay")
            val ok = withContext(Dispatchers.IO) { BitmapUtils.copyUriToFile(app, uri, file) }
            if (ok) {
                pushOverlayUndo()
                val spec = ImageOverlaySpec(imagePath = file.absolutePath, normX = 0.35f, normY = 0.45f, normWidth = 0.32f)
                _state.update {
                    it.copy(
                        pendingImages = it.pendingImages + spec,
                        canUndoOverlay = overlayUndo.isNotEmpty(),
                        canRedoOverlay = overlayRedo.isNotEmpty()
                    )
                }
            }
        }
    }

    fun undoOverlay() {
        val prev = overlayUndo.removeLastOrNull() ?: return
        overlayRedo.addLast(OverlaySnapshot(_state.value.pendingStrokes, _state.value.pendingTexts, _state.value.pendingImages))
        _state.update {
            it.copy(
                pendingStrokes = prev.strokes,
                pendingTexts = prev.texts,
                pendingImages = prev.images,
                canUndoOverlay = overlayUndo.isNotEmpty(),
                canRedoOverlay = overlayRedo.isNotEmpty()
            )
        }
    }

    fun redoOverlay() {
        val next = overlayRedo.removeLastOrNull() ?: return
        overlayUndo.addLast(OverlaySnapshot(_state.value.pendingStrokes, _state.value.pendingTexts, _state.value.pendingImages))
        _state.update {
            it.copy(
                pendingStrokes = next.strokes,
                pendingTexts = next.texts,
                pendingImages = next.images,
                canUndoOverlay = overlayUndo.isNotEmpty(),
                canRedoOverlay = overlayRedo.isNotEmpty()
            )
        }
    }

    fun commitOverlaysToPage() {
        val doc = _state.value.document ?: return
        val s = _state.value
        if (s.pendingStrokes.isEmpty() && s.pendingTexts.isEmpty() && s.pendingImages.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            PdfEditorEngine.applyPageOverlays(
                pdfFile = File(doc.filePath),
                pageIndex = s.currentPageIndex,
                strokes = s.pendingStrokes,
                texts = s.pendingTexts,
                images = s.pendingImages,
                password = s.activePassword
            ).onSuccess {
                clearPendingOverlays()
                refreshDocumentViews(doc, s.currentPageIndex, s.activePassword)
                _state.update { it.copy(message = "Edits saved to PDF") }
            }.onFailure { e ->
                _state.update { it.copy(isBusy = false, error = e.message) }
            }
        }
    }

    fun addDateStamp() {
        val doc = _state.value.document ?: return
        val idx = _state.value.currentPageIndex
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            PdfEditorEngine.addDateStampToPage(File(doc.filePath), idx, _state.value.activePassword)
                .onSuccess { refreshDocumentViews(doc, idx, _state.value.activePassword) }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun addPageNumbers() {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            PdfEditorEngine.addPageNumbers(File(doc.filePath), _state.value.activePassword)
                .onSuccess { refreshDocumentViews(doc, _state.value.currentPageIndex, _state.value.activePassword) }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun addWatermark(text: String) {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            PdfEditorEngine.addWatermark(File(doc.filePath), text, _state.value.activePassword)
                .onSuccess { refreshDocumentViews(doc, _state.value.currentPageIndex, _state.value.activePassword) }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun compressCurrentPdf(preset: CompressionPreset) {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, progress = 0f) }
            val file = File(doc.filePath)
            val beforeKb = file.length() / 1024
            PdfEditorEngine.compressPdf(file, file, preset) { p ->
                _state.update { it.copy(progress = p) }
            }.onSuccess { newSize ->
                val afterKb = newSize / 1024
                refreshDocumentViews(doc, _state.value.currentPageIndex, _state.value.activePassword)
                _state.update { it.copy(message = "Compressed: ${beforeKb}KB -> ${afterKb}KB") }
            }.onFailure { e ->
                _state.update { it.copy(isBusy = false, error = e.message) }
            }
        }
    }

    fun splitCurrentPdf(fromPage: Int, toPage: Int, newTitle: String, onSplitCreated: (Long) -> Unit) {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val outFile = fileStorage.createUniquePdfFile(doc.folderName, newTitle)
            PdfEditorEngine.splitPdf(File(doc.filePath), fromPage, toPage, outFile, _state.value.activePassword)
                .onSuccess { count ->
                    val thumb = PdfGenerator.renderFirstPageThumbnail(
                        outFile,
                        fileStorage.createThumbnailFile(System.currentTimeMillis())
                    )
                    val newId = repository.saveDocumentRecord(
                        DocumentEntity(
                            title = outFile.nameWithoutExtension,
                            filePath = outFile.absolutePath,
                            thumbnailPath = thumb?.absolutePath,
                            folderName = doc.folderName,
                            templateId = doc.templateId,
                            pageCount = count,
                            fileSizeBytes = outFile.length()
                        )
                    )
                    _state.update { it.copy(isBusy = false, message = "Split PDF saved") }
                    onSplitCreated(newId)
                }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun mergePdfs(docs: List<DocumentEntity>, outputName: String, onMerged: (Long) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val files = docs.map { File(it.filePath) }.filter { it.exists() }
            val outFile = fileStorage.createUniquePdfFile("Scanned Documents", outputName)
            PdfEditorEngine.mergePdfs(files, outFile)
                .onSuccess { pageCount ->
                    val thumb = PdfGenerator.renderFirstPageThumbnail(
                        outFile,
                        fileStorage.createThumbnailFile(System.currentTimeMillis())
                    )
                    val newId = repository.saveDocumentRecord(
                        DocumentEntity(
                            title = outFile.nameWithoutExtension,
                            filePath = outFile.absolutePath,
                            thumbnailPath = thumb?.absolutePath,
                            folderName = "Scanned Documents",
                            pageCount = pageCount,
                            fileSizeBytes = outFile.length()
                        )
                    )
                    _state.update { it.copy(isBusy = false, message = "Merged PDF created") }
                    onMerged(newId)
                }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun setPasswordProtection(newPassword: String) {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            PdfEditorEngine.setPassword(File(doc.filePath), newPassword, _state.value.activePassword)
                .onSuccess {
                    val updated = doc.copy(isPasswordProtected = true)
                    repository.updateDocumentMetadata(updated)
                    _state.update { it.copy(document = updated, activePassword = newPassword, isBusy = false, message = "PDF password protected") }
                }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun removePasswordProtection(currentPassword: String) {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            PdfEditorEngine.removePassword(File(doc.filePath), currentPassword)
                .onSuccess {
                    val updated = doc.copy(isPasswordProtected = false)
                    repository.updateDocumentMetadata(updated)
                    refreshDocumentViews(updated, _state.value.currentPageIndex, null)
                    _state.update { it.copy(message = "PDF password removed") }
                }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = e.message) } }
        }
    }

    fun runOcrOnCurrentPdf() {
        val doc = _state.value.document ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, progress = 0f) }
            OcrEngine.extractTextFromPdf(app, File(doc.filePath)) { p ->
                _state.update { it.copy(progress = p) }
            }.onSuccess { result ->
                val updated = doc.copy(ocrTextIndex = result.fullText.take(4000))
                repository.updateDocumentMetadata(updated)
                _state.update { it.copy(document = updated, isBusy = false, ocrText = result.fullText) }
            }.onFailure { e ->
                _state.update { it.copy(isBusy = false, error = e.message) }
            }
        }
    }

    fun exportOcrTextFile(fileName: String, content: String): File? {
        val dir = fileStorage.getFolderDir("Scanned Documents")
        val file = File(dir, "${fileName.removeSuffix(".txt")}.txt")
        return if (OcrEngine.saveTextToFile(file, content)) file else null
    }

    // ---- Images to PDF ----

    fun addGalleryImages(uris: List<Uri>) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val id = UUID.randomUUID().toString()
                    val raw = fileStorage.createTempImageFile("img2pdf_raw_$id")
                    if (!BitmapUtils.copyUriToFile(app, uri, raw)) return@mapNotNull null
                    val bmp = BitmapUtils.decodeSampledBitmap(raw.absolutePath, 1800) ?: return@mapNotNull null
                    val proc = fileStorage.createTempImageFile("img2pdf_proc_$id")
                    BitmapUtils.saveBitmapAsJpeg(bmp, proc, 90)
                    val quad = AutoEdgeDetector.detectDocumentQuad(bmp)
                    bmp.recycle()
                    ScanPageItem(
                        id = id,
                        originalImagePath = raw.absolutePath,
                        processedImagePath = proc.absolutePath,
                        quad = quad,
                        filterMode = ImageFilterMode.ORIGINAL
                    )
                }
            }
            _state.update { it.copy(galleryImages = it.galleryImages + added, isBusy = false) }
        }
    }

    fun rotateGalleryImage(index: Int) {
        val list = _state.value.galleryImages
        val item = list.getOrNull(index) ?: return
        viewModelScope.launch {
            val nextRot = (item.rotationDegrees + 90) % 360
            val updated = item.copy(rotationDegrees = nextRot)
            reprocessGalleryItem(index, updated)
        }
    }

    fun enhanceGalleryImage(index: Int, filter: ImageFilterMode) {
        val item = _state.value.galleryImages.getOrNull(index) ?: return
        viewModelScope.launch {
            reprocessGalleryItem(index, item.copy(filterMode = filter))
        }
    }

    fun cropGalleryImageAuto(index: Int) {
        val item = _state.value.galleryImages.getOrNull(index) ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val croppedItem = withContext(Dispatchers.IO) {
                val bmp = BitmapUtils.decodeSampledBitmap(item.originalImagePath, 1800) ?: return@withContext item
                val quad = AutoEdgeDetector.detectDocumentQuad(bmp)
                val warped = PerspectiveTransformer.warpAndTransform(bmp, quad, item.rotationDegrees)
                bmp.recycle()
                val proc = File(item.processedImagePath)
                BitmapUtils.saveBitmapAsJpeg(warped, proc, 90)
                warped.recycle()
                item.copy(quad = quad)
            }
            val list = _state.value.galleryImages.toMutableList().apply { set(index, croppedItem) }
            _state.update { it.copy(galleryImages = list, isBusy = false) }
        }
    }

    fun moveGalleryImage(from: Int, to: Int) {
        val list = _state.value.galleryImages.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        _state.update { it.copy(galleryImages = list) }
    }

    fun removeGalleryImage(index: Int) {
        val list = _state.value.galleryImages.toMutableList()
        if (index in list.indices) list.removeAt(index)
        _state.update { it.copy(galleryImages = list) }
    }

    fun generatePdfFromGalleryImages(
        fileName: String,
        pageSize: PageSizeOption,
        orientation: PageOrientation,
        marginPt: Float,
        compression: CompressionPreset,
        customW: Float = 595f,
        customH: Float = 842f,
        onCreated: (Long) -> Unit
    ) {
        val items = _state.value.galleryImages
        if (items.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, progress = 0f) }
            val outFile = fileStorage.createUniquePdfFile("Images", fileName)
            val thumbFile = fileStorage.createThumbnailFile(System.currentTimeMillis())
            PdfGenerator.generateFromPages(
                pages = items,
                outputFile = outFile,
                thumbnailOutputFile = thumbFile,
                options = PdfExportOptions(
                    fileName = fileName,
                    folderName = "Images",
                    pageSize = pageSize,
                    orientation = orientation,
                    marginPt = marginPt,
                    compression = compression,
                    customWidthPt = customW,
                    customHeightPt = customH
                ),
                onProgress = { p -> _state.update { it.copy(progress = p) } }
            ).onSuccess { res ->
                val docId = repository.saveDocumentRecord(
                    DocumentEntity(
                        title = res.pdfFile.nameWithoutExtension,
                        filePath = res.pdfFile.absolutePath,
                        thumbnailPath = res.thumbnailFile?.absolutePath,
                        folderName = "Images",
                        templateId = "images",
                        pageCount = res.pageCount,
                        fileSizeBytes = res.fileSizeBytes
                    )
                )
                _state.update { it.copy(isBusy = false, galleryImages = emptyList()) }
                onCreated(docId)
            }.onFailure { e ->
                _state.update { it.copy(isBusy = false, error = e.message) }
            }
        }
    }

    // ---- PDF to Images ----

    fun exportPdfToImages(
        pdfFile: File,
        selectedIndices: List<Int>,
        format: ExportImageFormat,
        dpi: Int,
        quality: Int,
        saveToGallery: Boolean
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, progress = 0f, exportedImagesResult = null) }
            val outDir = File(fileStorage.getFolderDir("Images"), pdfFile.nameWithoutExtension)
            PdfToImageConverter.convertPdfToImages(
                context = app,
                pdfFile = pdfFile,
                pageIndicesZeroBased = selectedIndices,
                format = format,
                dpi = dpi,
                quality = quality,
                saveToGallery = saveToGallery,
                outputDir = outDir,
                onProgress = { p -> _state.update { it.copy(progress = p) } }
            ).onSuccess { res ->
                _state.update {
                    it.copy(
                        isBusy = false,
                        exportedImagesResult = res,
                        message = "Exported ${res.exportedFiles.size} image(s)"
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(isBusy = false, error = e.message) }
            }
        }
    }

    fun clearMessageAndError() {
        _state.update { it.copy(message = null, error = null) }
    }

    fun clearOcrText() {
        _state.update { it.copy(ocrText = null) }
    }

    private suspend fun reprocessGalleryItem(index: Int, updated: ScanPageItem) {
        _state.update { it.copy(isBusy = true) }
        withContext(Dispatchers.IO) {
            val raw = BitmapUtils.decodeSampledBitmap(updated.originalImagePath, 1800) ?: return@withContext
            val warped = PerspectiveTransformer.warpAndTransform(raw, NormalizedQuad.FULL, updated.rotationDegrees)
            raw.recycle()
            val enhanced = ImageEnhancementEngine.enhance(warped, updated.filterMode)
            if (enhanced !== warped) warped.recycle()
            BitmapUtils.saveBitmapAsJpeg(enhanced, File(updated.processedImagePath), 90)
            enhanced.recycle()
        }
        val list = _state.value.galleryImages.toMutableList()
        if (index in list.indices) list[index] = updated
        _state.update { it.copy(galleryImages = list, isBusy = false) }
    }

    private fun pushOverlayUndo() {
        overlayUndo.addLast(OverlaySnapshot(_state.value.pendingStrokes, _state.value.pendingTexts, _state.value.pendingImages))
        if (overlayUndo.size > 20) overlayUndo.removeFirst()
        overlayRedo.clear()
    }

    private fun clearPendingOverlays() {
        overlayUndo.clear()
        overlayRedo.clear()
        _state.update {
            it.copy(
                pendingStrokes = emptyList(),
                pendingTexts = emptyList(),
                pendingImages = emptyList(),
                canUndoOverlay = false,
                canRedoOverlay = false
            )
        }
    }
}
