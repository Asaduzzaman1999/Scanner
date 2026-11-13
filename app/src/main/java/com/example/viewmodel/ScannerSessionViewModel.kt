package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DocScanApplication
import com.example.core.model.CompressionPreset
import com.example.core.model.DocumentTemplate
import com.example.core.model.ImageFilterMode
import com.example.core.model.NidLayoutOptions
import com.example.core.model.NormalizedQuad
import com.example.core.model.PageOrientation
import com.example.core.model.PageSizeOption
import com.example.core.model.PdfExportOptions
import com.example.core.model.ScanPageItem
import com.example.data.local.DocumentEntity
import com.example.processing.pdf.PdfGenerator
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

data class ScannerUiState(
    val template: DocumentTemplate = DocumentTemplate.NORMAL,
    val isBatchMode: Boolean = false,
    val pages: List<ScanPageItem> = emptyList(),
    val activePageIndex: Int = 0,
    val replaceTargetIndex: Int? = null, // When retaking/replacing a specific page
    val nidStep: Int = 0, // 0 = Front, 1 = Back, 2 = Review
    val nidLayout: NidLayoutOptions = NidLayoutOptions(),
    val isProcessing: Boolean = false,
    val exportProgress: Float = 0f,
    val errorMessage: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val ocrResult: String? = null,
    val isOcrRunning: Boolean = false
) {
    val activePage: ScanPageItem?
        get() = pages.getOrNull(activePageIndex)
}

class ScannerSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DocScanApplication
    private val repository = app.repository
    private val fileStorage = app.fileStorage

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<List<ScanPageItem>>()
    private val redoStack = ArrayDeque<List<ScanPageItem>>()

    fun startNewSession(template: DocumentTemplate, isBatch: Boolean) {
        undoStack.clear()
        redoStack.clear()
        _state.value = ScannerUiState(
            template = template,
            isBatchMode = isBatch,
            nidStep = 0
        )
    }

    fun restoreDraft(template: DocumentTemplate, pages: List<ScanPageItem>) {
        undoStack.clear()
        redoStack.clear()
        _state.value = ScannerUiState(
            template = template,
            isBatchMode = pages.size > 1 || template == DocumentTemplate.NORMAL,
            pages = pages,
            activePageIndex = (pages.size - 1).coerceAtLeast(0),
            nidStep = if (template.isDualSide && pages.size >= 2) 2 else pages.size
        )
    }

    fun setTemplate(template: DocumentTemplate) {
        _state.update { it.copy(template = template) }
    }

    fun prepareRetakeForPage(index: Int) {
        _state.update { it.copy(replaceTargetIndex = index, activePageIndex = index) }
    }

    fun prepareAddNewPage() {
        _state.update { it.copy(replaceTargetIndex = null) }
    }

    fun addCapturedImageFile(rawImageFile: File, onAdded: (Int) -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, errorMessage = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = BitmapUtils.decodeSampledBitmap(rawImageFile.absolutePath, maxDimension = 1800)
                        ?: error("Invalid or unreadable image.")
                    try {
                        val template = _state.value.template
                        val quad = AutoEdgeDetector.detectDocumentQuad(bmp, template)
                        val pageId = UUID.randomUUID().toString()
                        val permanentRaw = fileStorage.createDraftPageFile("${pageId}_orig")
                        BitmapUtils.saveBitmapAsJpeg(bmp, permanentRaw, quality = 92)

                        val processedFile = fileStorage.createDraftPageFile("${pageId}_proc")
                        val warped = PerspectiveTransformer.warpAndTransform(bmp, quad)
                        val enhanced = ImageEnhancementEngine.enhance(warped, template.defaultFilter)
                        if (warped !== enhanced) warped.recycle()
                        BitmapUtils.saveBitmapAsJpeg(enhanced, processedFile, quality = 90)
                        enhanced.recycle()

                        val label = if (template.isDualSide) {
                            val targetIdx = _state.value.replaceTargetIndex ?: _state.value.pages.size
                            if (targetIdx == 0) "Front" else "Back"
                        } else ""

                        ScanPageItem(
                            id = pageId,
                            originalImagePath = permanentRaw.absolutePath,
                            processedImagePath = processedFile.absolutePath,
                            quad = quad,
                            filterMode = template.defaultFilter,
                            label = label
                        )
                    } finally {
                        bmp.recycle()
                    }
                }
            }

            result.onSuccess { newPage ->
                pushUndoSnapshot()
                val current = _state.value
                val replaceIdx = current.replaceTargetIndex
                val updatedPages = if (replaceIdx != null && replaceIdx in current.pages.indices) {
                    current.pages.toMutableList().apply { set(replaceIdx, newPage) }
                } else {
                    current.pages + newPage
                }
                val newIdx = if (replaceIdx != null && replaceIdx in updatedPages.indices) replaceIdx else updatedPages.lastIndex
                val nextNidStep = if (current.template.isDualSide) {
                    if (updatedPages.size >= 2) 2 else 1
                } else current.nidStep

                _state.update {
                    it.copy(
                        pages = updatedPages,
                        activePageIndex = newIdx,
                        replaceTargetIndex = null,
                        nidStep = nextNidStep,
                        isProcessing = false,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty()
                    )
                }
                repository.saveScanDraft(current.template, updatedPages)
                onAdded(newIdx)
            }.onFailure { err ->
                _state.update { it.copy(isProcessing = false, errorMessage = err.message) }
            }
        }
    }

    fun importFromGalleryUri(uri: Uri, onImported: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val tempFile = fileStorage.createTempImageFile("gallery")
            val copied = withContext(Dispatchers.IO) {
                BitmapUtils.copyUriToFile(app, uri, tempFile)
            }
            if (copied) {
                addCapturedImageFile(tempFile, onImported)
            } else {
                _state.update { it.copy(errorMessage = "Could not load selected image.") }
            }
        }
    }

    fun selectActivePage(index: Int) {
        _state.update {
            it.copy(activePageIndex = index.coerceIn(0, (it.pages.size - 1).coerceAtLeast(0)))
        }
    }

    fun updateActivePageQuad(quad: NormalizedQuad, reprocessNow: Boolean = true) {
        val idx = _state.value.activePageIndex
        val page = _state.value.pages.getOrNull(idx) ?: return
        pushUndoSnapshot()
        val updated = page.copy(quad = quad)
        applyPageUpdate(idx, updated, reprocessNow)
    }

    fun autoDetectActivePageCorners() {
        val idx = _state.value.activePageIndex
        val page = _state.value.pages.getOrNull(idx) ?: return
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true) }
            val quad = withContext(Dispatchers.IO) {
                val bmp = BitmapUtils.decodeSampledBitmap(page.originalImagePath, 1400)
                if (bmp != null) {
                    try {
                        AutoEdgeDetector.detectDocumentQuad(bmp, _state.value.template)
                    } finally {
                        bmp.recycle()
                    }
                } else page.quad
            }
            pushUndoSnapshot()
            applyPageUpdate(idx, page.copy(quad = quad), reprocessNow = true)
        }
    }

    fun rotateActivePage(deltaDegrees: Int = 90) {
        val idx = _state.value.activePageIndex
        val page = _state.value.pages.getOrNull(idx) ?: return
        pushUndoSnapshot()
        val updated = page.copy(rotationDegrees = (page.rotationDegrees + deltaDegrees + 360) % 360)
        applyPageUpdate(idx, updated, reprocessNow = true)
    }

    fun rotatePageAt(index: Int, deltaDegrees: Int = 90) {
        val page = _state.value.pages.getOrNull(index) ?: return
        pushUndoSnapshot()
        val updated = page.copy(rotationDegrees = (page.rotationDegrees + deltaDegrees + 360) % 360)
        applyPageUpdate(index, updated, reprocessNow = true)
    }

    fun updateEditorAdjustments(
        filterMode: ImageFilterMode? = null,
        brightness: Float? = null,
        contrast: Float? = null,
        sharpness: Float? = null,
        straightenDegrees: Float? = null,
        targetScalePercent: Int? = null
    ) {
        val idx = _state.value.activePageIndex
        val page = _state.value.pages.getOrNull(idx) ?: return
        pushUndoSnapshot()
        val updated = page.copy(
            filterMode = filterMode ?: page.filterMode,
            brightness = brightness ?: page.brightness,
            contrast = contrast ?: page.contrast,
            sharpness = sharpness ?: page.sharpness,
            straightenDegrees = straightenDegrees ?: page.straightenDegrees,
            targetScalePercent = targetScalePercent ?: page.targetScalePercent
        )
        applyPageUpdate(idx, updated, reprocessNow = true)
    }

    fun deletePageAt(index: Int) {
        val current = _state.value
        if (index !in current.pages.indices) return
        pushUndoSnapshot()
        val updated = current.pages.toMutableList().apply { removeAt(index) }
        val newActive = current.activePageIndex.coerceAtMost((updated.size - 1).coerceAtLeast(0))
        _state.update {
            it.copy(
                pages = updated,
                activePageIndex = newActive,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        viewModelScope.launch { repository.saveScanDraft(current.template, updated) }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val current = _state.value
        if (fromIndex !in current.pages.indices || toIndex !in current.pages.indices || fromIndex == toIndex) return
        pushUndoSnapshot()
        val list = current.pages.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _state.update {
            it.copy(
                pages = list,
                activePageIndex = toIndex,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        viewModelScope.launch { repository.saveScanDraft(current.template, list) }
    }

    fun updateNidLayout(options: NidLayoutOptions) {
        _state.update { it.copy(nidLayout = options) }
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_state.value.pages)
        _state.update {
            it.copy(
                pages = previous,
                activePageIndex = it.activePageIndex.coerceIn(0, (previous.size - 1).coerceAtLeast(0)),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        _state.value.activePage?.let { reprocessPageFile(it) }
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_state.value.pages)
        _state.update {
            it.copy(
                pages = next,
                activePageIndex = it.activePageIndex.coerceIn(0, (next.size - 1).coerceAtLeast(0)),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        _state.value.activePage?.let { reprocessPageFile(it) }
    }

    fun runOcrOnActivePage() {
        val page = _state.value.activePage ?: return
        viewModelScope.launch {
            _state.update { it.copy(isOcrRunning = true, ocrResult = null) }
            val res = OcrEngine.extractTextFromImageFile(page.processedImagePath)
            _state.update {
                it.copy(
                    isOcrRunning = false,
                    ocrResult = res.getOrElse { err -> "OCR Error: ${err.message}" }
                )
            }
        }
    }

    fun clearOcrResult() {
        _state.update { it.copy(ocrResult = null) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun suggestDefaultFileName(existingDocCount: Int): String {
        return fileStorage.suggestFileName(_state.value.template, existingDocCount)
    }

    fun generateAndSavePdf(
        fileName: String,
        folderName: String = _state.value.template.defaultFolder,
        pageSize: PageSizeOption = _state.value.template.defaultPageSize,
        orientation: PageOrientation = _state.value.template.defaultOrientation,
        compression: CompressionPreset = CompressionPreset.HIGH_QUALITY,
        password: String? = null,
        onSaved: (Long) -> Unit
    ) {
        val snapshot = _state.value
        if (snapshot.pages.isEmpty()) return

        viewModelScope.launch {
            if (!fileStorage.hasEnoughStorage()) {
                _state.update { it.copy(errorMessage = "Insufficient device storage available.") }
                return@launch
            }

            _state.update { it.copy(isProcessing = true, exportProgress = 0.05f, errorMessage = null) }
            val outFile = fileStorage.createUniquePdfFile(folderName, fileName)
            val thumbFile = fileStorage.createThumbnailFile(System.currentTimeMillis())

            val result = if (snapshot.template.isDualSide && snapshot.pages.size >= 2) {
                PdfGenerator.generateNidDualSidePdf(
                    frontPage = snapshot.pages[0],
                    backPage = snapshot.pages[1],
                    outputFile = outFile,
                    thumbnailOutputFile = thumbFile,
                    layout = snapshot.nidLayout,
                    password = password,
                    onProgress = { p -> _state.update { it.copy(exportProgress = p) } }
                )
            } else {
                PdfGenerator.generateFromPages(
                    pages = snapshot.pages,
                    outputFile = outFile,
                    thumbnailOutputFile = thumbFile,
                    options = PdfExportOptions(
                        fileName = fileName,
                        folderName = folderName,
                        pageSize = pageSize,
                        orientation = orientation,
                        compression = compression,
                        password = password
                    ),
                    onProgress = { p -> _state.update { it.copy(exportProgress = p) } }
                )
            }

            result.onSuccess { pdfRes ->
                val docId = repository.saveDocumentRecord(
                    DocumentEntity(
                        title = pdfRes.pdfFile.nameWithoutExtension,
                        filePath = pdfRes.pdfFile.absolutePath,
                        thumbnailPath = pdfRes.thumbnailFile?.absolutePath,
                        folderName = folderName,
                        templateId = snapshot.template.id,
                        pageCount = pdfRes.pageCount,
                        fileSizeBytes = pdfRes.fileSizeBytes,
                        isPasswordProtected = !password.isNullOrBlank()
                    )
                )
                repository.clearScanDraft()
                _state.update { it.copy(isProcessing = false, exportProgress = 1f) }
                onSaved(docId)
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        isProcessing = false,
                        exportProgress = 0f,
                        errorMessage = err.message ?: "Failed to create PDF"
                    )
                }
            }
        }
    }

    private fun pushUndoSnapshot() {
        undoStack.addLast(_state.value.pages.map { it.copy() })
        if (undoStack.size > 20) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun applyPageUpdate(index: Int, updatedPage: ScanPageItem, reprocessNow: Boolean) {
        val list = _state.value.pages.toMutableList()
        if (index !in list.indices) return
        list[index] = updatedPage
        _state.update {
            it.copy(
                pages = list,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        viewModelScope.launch {
            repository.saveScanDraft(_state.value.template, list)
        }
        if (reprocessNow) {
            reprocessPageFile(updatedPage)
        }
    }

    private fun reprocessPageFile(page: ScanPageItem) {
        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true) }
            withContext(Dispatchers.IO) {
                val orig = BitmapUtils.decodeSampledBitmap(page.originalImagePath, 1800) ?: return@withContext
                try {
                    val warped = PerspectiveTransformer.warpAndTransform(
                        source = orig,
                        quad = page.quad,
                        rotationDegrees = page.rotationDegrees,
                        straightenDegrees = page.straightenDegrees,
                        targetScalePercent = page.targetScalePercent
                    )
                    val enhanced = ImageEnhancementEngine.enhance(
                        source = warped,
                        filterMode = page.filterMode,
                        brightness = page.brightness,
                        contrast = page.contrast,
                        sharpness = page.sharpness
                    )
                    if (warped !== enhanced) warped.recycle()
                    val outFile = File(page.processedImagePath)
                    BitmapUtils.saveBitmapAsJpeg(enhanced, outFile, quality = 90)
                    enhanced.recycle()
                } finally {
                    orig.recycle()
                }
            }
            // Bump path query timestamp or trigger state emission cleanly
            _state.update { it.copy(isProcessing = false) }
        }
    }
}
