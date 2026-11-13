package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ads.AdMobConfig
import com.example.ads.TestBannerAdView
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.AppLanguage
import com.example.core.model.DocumentTemplate
import com.example.core.model.ScanPageItem
import com.example.data.local.DocumentEntity
import com.example.data.local.FolderEntity
import com.example.processing.pdf.PdfPrintHelper
import com.example.ui.components.DocumentCard
import com.example.ui.components.EmptyStateIllustration
import java.io.File

private data class QuickToolItem(
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val testTag: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentLanguage: AppLanguage,
    recentDocuments: List<DocumentEntity>,
    folders: List<FolderEntity>,
    activeDraft: Pair<DocumentTemplate, List<ScanPageItem>>?,
    onToggleLanguage: () -> Unit,
    onSearchClick: () -> Unit,
    onScanDocument: (DocumentTemplate, Boolean) -> Unit,
    onImagesToPdf: () -> Unit,
    onPdfToImages: () -> Unit,
    onEditPdfPicker: () -> Unit,
    onMyDocuments: () -> Unit,
    onFavorites: () -> Unit,
    onTrash: () -> Unit,
    onSettings: () -> Unit,
    onResumeDraft: (DocumentTemplate, List<ScanPageItem>) -> Unit,
    onDiscardDraft: () -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
    onEditDocument: (DocumentEntity) -> Unit,
    onPdfToImagesDoc: (DocumentEntity) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity, String) -> Unit,
    onDuplicateDocument: (DocumentEntity) -> Unit,
    onMoveDocumentFolder: (DocumentEntity, String) -> Unit,
    onTogglePrivate: (DocumentEntity) -> Unit,
    onDeleteToTrash: (DocumentEntity) -> Unit,
    onSharePdf: (DocumentEntity) -> Intent?
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var moveTarget by remember { mutableStateOf<DocumentEntity?>(null) }

    val quickTools = listOf(
        QuickToolItem(s.scanDocument, Icons.Filled.DocumentScanner, MaterialTheme.colorScheme.primary, "tool_scan") {
            onScanDocument(DocumentTemplate.NORMAL, false)
        },
        QuickToolItem(s.batchScan, Icons.Filled.FilterNone, Color(0xFF0284C7), "tool_batch") {
            onScanDocument(DocumentTemplate.NORMAL, true)
        },
        QuickToolItem(s.imagesToPdf, Icons.Filled.PhotoLibrary, Color(0xFF7C3AED), "tool_img2pdf", onImagesToPdf),
        QuickToolItem(s.pdfToImages, Icons.Filled.Collections, Color(0xFFD97706), "tool_pdf2img", onPdfToImages),
        QuickToolItem(s.editPdf, Icons.Filled.EditNote, Color(0xFF059669), "tool_edit_pdf", onEditPdfPicker),
        QuickToolItem(s.myDocuments, Icons.Filled.FolderCopy, Color(0xFF2563EB), "tool_my_docs", onMyDocuments),
        QuickToolItem(s.favorites, Icons.Filled.Star, Color(0xFFEAB308), "tool_favorites", onFavorites),
        QuickToolItem(s.trash, Icons.Filled.DeleteOutline, Color(0xFFDC2626), "tool_trash", onTrash),
        QuickToolItem(s.settings, Icons.Filled.Settings, Color(0xFF475569), "tool_settings", onSettings)
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(s.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (AdMobConfig.IS_TEST_ADS_MODE) "${s.offlineBadge} • Test Ads" else s.offlineBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = onToggleLanguage,
                        modifier = Modifier.testTag("home_lang_toggle")
                    ) {
                        Text(if (currentLanguage == AppLanguage.EN) "বাংলা" else "EN")
                    }
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_search_button")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = s.searchDocuments)
                    }
                }
            )
        },
        bottomBar = {
            // Test Banner Ad placed safely at bottom of Home dashboard (never during camera scan)
            TestBannerAdView(modifier = Modifier.navigationBarsPadding())
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Unfinished Scan Recovery Banner
            if (activeDraft != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.RestorePage,
                                contentDescription = s.resumeUnfinishedScan,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.resumeUnfinishedScan, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    s.unfinishedPagesCount(activeDraft.second.size),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            TextButton(onClick = onDiscardDraft) { Text(s.discard) }
                            Button(onClick = { onResumeDraft(activeDraft.first, activeDraft.second) }) {
                                Text(s.resume)
                            }
                        }
                    }
                }
            }

            // Hero Scan CTA
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    Color(0xFF0D9488)
                                )
                            )
                        )
                        .clickable { onScanDocument(DocumentTemplate.NORMAL, false) }
                        .testTag("hero_scan_button")
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.scanNowHero,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = s.scanNowSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = { onScanDocument(DocumentTemplate.NORMAL, false) },
                                    label = { Text(s.scanDocument, color = Color.White) },
                                    leadingIcon = { Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                    border = null
                                )
                                AssistChip(
                                    onClick = { onScanDocument(DocumentTemplate.NID, false) },
                                    label = { Text("NID", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Filled.Badge, null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                    border = null
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.DocumentScanner,
                                    contentDescription = s.scanNowHero,
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 9-item Quick Tools 3x3 Grid
            item {
                Text(s.quickActions, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    quickTools.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { item ->
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 88.dp)
                                        .testTag(item.testTag)
                                        .clickable { item.onClick() },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = item.tint.copy(alpha = 0.14f),
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(item.icon, contentDescription = item.title, tint = item.tint, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Special Document Modes Strip (all 18 modes)
            item {
                Text(s.specialModes, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(DocumentTemplate.entries.filter { it != DocumentTemplate.NORMAL }) { template ->
                        ElevatedCard(
                            modifier = Modifier
                                .width(136.dp)
                                .testTag("template_${template.id}")
                                .clickable { onScanDocument(template, false) },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = templateIcon(template),
                                    contentDescription = s.templateName(template),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = s.templateName(template),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = template.defaultPageSize.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Recent Documents
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.recentDocuments, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onMyDocuments) { Text(s.myDocuments) }
                }
            }

            if (recentDocuments.isEmpty()) {
                item {
                    EmptyStateIllustration(title = s.noRecentDocs, subtitle = s.noRecentDocsHint)
                }
            } else {
                items(recentDocuments, key = { it.id }) { doc ->
                    DocumentCard(
                        doc = doc,
                        onClick = { onOpenDocument(doc) },
                        onToggleFavorite = { onToggleFavorite(doc) },
                        onSharePdf = {
                            onSharePdf(doc)?.let { context.startActivity(Intent.createChooser(it, s.sharePdf)) }
                        },
                        onRename = { renameTarget = doc },
                        onDuplicate = { onDuplicateDocument(doc) },
                        onMoveFolder = { moveTarget = doc },
                        onEditPdf = { onEditDocument(doc) },
                        onPdfToImages = { onPdfToImagesDoc(doc) },
                        onPrint = { PdfPrintHelper.printPdf(context, File(doc.filePath), doc.title) },
                        onTogglePrivate = { onTogglePrivate(doc) },
                        onDelete = { onDeleteToTrash(doc) }
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        var newName by remember { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(s.renamePdf) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRenameDocument(target, newName)
                    renameTarget = null
                }) { Text(s.save) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(s.cancel) } }
        )
    }

    moveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text(s.moveToFolder) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    folders.forEach { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = { Icon(Icons.Filled.Folder, null) },
                            modifier = Modifier.clickable {
                                onMoveDocumentFolder(target, folder.name)
                                moveTarget = null
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moveTarget = null }) { Text(s.cancel) } }
        )
    }
}

private fun templateIcon(template: DocumentTemplate): ImageVector = when (template) {
    DocumentTemplate.NID, DocumentTemplate.DRIVING_LICENSE, DocumentTemplate.STUDENT_ID -> Icons.Filled.Badge
    DocumentTemplate.PASSPORT, DocumentTemplate.PASSPORT_PHOTO -> Icons.Filled.AccountBox
    DocumentTemplate.BIRTH_CERTIFICATE, DocumentTemplate.ACADEMIC_CERTIFICATE,
    DocumentTemplate.MARKSHEET, DocumentTemplate.TRANSCRIPT -> Icons.Filled.WorkspacePremium
    DocumentTemplate.BUSINESS_CARD -> Icons.Filled.ContactPage
    DocumentTemplate.RECEIPT, DocumentTemplate.INVOICE -> Icons.Filled.ReceiptLong
    DocumentTemplate.LEGAL_DOCUMENT -> Icons.Filled.Gavel
    DocumentTemplate.WHITEBOARD -> Icons.Filled.CoPresent
    DocumentTemplate.NOTES -> Icons.Filled.EditNote
    DocumentTemplate.BOOK_PAGES -> Icons.AutoMirrored.Filled.LibraryBooks
    else -> Icons.Filled.Description
}
