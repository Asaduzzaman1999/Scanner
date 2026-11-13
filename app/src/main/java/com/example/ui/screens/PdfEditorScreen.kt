package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.CompressionPreset
import com.example.core.model.NormalizedPoint
import com.example.core.model.NormalizedQuad
import com.example.data.local.DocumentEntity
import com.example.processing.pdf.DrawPathSpec
import com.example.processing.pdf.TextOverlaySpec
import com.example.viewmodel.PdfWorkbenchState

private enum class PdfEditorTool {
    VIEW,
    DRAW,
    HIGHLIGHT,
    TEXT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfEditorScreen(
    state: PdfWorkbenchState,
    allDocuments: List<DocumentEntity>,
    onSelectPage: (Int) -> Unit,
    onRotatePage: () -> Unit,
    onDeletePage: () -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onCropPage: (NormalizedQuad) -> Unit,
    onAddImagePage: (android.net.Uri) -> Unit,
    onReplacePage: (android.net.Uri) -> Unit,
    onAddStroke: (DrawPathSpec) -> Unit,
    onAddTextOverlay: (TextOverlaySpec) -> Unit,
    onAddSignatureBitmap: (Bitmap) -> Unit,
    onAddImageOverlay: (android.net.Uri) -> Unit,
    onUndoOverlay: () -> Unit,
    onRedoOverlay: () -> Unit,
    onSaveOverlays: () -> Unit,
    onAddDateStamp: () -> Unit,
    onAddPageNumbers: () -> Unit,
    onAddWatermark: (String) -> Unit,
    onRenamePdf: (String) -> Unit,
    onSplitPdf: (Int, Int, String) -> Unit,
    onMergePdfs: (List<DocumentEntity>, String) -> Unit,
    onCompressPdf: (CompressionPreset) -> Unit,
    onClearMessage: () -> Unit,
    onBack: () -> Unit
) {
    val s = LocalAppStrings.current
    val doc = state.document

    var activeTool by remember { mutableStateOf(PdfEditorTool.VIEW) }
    var currentStrokePts by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    var showTextPromptAt by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var showSignatureModal by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }

