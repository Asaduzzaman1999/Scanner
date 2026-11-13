package com.example.ui.screens

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.CompressionPreset
import com.example.data.local.DocumentEntity
import com.example.processing.pdf.PdfPrintHelper
import com.example.ui.components.OcrResultDialog
import com.example.viewmodel.PdfWorkbenchState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    state: PdfWorkbenchState,
    onSelectPage: (Int) -> Unit,
    onUnlockPdf: (String) -> Unit,
    onOpenEditor: () -> Unit,
    onOpenPdfToImages: () -> Unit,
    onSharePdf: (DocumentEntity) -> Intent?,
    onRunOcr: () -> Unit,
    onClearOcr: () -> Unit,
    onExportOcrTxt: (String) -> Unit,
    onCompressPdf: (CompressionPreset) -> Unit,
    onSetPassword: (String) -> Unit,
    onRemovePassword: (String) -> Unit,
    onClearMessage: () -> Unit,
    onBack: () -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val doc = state.document

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var unlockInput by remember { mutableStateOf("") }

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
                title = {
                    Column {
                        Text(doc?.title ?: s.pdfPreviewTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        if (doc != null) {
                            val sizeStr = Formatter.formatShortFileSize(context, doc.fileSizeBytes)
                            Text(
                                text = "${s.pageCount(state.pageCount)} • $sizeStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (doc != null && !state.isPasswordLocked) {
                        IconButton(
                            onClick = {
                                onSharePdf(doc)?.let { context.startActivity(Intent.createChooser(it, s.sharePdf)) }
                            },
                            modifier = Modifier.testTag("pdf_preview_share")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = s.sharePdf)
                        }
                        IconButton(
                            onClick = { PdfPrintHelper.printPdf(context, File(doc.filePath), doc.title) },
                            modifier = Modifier.testTag("pdf_preview_print")
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = s.printPdf)
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!state.isPasswordLocked && doc != null) {
                Surface(tonalElevation = 4.dp, modifier = Modifier.navigationBarsPadding()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilledTonalButton(onClick = onOpenEditor, modifier = Modifier.testTag("pdf_preview_edit")) {
                                Icon(Icons.Filled.EditNote, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(s.editPdf)
                            }
                        }
                        item {
                            FilledTonalButton(onClick = onRunOcr, modifier = Modifier.testTag("pdf_preview_ocr")) {
                                Icon(Icons.Filled.TextFields, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("OCR")
                            }
                        }
                        item {
                            FilledTonalButton(onClick = onOpenPdfToImages) {
                                Icon(Icons.Filled.Image, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(s.shareAsImages)
                            }
                        }
                        item {
                            FilledTonalButton(onClick = { showCompressDialog = true }) {
                                Icon(Icons.Filled.Compress, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(s.compressPdf)
                            }
                        }
                        item {
                            FilledTonalButton(onClick = { showPasswordDialog = true }) {
                                Icon(Icons.Filled.Lock, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(s.passwordProtect)
                            }
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
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            if (state.isPasswordLocked) {
                Card(
                    modifier = Modifier.padding(24.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        Text(s.enterPassword, style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = unlockInput,
                            onValueChange = { unlockInput = it },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { onUnlockPdf(unlockInput) }, modifier = Modifier.fillMaxWidth()) {
                            Text(s.unlockPdf)
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        state.pagePreviewBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "PDF Page ${state.currentPageIndex + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale)
                            )
                        }
                        if (state.isBusy) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }

                    if (state.pageCount > 1) {
                        Surface(color = Color.Black.copy(alpha = 0.65f)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onSelectPage(state.currentPageIndex - 1) },
                                    enabled = state.currentPageIndex > 0
                                ) {
                                    Icon(Icons.Filled.ChevronLeft, null, tint = Color.White)
                                }
                                Text(
                                    text = "${state.currentPageIndex + 1} / ${state.pageCount}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                IconButton(
                                    onClick = { onSelectPage(state.currentPageIndex + 1) },
                                    enabled = state.currentPageIndex < state.pageCount - 1
                                ) {
                                    Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCompressDialog && doc != null) {
        val currentBytes = doc.fileSizeBytes
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text(s.compressPdf) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current: ${Formatter.formatShortFileSize(context, currentBytes)}")
                    listOf(
                        Triple(CompressionPreset.HIGH_QUALITY, s.highQuality, 0.80),
                        Triple(CompressionPreset.BALANCED, s.balanced, 0.52),
                        Triple(CompressionPreset.SMALL_SIZE, s.smallSize, 0.28)
                    ).forEach { (preset, label, ratio) ->
                        val est = Formatter.formatShortFileSize(context, (currentBytes * ratio).toLong().coerceAtLeast(12_000L))
                        ListItem(
                            headlineContent = { Text(label) },
                            supportingContent = { Text("${s.estimatedSize}: ~$est") },
                            leadingContent = { Icon(Icons.Filled.Compress, null) },
                            modifier = Modifier.clickable {
                                showCompressDialog = false
                                onCompressPdf(preset)
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCompressDialog = false }) { Text(s.cancel) } }
        )
    }

    if (showPasswordDialog && doc != null) {
        var pass1 by remember { mutableStateOf("") }
        var pass2 by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text(if (doc.isPasswordProtected) s.removePassword else s.passwordProtect) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (doc.isPasswordProtected) {
                        OutlinedTextField(
                            value = pass1,
                            onValueChange = { pass1 = it },
                            label = { Text(s.enterPassword) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    } else {
                        OutlinedTextField(
                            value = pass1,
                            onValueChange = { pass1 = it; err = null },
                            label = { Text(s.setPassword) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = pass2,
                            onValueChange = { pass2 = it; err = null },
                            label = { Text(s.confirmPassword) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (doc.isPasswordProtected) {
                        onRemovePassword(pass1)
                        showPasswordDialog = false
                    } else if (pass1.isNotBlank() && pass1 == pass2) {
                        onSetPassword(pass1)
                        showPasswordDialog = false
                    } else {
                        err = s.passwordMismatch
                    }
                }) { Text(s.confirm) }
            },
            dismissButton = { TextButton(onClick = { showPasswordDialog = false }) { Text(s.cancel) } }
        )
    }

    state.ocrText?.let { text ->
        OcrResultDialog(
            extractedText = text,
            onExportTxt = onExportOcrTxt,
            onDismiss = onClearOcr
        )
    }
}
