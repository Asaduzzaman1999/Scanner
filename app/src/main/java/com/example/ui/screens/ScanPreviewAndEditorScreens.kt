package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.ImageFilterMode
import com.example.core.model.NormalizedPoint
import com.example.core.model.NormalizedQuad
import com.example.ui.components.OcrResultDialog
import com.example.viewmodel.ScannerUiState
import java.io.File
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPreviewScreen(
    uiState: ScannerUiState,
    onUpdateQuad: (NormalizedQuad) -> Unit,
    onAutoDetect: () -> Unit,
    onRetake: () -> Unit,
    onEdit: () -> Unit,
    onAddPage: () -> Unit,
    onSaveOrNext: () -> Unit,
    onBack: () -> Unit
) {
    val s = LocalAppStrings.current
    val activePage = uiState.activePage

    var showManualCrop by remember { mutableStateOf(false) }
    var editableQuad by remember(activePage?.id, activePage?.quad) {
        mutableStateOf(activePage?.quad ?: NormalizedQuad())
    }
    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
    var activeCornerIdx by remember { mutableIntStateOf(-1) }

    val displayPath = if (showManualCrop) activePage?.originalImagePath else activePage?.processedImagePath
    val bitmap = remember(displayPath, uiState.isProcessing) {
        displayPath?.let { path ->
            if (File(path).exists()) BitmapFactory.decodeFile(path)?.asImageBitmap() else null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (uiState.template.isDualSide) {
                                if (uiState.activePageIndex == 0) s.nidFrontLabel else s.nidBackLabel
                            } else {
                                s.templateName(uiState.template)
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (showManualCrop) s.adjustCornersHint else s.autoCropApplied,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (showManualCrop) {
                                onUpdateQuad(editableQuad)
                            }
                            showManualCrop = !showManualCrop
                        },
                        modifier = Modifier.testTag("toggle_manual_crop")
                    ) {
                        Text(if (showManualCrop) s.done else s.manualCrop)
                    }
                    if (showManualCrop) {
                        IconButton(onClick = onAutoDetect) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = s.autoDetect)
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onRetake,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("preview_retake_button")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Refresh, null)
                            Text(s.retake, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    TextButton(
                        onClick = {
                            if (showManualCrop) {
                                onUpdateQuad(editableQuad)
                                showManualCrop = false
                            }
                            onEdit()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("preview_edit_button")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Tune, null)
                            Text(s.edit, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    TextButton(
                        onClick = {
                            if (showManualCrop) {
                                onUpdateQuad(editableQuad)
                                showManualCrop = false
                            }
                            onAddPage()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("preview_add_page_button")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AddBox, null)
                            Text(
                                text = if (uiState.template.isDualSide && uiState.pages.size < 2) s.nidBackLabel else s.addPage,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Button(
                        onClick = {
                            if (showManualCrop) {
                                onUpdateQuad(editableQuad)
                                showManualCrop = false
                            }
                            onSaveOrNext()
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("preview_save_button")
                    ) {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.save)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F172A))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { boxSize = it }
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Captured preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (showManualCrop) {
                        val w = boxSize.width.toFloat().coerceAtLeast(1f)
                        val h = boxSize.height.toFloat().coerceAtLeast(1f)
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(editableQuad) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val pts = editableQuad.asList().map {
                                                Offset(it.x * w, it.y * h)
                                            }
                                            var bestIdx = -1
                                            var bestDist = 90f
                                            pts.forEachIndexed { idx, p ->
                                                val d = hypot(p.x - offset.x, p.y - offset.y)
                                                if (d < bestDist) {
                                                    bestDist = d
                                                    bestIdx = idx
                                                }
                                            }
                                            activeCornerIdx = bestIdx
                                        },
                                        onDragEnd = { activeCornerIdx = -1 },
                                        onDragCancel = { activeCornerIdx = -1 },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (activeCornerIdx in 0..3) {
                                                val list = editableQuad.asList().toMutableList()
                                                val cur = list[activeCornerIdx]
                                                val nx = (cur.x + dragAmount.x / w).coerceIn(0.01f, 0.99f)
                                                val ny = (cur.y + dragAmount.y / h).coerceIn(0.01f, 0.99f)
                                                list[activeCornerIdx] = NormalizedPoint(nx, ny)
                                                editableQuad = NormalizedQuad(list[0], list[1], list[2], list[3])
                                            }
                                        }
                                    )
                                }
                        ) {
                            val tl = Offset(editableQuad.topLeft.x * size.width, editableQuad.topLeft.y * size.height)
                            val tr = Offset(editableQuad.topRight.x * size.width, editableQuad.topRight.y * size.height)
                            val br = Offset(editableQuad.bottomRight.x * size.width, editableQuad.bottomRight.y * size.height)
                            val bl = Offset(editableQuad.bottomLeft.x * size.width, editableQuad.bottomLeft.y * size.height)

                            val path = Path().apply {
                                moveTo(tl.x, tl.y)
                                lineTo(tr.x, tr.y)
                                lineTo(br.x, br.y)
                                lineTo(bl.x, bl.y)
                                close()
                            }
                            drawPath(path, color = Color(0xFF14B8A6), style = Stroke(width = 3.dp.toPx()))
                            listOf(tl, tr, br, bl).forEachIndexed { idx, pt ->
                                drawCircle(
                                    color = if (idx == activeCornerIdx) Color(0xFFF59E0B) else Color.White,
                                    radius = 12.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = Color(0xFF0F766E),
                                    radius = 7.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isProcessing) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    uiState: ScannerUiState,
    onRotate: () -> Unit,
    onAdjustmentsChanged: (
        filterMode: ImageFilterMode?,
        brightness: Float?,
        contrast: Float?,
        sharpness: Float?,
        straightenDegrees: Float?,
        targetScalePercent: Int?
    ) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRunOcr: () -> Unit,
    onClearOcr: () -> Unit,
    onExportOcrTxt: (String) -> Unit,
    onDone: () -> Unit
) {
    val s = LocalAppStrings.current
    val page = uiState.activePage
    val bitmap = remember(page?.processedImagePath, uiState.isProcessing) {
        page?.processedImagePath?.let { p ->
            if (File(p).exists()) BitmapFactory.decodeFile(p)?.asImageBitmap() else null
        }
    }

    var brightness by remember(page?.id, page?.brightness) { mutableFloatStateOf(page?.brightness ?: 0f) }
    var contrast by remember(page?.id, page?.contrast) { mutableFloatStateOf(page?.contrast ?: 1f) }
    var sharpness by remember(page?.id, page?.sharpness) { mutableFloatStateOf(page?.sharpness ?: 0.25f) }
    var straighten by remember(page?.id, page?.straightenDegrees) { mutableFloatStateOf(page?.straightenDegrees ?: 0f) }
    var scalePct by remember(page?.id, page?.targetScalePercent) { mutableFloatStateOf((page?.targetScalePercent ?: 100).toFloat()) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(s.edit) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onUndo, enabled = uiState.canUndo, modifier = Modifier.testTag("editor_undo")) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = s.undo)
                    }
                    IconButton(onClick = onRedo, enabled = uiState.canRedo, modifier = Modifier.testTag("editor_redo")) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = s.redo)
                    }
                    IconButton(onClick = onRunOcr, modifier = Modifier.testTag("editor_ocr_button")) {
                        Icon(Icons.Filled.TextFields, contentDescription = s.extractTextOcr)
                    }
                    TextButton(onClick = onDone, modifier = Modifier.testTag("editor_done_button")) {
                        Text(s.done, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Image Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0F172A))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Edited document",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (uiState.isProcessing || uiState.isOcrRunning) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            // Controls Panel
            Surface(
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.filters, style = MaterialTheme.typography.titleMedium)
                        FilledTonalButton(onClick = onRotate) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.rotate)
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ImageFilterMode.entries) { mode ->
                            val selected = page?.filterMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { onAdjustmentsChanged(mode, null, null, null, null, null) },
                                label = { Text(s.filterName(mode)) },
                                modifier = Modifier.testTag("filter_${mode.name}")
                            )
                        }
                    }

                    Text("${s.brightness}: ${brightness.toInt()}", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        onValueChangeFinished = { onAdjustmentsChanged(null, brightness, null, null, null, null) },
                        valueRange = -50f..50f
                    )

                    Text("${s.contrast}: ${String.format("%.2f", contrast)}x", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = contrast,
                        onValueChange = { contrast = it },
                        onValueChangeFinished = { onAdjustmentsChanged(null, null, contrast, null, null, null) },
                        valueRange = 0.6f..1.9f
                    )

                    Text("${s.sharpness}: ${(sharpness * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = sharpness,
                        onValueChange = { sharpness = it },
                        onValueChangeFinished = { onAdjustmentsChanged(null, null, null, sharpness, null, null) },
                        valueRange = 0f..1f
                    )

                    Text("${s.straighten}: ${straighten.toInt()}°", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = straighten,
                        onValueChange = { straighten = it },
                        onValueChangeFinished = { onAdjustmentsChanged(null, null, null, null, straighten, null) },
                        valueRange = -15f..15f
                    )

                    Text("${s.resizeScale}: ${scalePct.toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = scalePct,
                        onValueChange = { scalePct = it },
                        onValueChangeFinished = { onAdjustmentsChanged(null, null, null, null, null, scalePct.toInt()) },
                        valueRange = 40f..100f
                    )
                }
            }
        }
    }

    uiState.ocrResult?.let { txt ->
        OcrResultDialog(
            extractedText = txt,
            onExportTxt = onExportOcrTxt,
            onDismiss = onClearOcr
        )
    }
}
