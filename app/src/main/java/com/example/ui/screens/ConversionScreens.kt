package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.CompressionPreset
import com.example.core.model.ExportImageFormat
import com.example.core.model.ImageFilterMode
import com.example.core.model.PageOrientation
import com.example.core.model.PageSizeOption
import com.example.data.local.DocumentEntity
import com.example.ui.components.EmptyStateIllustration
import com.example.viewmodel.PdfWorkbenchState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesToPdfScreen(
    state: PdfWorkbenchState,
    onAddImages: (List<Uri>) -> Unit,
    onRotateImage: (Int) -> Unit,
    onAutoCropImage: (Int) -> Unit,
    onEnhanceImage: (Int, ImageFilterMode) -> Unit,
    onMoveImage: (Int, Int) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onGeneratePdf: (
        fileName: String,
        pageSize: PageSizeOption,
        orientation: PageOrientation,
        marginPt: Float,
        compression: CompressionPreset,
        customW: Float,
        customH: Float
    ) -> Unit,
    onBack: () -> Unit
) {
    val s = LocalAppStrings.current
    val dateStamp = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var fileName by remember { mutableStateOf("Images_$dateStamp") }
    var pageSize by remember { mutableStateOf(PageSizeOption.A4) }
    var orientation by remember { mutableStateOf(PageOrientation.PORTRAIT) }
    var marginPt by remember { mutableFloatStateOf(20f) }
    var compression by remember { mutableStateOf(CompressionPreset.HIGH_QUALITY) }
    var customW by remember { mutableStateOf("595") }
    var customH by remember { mutableStateOf("842") }

    val multiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris ->
        if (uris.isNotEmpty()) onAddImages(uris)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(s.imagesToPdf) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.testTag("img2pdf_pick_button")
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, null)
                        Spacer(Modifier.width(4.dp))
                        Text(s.selectImages)
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp, modifier = Modifier.navigationBarsPadding()) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    if (state.isBusy && state.progress > 0f) {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    }
                    Button(
                        onClick = {
                            onGeneratePdf(
                                fileName.ifBlank { "Images_$dateStamp" },
                                pageSize,
                                orientation,
                                marginPt,
                                compression,
                                customW.toFloatOrNull() ?: 595f,
                                customH.toFloatOrNull() ?: 842f
                            )
                        },
                        enabled = state.galleryImages.isNotEmpty() && !state.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("img2pdf_generate_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, null)
                        Spacer(Modifier.width(8.dp))
                        Text("${s.generatePdf} (${state.galleryImages.size})")
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
            if (state.galleryImages.isEmpty()) {
                item {
                    EmptyStateIllustration(
                        title = s.imagesToPdf,
                        subtitle = s.selectImages
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text(s.selectImages)
                    }
                }
            } else {
                // Settings Card
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = fileName,
                                onValueChange = { fileName = it },
                                label = { Text(s.renamePdf) },
                                suffix = { Text(".pdf") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(s.pageSize, style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(PageSizeOption.entries) { size ->
                                    FilterChip(
                                        selected = pageSize == size,
                                        onClick = { pageSize = size },
                                        label = { Text(size.label) }
                                    )
                                }
                            }
                            if (pageSize == PageSizeOption.CUSTOM) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = customW,
                                        onValueChange = { customW = it },
                                        label = { Text("Width (pt)") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = customH,
                                        onValueChange = { customH = it },
                                        label = { Text("Height (pt)") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = orientation == PageOrientation.PORTRAIT,
                                    onClick = { orientation = PageOrientation.PORTRAIT },
                                    label = { Text(s.portrait) }
                                )
                                FilterChip(
                                    selected = orientation == PageOrientation.LANDSCAPE,
                                    onClick = { orientation = PageOrientation.LANDSCAPE },
                                    label = { Text(s.landscape) }
                                )
                            }

                            Text("${s.margins}: ${marginPt.toInt()} pt", style = MaterialTheme.typography.labelMedium)
                            Slider(value = marginPt, onValueChange = { marginPt = it }, valueRange = 0f..56f)

                            Text(s.quality, style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = compression == CompressionPreset.HIGH_QUALITY,
                                    onClick = { compression = CompressionPreset.HIGH_QUALITY },
                                    label = { Text(s.highQuality) }
                                )
                                FilterChip(
                                    selected = compression == CompressionPreset.BALANCED,
                                    onClick = { compression = CompressionPreset.BALANCED },
                                    label = { Text(s.balanced) }
                                )
                                FilterChip(
                                    selected = compression == CompressionPreset.SMALL_SIZE,
                                    onClick = { compression = CompressionPreset.SMALL_SIZE },
                                    label = { Text(s.smallSize) }
                                )
                            }
                        }
                    }
                }

                itemsIndexed(state.galleryImages, key = { _, item -> item.id }) { idx, item ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = File(item.processedImagePath),
                                contentDescription = "Image ${idx + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 64.dp, height = 84.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Page ${idx + 1}", style = MaterialTheme.typography.titleMedium)
                                Row {
                                    IconButton(onClick = { onAutoCropImage(idx) }) {
                                        Icon(Icons.Filled.Crop, contentDescription = s.autoDetect)
                                    }
                                    IconButton(onClick = { onRotateImage(idx) }) {
                                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = s.rotate)
                                    }
                                    IconButton(onClick = {
                                        val next = if (item.filterMode == ImageFilterMode.COLOR_ENHANCE) ImageFilterMode.ORIGINAL else ImageFilterMode.COLOR_ENHANCE
                                        onEnhanceImage(idx, next)
                                    }) {
                                        Icon(Icons.Filled.AutoFixHigh, contentDescription = s.filters)
                                    }
                                    IconButton(onClick = { onRemoveImage(idx) }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = s.deletePage, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            Column {
                                IconButton(onClick = { onMoveImage(idx, idx - 1) }, enabled = idx > 0) {
                                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = s.moveUp)
                                }
                                IconButton(onClick = { onMoveImage(idx, idx + 1) }, enabled = idx < state.galleryImages.lastIndex) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = s.moveDown)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImagesScreen(
    state: PdfWorkbenchState,
    availableDocuments: List<DocumentEntity>,
    onSelectDocument: (Long) -> Unit,
    onExport: (
        pdfFile: File,
        selectedIndices: List<Int>,
        format: ExportImageFormat,
        dpi: Int,
        quality: Int,
        saveToGallery: Boolean
    ) -> Unit,
    onShareExportedUris: (List<Uri>) -> Intent,
    onClearMessage: () -> Unit,
    onBack: () -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val doc = state.document

    var format by remember { mutableStateOf(ExportImageFormat.JPG) }
    var dpi by remember { mutableIntStateOf(150) }
    var quality by remember { mutableFloatStateOf(90f) }
    var saveToGallery by remember { mutableStateOf(true) }
    var allPagesMode by remember { mutableStateOf(true) }
    val selectedPages = remember(doc?.id, state.pageCount) {
        mutableStateListOf<Int>().apply {
            for (i in 0 until state.pageCount) add(i)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message, state.error) {
        val msg = state.message ?: state.error
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onClearMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(s.pdfToImages) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (doc == null) {
                item {
                    Text(s.selectPdf, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (availableDocuments.isEmpty()) {
                    item { EmptyStateIllustration(title = s.noRecentDocs, subtitle = s.noRecentDocsHint) }
                } else {
                    items(availableDocuments.filter { !it.isPasswordProtected }, key = { it.id }) { candidate ->
                        ListItem(
                            headlineContent = { Text(candidate.title) },
                            supportingContent = { Text(s.pageCount(candidate.pageCount)) },
                            leadingContent = { Icon(Icons.Filled.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectDocument(candidate.id) }
                        )
                        HorizontalDivider()
                    }
                }
            } else {
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(doc.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(s.pageCount(state.pageCount), style = MaterialTheme.typography.bodyMedium)

                            Text(s.formatLabel, style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExportImageFormat.entries.forEach { fmt ->
                                    FilterChip(
                                        selected = format == fmt,
                                        onClick = { format = fmt },
                                        label = { Text(fmt.name) }
                                    )
                                }
                            }

                            Text("${s.resolutionDpi}: $dpi DPI", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(72, 150, 300).forEach { opt ->
                                    FilterChip(
                                        selected = dpi == opt,
                                        onClick = { dpi = opt },
                                        label = { Text("$opt DPI") }
                                    )
                                }
                            }

                            Text("${s.quality}: ${quality.toInt()}%", style = MaterialTheme.typography.labelMedium)
                            Slider(value = quality, onValueChange = { quality = it }, valueRange = 40f..100f)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = saveToGallery, onCheckedChange = { saveToGallery = it })
                                Spacer(Modifier.width(8.dp))
                                Text(s.saveToGallery)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = allPagesMode,
                                    onClick = { allPagesMode = true },
                                    label = { Text(s.allPages) }
                                )
                                FilterChip(
                                    selected = !allPagesMode,
                                    onClick = { allPagesMode = false },
                                    label = { Text(s.selectedPages) }
                                )
                            }

                            if (!allPagesMode) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(state.pageCount) { idx ->
                                        val isSel = selectedPages.contains(idx)
                                        FilterChip(
                                            selected = isSel,
                                            onClick = {
                                                if (isSel) selectedPages.remove(idx) else selectedPages.add(idx)
                                            },
                                            label = { Text("P${idx + 1}") }
                                        )
                                    }
                                }
                            }

                            if (state.isBusy) {
                                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                            }

                            Button(
                                onClick = {
                                    val targets = if (allPagesMode) (0 until state.pageCount).toList() else selectedPages.sorted()
                                    onExport(File(doc.filePath), targets, format, dpi, quality.toInt(), saveToGallery)
                                },
                                enabled = !state.isBusy && (allPagesMode || selectedPages.isNotEmpty()),
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pdf2img_export_button")
                            ) {
                                Icon(Icons.Filled.Download, null)
                                Spacer(Modifier.width(8.dp))
                                Text(s.exportImages)
                            }
                        }
                    }
                }

                state.exportedImagesResult?.let { res ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${s.successSaved}: ${res.exportedFiles.size} image(s)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Button(
                                    onClick = {
                                        val intent = onShareExportedUris(res.shareUris)
                                        context.startActivity(Intent.createChooser(intent, s.shareAsImages))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.Share, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(s.shareAsImages)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