    val addPagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onAddImagePage)
    }
    val replacePagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onReplacePage)
    }
    val addOverlayImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onAddImageOverlay)
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
                title = { Text(doc?.title ?: s.pdfEditorTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onUndoOverlay, enabled = state.canUndoOverlay) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = s.undo)
                    }
                    IconButton(onClick = onRedoOverlay, enabled = state.canRedoOverlay) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = s.redo)
                    }
                    if (state.pendingStrokes.isNotEmpty() || state.pendingTexts.isNotEmpty() || state.pendingImages.isNotEmpty()) {
                        Button(
                            onClick = onSaveOverlays,
                            modifier = Modifier.padding(end = 6.dp).testTag("pdf_editor_save_overlays")
                        ) {
                            Text(s.save)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp, modifier = Modifier.navigationBarsPadding()) {
                Column {
                    // Page Thumbnails + Per-page quick actions
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(state.thumbnails) { idx, thumb ->
                            val selected = idx == state.currentPageIndex
                            Box(
                                modifier = Modifier
                                    .size(width = 48.dp, height = 64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectPage(idx) }
                            ) {
                                if (thumb != null) {
                                    Image(
                                        bitmap = thumb.asImageBitmap(),
                                        contentDescription = "Page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(bottomEnd = 6.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                        item {
                            FilledTonalIconButton(onClick = {
                                addPagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = s.addPage)
                            }
                        }
                    }

                    HorizontalDivider()

                    // Complete PDF Edit Toolbar
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = activeTool == PdfEditorTool.DRAW,
                                onClick = { activeTool = if (activeTool == PdfEditorTool.DRAW) PdfEditorTool.VIEW else PdfEditorTool.DRAW },
                                leadingIcon = { Icon(Icons.Filled.Draw, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(s.draw) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeTool == PdfEditorTool.HIGHLIGHT,
                                onClick = { activeTool = if (activeTool == PdfEditorTool.HIGHLIGHT) PdfEditorTool.VIEW else PdfEditorTool.HIGHLIGHT },
                                leadingIcon = { Icon(Icons.Filled.BorderColor, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(s.highlight) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = activeTool == PdfEditorTool.TEXT,
                                onClick = { activeTool = if (activeTool == PdfEditorTool.TEXT) PdfEditorTool.VIEW else PdfEditorTool.TEXT },
                                leadingIcon = { Icon(Icons.Filled.TextFields, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(s.addText) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { showSignatureModal = true },
                                leadingIcon = { Icon(Icons.Filled.Gesture, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(s.addSignature) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    addOverlayImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(s.addImage) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = onAddDateStamp,
                                leadingIcon = { Icon(Icons.Filled.Event, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(s.addDate) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = onAddPageNumbers,
                                leadingIcon = { Icon(Icons.Filled.FormatListNumbered, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(s.addPageNumbers) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { showWatermarkDialog = true },
                                leadingIcon = { Icon(Icons.Filled.BrandingWatermark, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(s.addWatermark) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = onRotatePage,
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(s.rotate) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    replacePagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                leadingIcon = { Icon(Icons.Filled.FindReplace, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(s.replacePage) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { showCropDialog = true },
                                leadingIcon = { Icon(Icons.Filled.Crop, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(s.cropPage) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { onMovePage(state.currentPageIndex, state.currentPageIndex - 1) },
                                enabled = state.currentPageIndex > 0,
                                label = { Text("← ${s.moveUp}") }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = { onMovePage(state.currentPageIndex, state.currentPageIndex + 1) },
                                enabled = state.currentPageIndex < state.pageCount - 1,
                                label = { Text("${s.moveDown} →") }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = onDeletePage,
                                enabled = state.pageCount > 1,
                                leadingIcon = { Icon(Icons.Filled.Delete, null, modifier = Modifier.size(16.dp)) },
                                label = { Text(s.deletePage) }
                            )
                        }
                        item {
                            AssistChip(onClick = { showMergeDialog = true }, label = { Text(s.mergePdf) })
                        }
                        item {
                            AssistChip(onClick = { showSplitDialog = true }, label = { Text(s.splitPdf) })
                        }
                        item {
                            AssistChip(onClick = { onCompressPdf(CompressionPreset.BALANCED) }, label = { Text(s.compressPdf) })
                        }
                        item {
                            AssistChip(onClick = { showRenameDialog = true }, label = { Text(s.renamePdf) })
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF1E293B))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            state.pagePreviewBitmap?.let { bmp ->
                val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .background(Color.White)
                        .onSizeChanged { canvasSize = it }
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "PDF Editor Page",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(activeTool) {
                                when (activeTool) {
                                    PdfEditorTool.DRAW, PdfEditorTool.HIGHLIGHT -> {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                                val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                                currentStrokePts = listOf(offset.x / w to offset.y / h)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                                val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                                val nx = (change.position.x / w).coerceIn(0f, 1f)
                                                val ny = (change.position.y / h).coerceIn(0f, 1f)
                                                currentStrokePts = currentStrokePts + (nx to ny)
                                            },
                                            onDragEnd = {
                                                if (currentStrokePts.size >= 2) {
                                                    val isHL = activeTool == PdfEditorTool.HIGHLIGHT
                                                    onAddStroke(
                                                        DrawPathSpec(
                                                            points = currentStrokePts,
                                                            colorInt = if (isHL) Color(0xFFFACC15).toArgb() else Color(0xFF0F766E).toArgb(),
                                                            strokeWidthRatio = if (isHL) 0.025f else 0.006f,
                                                            isHighlight = isHL
                                                        )
                                                    )
                                                }
                                                currentStrokePts = emptyList()
                                            }
                                        )
                                    }
                                    PdfEditorTool.TEXT -> {
                                        detectTapGestures { offset ->
                                            val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                            val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                            showTextPromptAt = (offset.x / w).coerceIn(0.05f, 0.85f) to (offset.y / h).coerceIn(0.05f, 0.90f)
                                        }
                                    }
                                    PdfEditorTool.VIEW -> Unit
                                }
                            }
                    ) {
                        val drawStrokeList = state.pendingStrokes + if (currentStrokePts.size >= 2) {
                            listOf(
                                DrawPathSpec(
                                    points = currentStrokePts,
                                    colorInt = if (activeTool == PdfEditorTool.HIGHLIGHT) Color(0xFFFACC15).toArgb() else Color(0xFF0F766E).toArgb(),
                                    strokeWidthRatio = if (activeTool == PdfEditorTool.HIGHLIGHT) 0.025f else 0.006f,
                                    isHighlight = activeTool == PdfEditorTool.HIGHLIGHT
                                )
                            )
                        } else emptyList()

                        drawStrokeList.forEach { spec ->
                            if (spec.points.size >= 2) {
                                val path = Path().apply {
                                    moveTo(spec.points[0].first * size.width, spec.points[0].second * size.height)
                                    for (i in 1 until spec.points.size) {
                                        lineTo(spec.points[i].first * size.width, spec.points[i].second * size.height)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = Color(spec.colorInt).copy(alpha = if (spec.isHighlight) 0.4f else 1f),
                                    style = Stroke(
                                        width = spec.strokeWidthRatio * size.width,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }

                    // Text preview markers
                    state.pendingTexts.forEach { t ->
                        Text(
                            text = t.text,
                            color = Color(t.colorInt),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .offset(
                                    x = ((t.normX * canvasSize.width) / 3f).dp,
                                    y = ((t.normY * canvasSize.height) / 3f).dp
                                )
                                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }
            if (state.isBusy) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }

    // Add Text Dialog
    showTextPromptAt?.let { pos ->
        var textInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTextPromptAt = null },
            title = { Text(s.addText) },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Enter text...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (textInput.isNotBlank()) {
                        onAddTextOverlay(TextOverlaySpec(textInput, pos.first, pos.second))
                    }
                    showTextPromptAt = null
                    activeTool = PdfEditorTool.VIEW
                }) { Text(s.confirm) }
            },
            dismissButton = { TextButton(onClick = { showTextPromptAt = null }) { Text(s.cancel) } }
        )
    }

    // Signature Pad Modal
    if (showSignatureModal) {
        SignaturePadDialog(
            onDismiss = { showSignatureModal = false },
            onConfirmBitmap = { bmp ->
                showSignatureModal = false
                onAddSignatureBitmap(bmp)
            }
        )
    }

    // Watermark Dialog
    if (showWatermarkDialog) {
        var wm by remember { mutableStateOf("CONFIDENTIAL") }
        AlertDialog(
            onDismissRequest = { showWatermarkDialog = false },
            title = { Text(s.addWatermark) },
            text = {
                OutlinedTextField(value = wm, onValueChange = { wm = it }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    onAddWatermark(wm)
                    showWatermarkDialog = false
                }) { Text(s.confirm) }
            },
            dismissButton = { TextButton(onClick = { showWatermarkDialog = false }) { Text(s.cancel) } }
        )
    }

    // Rename Dialog
    if (showRenameDialog && doc != null) {
        var name by remember { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(s.renamePdf) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    onRenamePdf(name)
                    showRenameDialog = false
                }) { Text(s.save) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text(s.cancel) } }
        )
    }

    // Split PDF Dialog
    if (showSplitDialog && doc != null) {
        var fromStr by remember { mutableStateOf("1") }
        var toStr by remember { mutableStateOf(state.pageCount.coerceAtLeast(1).toString()) }
        var outTitle by remember { mutableStateOf("${doc.title}_Split") }
        AlertDialog(
            onDismissRequest = { showSplitDialog = false },
            title = { Text(s.splitPdf) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = outTitle, onValueChange = { outTitle = it }, label = { Text(s.renamePdf) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = fromStr, onValueChange = { fromStr = it }, label = { Text("From") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = toStr, onValueChange = { toStr = it }, label = { Text("To") }, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val f = fromStr.toIntOrNull() ?: 1
                    val t = toStr.toIntOrNull() ?: state.pageCount
                    onSplitPdf(f, t, outTitle)
                    showSplitDialog = false
                }) { Text(s.splitPdf) }
            },
            dismissButton = { TextButton(onClick = { showSplitDialog = false }) { Text(s.cancel) } }
        )
    }

    // Merge PDF Dialog
    if (showMergeDialog && doc != null) {
        val selected = remember { mutableStateListOf(doc) }
        var mergedName by remember { mutableStateOf("${doc.title}_Merged") }
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = { Text(s.mergePdf) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = mergedName, onValueChange = { mergedName = it }, label = { Text(s.renamePdf) })
                    allDocuments.filter { !it.isPasswordProtected }.take(8).forEach { candidate ->
                        val isChecked = selected.any { it.id == candidate.id }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked && candidate.id != doc.id) selected.removeAll { it.id == candidate.id }
                                    else if (!isChecked) selected.add(candidate)
                                }
                        ) {
                            Checkbox(checked = isChecked, onCheckedChange = null)
                            Text(candidate.title, maxLines = 1)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = selected.size >= 2,
                    onClick = {
                        onMergePdfs(selected.toList(), mergedName)
                        showMergeDialog = false
                    }
                ) { Text(s.mergePdf) }
            },
            dismissButton = { TextButton(onClick = { showMergeDialog = false }) { Text(s.cancel) } }
        )
    }

    // Page Margin Crop Dialog
    if (showCropDialog) {
        var inset by remember { mutableFloatStateOf(0.06f) }
        AlertDialog(
            onDismissRequest = { showCropDialog = false },
            title = { Text(s.cropPage) },
            text = {
                Column {
                    Text("Trim border inset: ${(inset * 100).toInt()}%")
                    Slider(value = inset, onValueChange = { inset = it }, valueRange = 0.02f..0.25f)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCropPage(
                        NormalizedQuad(
                            NormalizedPoint(inset, inset),
                            NormalizedPoint(1f - inset, inset),
                            NormalizedPoint(1f - inset, 1f - inset),
                            NormalizedPoint(inset, 1f - inset)
                        )
                    )
                    showCropDialog = false
                }) { Text(s.confirm) }
            },
            dismissButton = { TextButton(onClick = { showCropDialog = false }) { Text(s.cancel) } }
        )
    }
}

@Composable
private fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onConfirmBitmap: (Bitmap) -> Unit
) {
    val s = LocalAppStrings.current
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize(400, 200)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.addSignature) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .onSizeChanged { padSize = it }
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { activeStroke = listOf(it) },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        activeStroke = activeStroke + change.position
                                    },
                                    onDragEnd = {
                                        if (activeStroke.size >= 2) strokes.add(activeStroke)
                                        activeStroke = emptyList()
                                    }
                                )
                            }
                    ) {
                        (strokes + listOf(activeStroke)).forEach { pts ->
                            if (pts.size >= 2) {
                                val path = Path().apply {
                                    moveTo(pts[0].x, pts[0].y)
                                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                                }
                                drawPath(path, Color.Black, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                    }
                }
                TextButton(onClick = { strokes.clear(); activeStroke = emptyList() }) {
                    Text("Clear")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = strokes.isNotEmpty(),
                onClick = {
                    val bmp = Bitmap.createBitmap(padSize.width.coerceAtLeast(200), padSize.height.coerceAtLeast(120), Bitmap.Config.ARGB_8888)
                    val canvas = AndroidCanvas(bmp)
                    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        color = AndroidColor.BLACK
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = 7f
                        strokeCap = AndroidPaint.Cap.ROUND
                        strokeJoin = AndroidPaint.Join.ROUND
                    }
                    strokes.forEach { pts ->
                        if (pts.size >= 2) {
                            val p = AndroidPath()
                            p.moveTo(pts[0].x, pts[0].y)
                            for (i in 1 until pts.size) p.lineTo(pts[i].x, pts[i].y)
                            canvas.drawPath(p, paint)
                        }
                    }
                    onConfirmBitmap(bmp)
                }
            ) { Text(s.confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } }
    )
}
