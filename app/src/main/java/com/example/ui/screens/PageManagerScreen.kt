package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.CompressionPreset
import com.example.core.model.NidLayoutOptions
import com.example.core.model.PageOrientation
import com.example.core.model.PageSizeOption
import com.example.data.local.FolderEntity
import com.example.ui.components.SavePdfDialog
import com.example.viewmodel.ScannerUiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageManagerScreen(
    uiState: ScannerUiState,
    folders: List<FolderEntity>,
    suggestedName: String,
    onSelectPageAndEdit: (Int) -> Unit,
    onRetakePage: (Int) -> Unit,
    onRotatePage: (Int) -> Unit,
    onDeletePage: (Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onAddMorePages: () -> Unit,
    onUpdateNidLayout: (NidLayoutOptions) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onGeneratePdf: (
        fileName: String,
        folderName: String,
        pageSize: PageSizeOption,
        orientation: PageOrientation,
        compression: CompressionPreset,
        password: String?
    ) -> Unit,
    onBack: () -> Unit
) {
    val s = LocalAppStrings.current
    var showSaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (uiState.template.isDualSide) s.nidPreviewTitle else s.pageManagerTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = s.pageCount(uiState.pages.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onUndo, enabled = uiState.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = s.undo)
                    }
                    IconButton(onClick = onRedo, enabled = uiState.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = s.redo)
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding()) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    if (uiState.isProcessing && uiState.exportProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { uiState.exportProgress },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onAddMorePages,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("page_mgr_add_button")
                        ) {
                            Icon(Icons.Filled.AddAPhoto, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.addPage)
                        }
                        Button(
                            onClick = { showSaveDialog = true },
                            enabled = uiState.pages.isNotEmpty() && !uiState.isProcessing,
                            modifier = Modifier
                                .weight(1.4f)
                                .height(50.dp)
                                .testTag("page_mgr_generate_pdf_button")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.generatePdf)
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // NID Front + Back Same-Page Live Sheet Preview & Options
            if (uiState.template.isDualSide && uiState.pages.size >= 2) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = s.nidSamePageOption,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            // Visual A4 sheet preview showing Front + Back stacked cleanly
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(10.dp),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(
                                        if (uiState.nidLayout.orientation == PageOrientation.PORTRAIT) 0.707f else 1.414f
                                    )
                                    .padding(horizontal = 24.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding((uiState.nidLayout.marginDp / 2f).dp),
                                    verticalArrangement = Arrangement.SpaceEvenly,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    listOf(0 to s.nidFrontLabel, 1 to s.nidBackLabel).forEach { (idx, label) ->
                                        val page = uiState.pages[idx]
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .border(
                                                    width = if (uiState.nidLayout.drawGuideBorder) 1.dp else 0.dp,
                                                    color = if (uiState.nidLayout.drawGuideBorder) Color(0xFFCBD5E1) else Color.Transparent,
                                                    shape = RoundedCornerShape(6.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = File(page.processedImagePath),
                                                contentDescription = label,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize().padding(4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // NID Options
                            val opts = uiState.nidLayout
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = opts.samePage,
                                    onCheckedChange = { onUpdateNidLayout(opts.copy(samePage = it)) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(s.nidSamePageOption, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = opts.autoResizeUniform,
                                    onCheckedChange = { onUpdateNidLayout(opts.copy(autoResizeUniform = it)) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(s.nidAutoResizeOption, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = opts.drawGuideBorder,
                                    onCheckedChange = { onUpdateNidLayout(opts.copy(drawGuideBorder = it)) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(s.nidBorderGuideOption, style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = opts.orientation == PageOrientation.PORTRAIT,
                                    onClick = { onUpdateNidLayout(opts.copy(orientation = PageOrientation.PORTRAIT)) },
                                    label = { Text(s.portrait) }
                                )
                                FilterChip(
                                    selected = opts.orientation == PageOrientation.LANDSCAPE,
                                    onClick = { onUpdateNidLayout(opts.copy(orientation = PageOrientation.LANDSCAPE)) },
                                    label = { Text(s.landscape) }
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = s.reorderHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(uiState.pages, key = { _, p -> p.id }) { index, page ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 72.dp, height = 96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onSelectPageAndEdit(index) }
                        ) {
                            AsyncImage(
                                model = File(page.processedImagePath),
                                contentDescription = "Page ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(bottomEnd = 8.dp)
                            ) {
                                Text(
                                    text = if (page.label.isNotBlank()) page.label else "#${index + 1}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (page.label.isNotBlank()) "${page.label} (Page ${index + 1})" else "Page ${index + 1}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = s.filterName(page.filterMode),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { onRetakePage(index) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = s.retake)
                                }
                                IconButton(
                                    onClick = { onSelectPageAndEdit(index) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = s.edit)
                                }
                                IconButton(
                                    onClick = { onRotatePage(index) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = s.rotate)
                                }
                                IconButton(
                                    onClick = { onDeletePage(index) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = s.deletePage, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        Column {
                            IconButton(
                                onClick = { onMovePage(index, index - 1) },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = s.moveUp)
                            }
                            IconButton(
                                onClick = { onMovePage(index, index + 1) },
                                enabled = index < uiState.pages.lastIndex
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = s.moveDown)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SavePdfDialog(
            suggestedName = suggestedName,
            defaultFolder = uiState.template.defaultFolder,
            defaultPageSize = uiState.template.defaultPageSize,
            defaultOrientation = uiState.template.defaultOrientation,
            folders = folders,
            pageCount = if (uiState.template.isDualSide && uiState.nidLayout.samePage) 1 else uiState.pages.size,
            onDismiss = { showSaveDialog = false },
            onConfirmSave = { fileName, folderName, pageSize, orientation, compression, password ->
                showSaveDialog = false
                onGeneratePdf(fileName, folderName, pageSize, orientation, compression, password)
            }
        )
    }
}
