package com.example.ui.navigation

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.AppLanguage
import com.example.core.model.DocumentTemplate
import com.example.ui.screens.*
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.PdfWorkbenchViewModel
import com.example.viewmodel.ScannerSessionViewModel

@Composable
fun AppNavHost(
    appViewModel: AppViewModel,
    scannerViewModel: ScannerSessionViewModel = viewModel(),
    pdfViewModel: PdfWorkbenchViewModel = viewModel()
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val activity = context as? Activity
    val s = LocalAppStrings.current

    val prefs by appViewModel.userPrefs.collectAsStateWithLifecycle()
    val recentDocs by appViewModel.recentDocuments.collectAsStateWithLifecycle()
    val allDocs by appViewModel.allDocuments.collectAsStateWithLifecycle()
    val filteredDocs by appViewModel.filteredDocuments.collectAsStateWithLifecycle()
    val privateDocs by appViewModel.privateDocuments.collectAsStateWithLifecycle()
    val favoriteDocs by appViewModel.favoriteDocuments.collectAsStateWithLifecycle()
    val trashedDocs by appViewModel.trashedDocuments.collectAsStateWithLifecycle()
    val folders by appViewModel.folders.collectAsStateWithLifecycle()
    val activeDraft by appViewModel.activeDraft.collectAsStateWithLifecycle()
    val searchQuery by appViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFolder by appViewModel.selectedFolder.collectAsStateWithLifecycle()
    val sortOrder by appViewModel.sortOrder.collectAsStateWithLifecycle()

    val scannerState by scannerViewModel.state.collectAsStateWithLifecycle()
    val pdfState by pdfViewModel.state.collectAsStateWithLifecycle()

    var showEditPdfPicker by remember { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = SplashRoute
    ) {
        composable<SplashRoute> {
            SplashScreen(
                onboardingCompleted = prefs.onboardingCompleted,
                onNavigateNext = { goHome ->
                    navController.navigate(if (goHome) HomeRoute else OnboardingRoute) {
                        popUpTo<SplashRoute> { inclusive = true }
                    }
                }
            )
        }

        composable<OnboardingRoute> {
            OnboardingScreen(
                currentLanguage = prefs.language,
                onChangeLanguage = { appViewModel.setLanguage(it) },
                onFinish = {
                    appViewModel.completeOnboarding()
                    navController.navigate(HomeRoute) {
                        popUpTo<OnboardingRoute> { inclusive = true }
                    }
                }
            )
        }

        composable<HomeRoute> {
            HomeScreen(
                currentLanguage = prefs.language,
                recentDocuments = recentDocs,
                folders = folders,
                activeDraft = activeDraft,
                onToggleLanguage = {
                    val next = if (prefs.language == AppLanguage.EN) AppLanguage.BN else AppLanguage.EN
                    appViewModel.setLanguage(next)
                },
                onSearchClick = { navController.navigate(MyDocumentsRoute) },
                onScanDocument = { template, isBatch ->
                    scannerViewModel.startNewSession(template, isBatch)
                    navController.navigate(ScannerRoute(template.id, isBatch))
                },
                onImagesToPdf = { navController.navigate(ImagesToPdfRoute) },
                onPdfToImages = { navController.navigate(PdfToImagesRoute(-1L)) },
                onEditPdfPicker = { showEditPdfPicker = true },
                onMyDocuments = { navController.navigate(MyDocumentsRoute) },
                onFavorites = { navController.navigate(FavoritesRoute) },
                onTrash = { navController.navigate(TrashRoute) },
                onSettings = { navController.navigate(SettingsRoute) },
                onResumeDraft = { template, pages ->
                    scannerViewModel.restoreDraft(template, pages)
                    navController.navigate(PageManagerRoute)
                },
                onDiscardDraft = { appViewModel.discardDraft() },
                onOpenDocument = { doc -> navController.navigate(PdfPreviewRoute(doc.id)) },
                onEditDocument = { doc -> navController.navigate(PdfEditorRoute(doc.id)) },
                onPdfToImagesDoc = { doc -> navController.navigate(PdfToImagesRoute(doc.id)) },
                onToggleFavorite = { appViewModel.toggleFavorite(it) },
                onRenameDocument = { doc, name -> appViewModel.renameDocument(doc, name) },
                onDuplicateDocument = { appViewModel.duplicateDocument(it) },
                onMoveDocumentFolder = { doc, folder -> appViewModel.moveDocumentToFolder(doc, folder) },
                onTogglePrivate = { appViewModel.togglePrivateVault(it) },
                onDeleteToTrash = { appViewModel.moveToTrash(it) },
                onSharePdf = { appViewModel.createSharePdfIntent(it) }
            )
        }

        composable<ScannerRoute> { backStack ->
            val route = backStack.toRoute<ScannerRoute>()
            LaunchedEffect(route.templateId, route.isBatch) {
                if (scannerState.pages.isEmpty()) {
                    val tmpl = DocumentTemplate.entries.find { it.id == route.templateId } ?: DocumentTemplate.NORMAL
                    scannerViewModel.setTemplate(tmpl)
                }
            }
            ScannerScreen(
                uiState = scannerState,
                createTempCaptureFile = { appViewModel.fileStorage.createTempImageFile("cam") },
                onSelectTemplate = { scannerViewModel.setTemplate(it) },
                onImageCaptured = { file ->
                    scannerViewModel.addCapturedImageFile(file) {
                        val st = scannerViewModel.state.value
                        if (st.template.isDualSide && st.pages.size < 2) {
                            // Stay in scanner for Step 2 (Back side) or let user preview Front
                            navController.navigate(ScanPreviewRoute)
                        } else if (!st.isBatchMode) {
                            navController.navigate(ScanPreviewRoute)
                        }
                    }
                },
                onImportGalleryUri = { uri ->
                    scannerViewModel.importFromGalleryUri(uri) {
                        val st = scannerViewModel.state.value
                        if (!st.isBatchMode) {
                            navController.navigate(ScanPreviewRoute)
                        }
                    }
                },
                onOpenBatchManager = { navController.navigate(PageManagerRoute) },
                onDiscardAndExit = {
                    appViewModel.discardDraft()
                    navController.popBackStack(HomeRoute, false)
                },
                onExitPreservingDraft = {
                    navController.popBackStack()
                }
            )
        }

        composable<ScanPreviewRoute> {
            ScanPreviewScreen(
                uiState = scannerState,
                onUpdateQuad = { scannerViewModel.updateActivePageQuad(it) },
                onAutoDetect = { scannerViewModel.autoDetectActivePageCorners() },
                onRetake = {
                    scannerViewModel.prepareRetakeForPage(scannerState.activePageIndex)
                    navController.popBackStack()
                },
                onEdit = { navController.navigate(ImageEditorRoute) },
                onAddPage = {
                    scannerViewModel.prepareAddNewPage()
                    navController.popBackStack()
                },
                onSaveOrNext = {
                    val st = scannerState
                    if (st.template.isDualSide && st.pages.size < 2) {
                        // Move immediately to capture NID Back side
                        scannerViewModel.prepareAddNewPage()
                        navController.popBackStack()
                    } else {
                        navController.navigate(PageManagerRoute)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<ImageEditorRoute> {
            ImageEditorScreen(
                uiState = scannerState,
                onRotate = { scannerViewModel.rotateActivePage(90) },
                onAdjustmentsChanged = { f, b, c, sh, st, sc ->
                    scannerViewModel.updateEditorAdjustments(f, b, c, sh, st, sc)
                },
                onUndo = { scannerViewModel.undo() },
                onRedo = { scannerViewModel.redo() },
                onRunOcr = { scannerViewModel.runOcrOnActivePage() },
                onClearOcr = { scannerViewModel.clearOcrResult() },
                onExportOcrTxt = { text ->
                    val f = pdfViewModel.exportOcrTextFile("OCR_${System.currentTimeMillis() % 10000}", text)
                    Toast.makeText(context, if (f != null) "Saved ${f.name}" else "Export failed", Toast.LENGTH_SHORT).show()
                },
                onDone = { navController.popBackStack() }
            )
        }

        composable<PageManagerRoute> {
            PageManagerScreen(
                uiState = scannerState,
                folders = folders,
                suggestedName = scannerViewModel.suggestDefaultFileName(allDocs.size),
                onSelectPageAndEdit = { idx ->
                    scannerViewModel.selectActivePage(idx)
                    navController.navigate(ImageEditorRoute)
                },
                onRetakePage = { idx ->
                    scannerViewModel.prepareRetakeForPage(idx)
                    navController.navigate(ScannerRoute(scannerState.template.id, scannerState.isBatchMode))
                },
                onRotatePage = { idx -> scannerViewModel.rotatePageAt(idx, 90) },
                onDeletePage = { idx -> scannerViewModel.deletePageAt(idx) },
                onMovePage = { from, to -> scannerViewModel.movePage(from, to) },
                onAddMorePages = {
                    scannerViewModel.prepareAddNewPage()
                    navController.navigate(ScannerRoute(scannerState.template.id, true))
                },
                onUpdateNidLayout = { scannerViewModel.updateNidLayout(it) },
                onUndo = { scannerViewModel.undo() },
                onRedo = { scannerViewModel.redo() },
                onGeneratePdf = { name, folder, size, orient, comp, pass ->
                    scannerViewModel.generateAndSavePdf(name, folder, size, orient, comp, pass) { newDocId ->
                        appViewModel.adManager.maybeShowTestInterstitial(activity) {
                            navController.navigate(PdfPreviewRoute(newDocId)) {
                                popUpTo(HomeRoute)
                            }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<PdfPreviewRoute> { backStack ->
            val route = backStack.toRoute<PdfPreviewRoute>()
            LaunchedEffect(route.docId) {
                pdfViewModel.loadDocument(route.docId)
            }
            PdfPreviewScreen(
                state = pdfState,
                onSelectPage = { pdfViewModel.selectPage(it) },
                onUnlockPdf = { pdfViewModel.unlockPdfWithPassword(it) },
                onOpenEditor = { navController.navigate(PdfEditorRoute(route.docId)) },
                onOpenPdfToImages = { navController.navigate(PdfToImagesRoute(route.docId)) },
                onSharePdf = { appViewModel.createSharePdfIntent(it) },
                onRunOcr = { pdfViewModel.runOcrOnCurrentPdf() },
                onClearOcr = { pdfViewModel.clearOcrText() },
                onExportOcrTxt = { txt ->
                    val name = pdfState.document?.title ?: "OCR_Text"
                    val f = pdfViewModel.exportOcrTextFile(name, txt)
                    Toast.makeText(context, if (f != null) "Saved ${f.name}" else "Export failed", Toast.LENGTH_SHORT).show()
                },
                onCompressPdf = { pdfViewModel.compressCurrentPdf(it) },
                onSetPassword = { pdfViewModel.setPasswordProtection(it) },
                onRemovePassword = { pdfViewModel.removePasswordProtection(it) },
                onClearMessage = { pdfViewModel.clearMessageAndError() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<PdfEditorRoute> { backStack ->
            val route = backStack.toRoute<PdfEditorRoute>()
            LaunchedEffect(route.docId) {
                pdfViewModel.loadDocument(route.docId)
            }
            PdfEditorScreen(
                state = pdfState,
                allDocuments = allDocs,
                onSelectPage = { pdfViewModel.selectPage(it) },
                onRotatePage = { pdfViewModel.rotateCurrentPage(90) },
                onDeletePage = { pdfViewModel.deleteCurrentPage() },
                onMovePage = { from, to -> pdfViewModel.movePage(from, to) },
                onCropPage = { pdfViewModel.cropCurrentPage(it) },
                onAddImagePage = { pdfViewModel.addImagePageFromUri(it) },
                onReplacePage = { pdfViewModel.replaceCurrentPageWithUri(it) },
                onAddStroke = { pdfViewModel.addStroke(it) },
                onAddTextOverlay = { pdfViewModel.addTextOverlay(it) },
                onAddSignatureBitmap = { pdfViewModel.addSignatureBitmap(it) },
                onAddImageOverlay = { pdfViewModel.addImageOverlayFromUri(it) },
                onUndoOverlay = { pdfViewModel.undoOverlay() },
                onRedoOverlay = { pdfViewModel.redoOverlay() },
                onSaveOverlays = { pdfViewModel.commitOverlaysToPage() },
                onAddDateStamp = { pdfViewModel.addDateStamp() },
                onAddPageNumbers = { pdfViewModel.addPageNumbers() },
                onAddWatermark = { pdfViewModel.addWatermark(it) },
                onRenamePdf = { newName ->
                    pdfState.document?.let { appViewModel.renameDocument(it, newName) }
                },
                onSplitPdf = { f, t, title ->
                    pdfViewModel.splitCurrentPdf(f, t, title) { splitId ->
                        navController.navigate(PdfPreviewRoute(splitId))
                    }
                },
                onMergePdfs = { docs, title ->
                    pdfViewModel.mergePdfs(docs, title) { mergedId ->
                        navController.navigate(PdfPreviewRoute(mergedId))
                    }
                },
                onCompressPdf = { pdfViewModel.compressCurrentPdf(it) },
                onClearMessage = { pdfViewModel.clearMessageAndError() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<ImagesToPdfRoute> {
            ImagesToPdfScreen(
                state = pdfState,
                onAddImages = { pdfViewModel.addGalleryImages(it) },
                onRotateImage = { pdfViewModel.rotateGalleryImage(it) },
                onAutoCropImage = { pdfViewModel.cropGalleryImageAuto(it) },
                onEnhanceImage = { idx, filter -> pdfViewModel.enhanceGalleryImage(idx, filter) },
                onMoveImage = { from, to -> pdfViewModel.moveGalleryImage(from, to) },
                onRemoveImage = { pdfViewModel.removeGalleryImage(it) },
                onGeneratePdf = { name, size, orient, margin, comp, cw, ch ->
                    pdfViewModel.generatePdfFromGalleryImages(name, size, orient, margin, comp, cw, ch) { docId ->
                        appViewModel.adManager.maybeShowTestInterstitial(activity) {
                            navController.navigate(PdfPreviewRoute(docId)) {
                                popUpTo(HomeRoute)
                            }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<PdfToImagesRoute> { backStack ->
            val route = backStack.toRoute<PdfToImagesRoute>()
            LaunchedEffect(route.docId) {
                if (route.docId > 0L) pdfViewModel.loadDocument(route.docId)
            }
            PdfToImagesScreen(
                state = pdfState,
                availableDocuments = allDocs,
                onSelectDocument = { pdfViewModel.loadDocument(it) },
                onExport = { file, indices, fmt, dpi, q, saveGal ->
                    pdfViewModel.exportPdfToImages(file, indices, fmt, dpi, q, saveGal)
                },
                onShareExportedUris = { appViewModel.createShareMultipleImagesIntent(it) },
                onClearMessage = { pdfViewModel.clearMessageAndError() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<MyDocumentsRoute> {
            MyDocumentsScreen(
                documents = filteredDocs,
                privateDocuments = privateDocs,
                folders = folders,
                searchQuery = searchQuery,
                selectedFolder = selectedFolder,
                sortOrder = sortOrder,
                onSearchChange = { appViewModel.setSearchQuery(it) },
                onSelectFolder = { appViewModel.setSelectedFolder(it) },
                onChangeSort = { appViewModel.setSortOrder(it) },
                onCreateFolder = { appViewModel.createFolder(it) },
                onOpenDocument = { navController.navigate(PdfPreviewRoute(it.id)) },
                onEditDocument = { navController.navigate(PdfEditorRoute(it.id)) },
                onPdfToImages = { navController.navigate(PdfToImagesRoute(it.id)) },
                onToggleFavorite = { appViewModel.toggleFavorite(it) },
                onRenameDocument = { doc, name -> appViewModel.renameDocument(doc, name) },
                onDuplicateDocument = { appViewModel.duplicateDocument(it) },
                onMoveDocumentFolder = { doc, folder -> appViewModel.moveDocumentToFolder(doc, folder) },
                onTogglePrivate = { appViewModel.togglePrivateVault(it) },
                onDeleteToTrash = { appViewModel.moveToTrash(it) },
                onMergeSelected = { docs, name ->
                    pdfViewModel.mergePdfs(docs, name) { mergedId ->
                        navController.navigate(PdfPreviewRoute(mergedId))
                    }
                },
                onSharePdf = { appViewModel.createSharePdfIntent(it) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<FavoritesRoute> {
            FavoritesScreen(
                favorites = favoriteDocs,
                onOpenDocument = { navController.navigate(PdfPreviewRoute(it.id)) },
                onEditDocument = { navController.navigate(PdfEditorRoute(it.id)) },
                onPdfToImages = { navController.navigate(PdfToImagesRoute(it.id)) },
                onToggleFavorite = { appViewModel.toggleFavorite(it) },
                onDuplicateDocument = { appViewModel.duplicateDocument(it) },
                onTogglePrivate = { appViewModel.togglePrivateVault(it) },
                onDeleteToTrash = { appViewModel.moveToTrash(it) },
                onSharePdf = { appViewModel.createSharePdfIntent(it) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<TrashRoute> {
            TrashScreen(
                trashedDocs = trashedDocs,
                onRestore = { appViewModel.restoreFromTrash(it) },
                onPermanentDelete = { appViewModel.permanentlyDelete(it) },
                onEmptyTrash = { appViewModel.emptyTrash() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                prefs = prefs,
                onLanguageChange = { appViewModel.setLanguage(it) },
                onThemeChange = { appViewModel.setThemeMode(it) },
                onDefaultPageSizeChange = { appViewModel.setDefaultPageSize(it) },
                onDefaultCompressionChange = { appViewModel.setDefaultCompression(it) },
                onSetPin = { appViewModel.setPin(it) },
                onToggleBiometric = { appViewModel.setBiometricEnabled(it) },
                onCleanTempFiles = {
                    appViewModel.cleanTempCache { freedBytes ->
                        Toast.makeText(context, "${s.tempFilesCleared} (${freedBytes / 1024} KB)", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenAbout = { navController.navigate(AboutRoute) },
                onOpenPrivacy = { navController.navigate(PrivacyPolicyRoute) },
                onBack = { navController.popBackStack() }
            )
        }

        composable<AboutRoute> {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable<PrivacyPolicyRoute> {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }
    }

    if (showEditPdfPicker) {
        AlertDialog(
            onDismissRequest = { showEditPdfPicker = false },
            title = { Text(s.selectPdf) },
            text = {
                if (allDocs.isEmpty()) {
                    Text(s.noRecentDocsHint)
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        allDocs.take(8).forEach { doc ->
                            ListItem(
                                headlineContent = { Text(doc.title, maxLines = 1) },
                                supportingContent = { Text(s.pageCount(doc.pageCount)) },
                                leadingContent = { Icon(Icons.Filled.PictureAsPdf, null) },
                                modifier = Modifier.clickable {
                                    showEditPdfPicker = false
                                    navController.navigate(PdfEditorRoute(doc.id))
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEditPdfPicker = false }) { Text(s.cancel) }
            }
        )
    }
}
