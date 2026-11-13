package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.DocumentTemplate
import com.example.viewmodel.ScannerUiState
import java.io.File

@Composable
fun ScannerScreen(
    uiState: ScannerUiState,
    createTempCaptureFile: () -> File,
    onSelectTemplate: (DocumentTemplate) -> Unit,
    onImageCaptured: (File) -> Unit,
    onImportGalleryUri: (android.net.Uri) -> Unit,
    onOpenBatchManager: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onExitPreservingDraft: () -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraAvailable by remember { mutableStateOf(true) }
    var flashEnabled by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(true) }
    var showExitConfirm by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(onImportGalleryUri)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler {
        if (uiState.pages.isNotEmpty()) {
            showExitConfirm = true
        } else {
            onExitPreservingDraft()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (hasCameraPermission && cameraAvailable) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        runCatching {
                            val cameraProvider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                                .build()
                            imageCapture = capture
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture
                            )
                        }.onFailure {
                            cameraAvailable = false
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                update = {
                    imageCapture?.flashMode =
                        if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                }
            )

            // Alignment / Aspect Guide Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (showGrid) {
                    val thirdW = size.width / 3f
                    val thirdH = size.height / 3f
                    val gridColor = Color.White.copy(alpha = 0.22f)
                    drawLine(gridColor, Offset(thirdW, 0f), Offset(thirdW, size.height), 1.5f)
                    drawLine(gridColor, Offset(thirdW * 2, 0f), Offset(thirdW * 2, size.height), 1.5f)
                    drawLine(gridColor, Offset(0f, thirdH), Offset(size.width, thirdH), 1.5f)
                    drawLine(gridColor, Offset(0f, thirdH * 2), Offset(size.width, thirdH * 2), 1.5f)
                }

                // Template aspect frame guide
                val targetRatio = uiState.template.aspectRatio
                val frameW = size.width * 0.84f
                val frameH = (frameW / targetRatio).coerceIn(size.height * 0.28f, size.height * 0.62f)
                val left = (size.width - frameW) / 2f
                val top = (size.height - frameH) / 2f
                drawRect(
                    color = Color(0xFF14B8A6).copy(alpha = 0.85f),
                    topLeft = Offset(left, top),
                    size = Size(frameW, frameH),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        } else {
            // Graceful Camera Permission Denied / Camera Unavailable Fallback
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.NoPhotography,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (!hasCameraPermission) s.cameraPermissionTitle else s.cameraUnavailable,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                if (!hasCameraPermission) {
                    Text(
                        text = s.cameraPermissionDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.testTag("grant_camera_button")
                    ) {
                        Text(s.grantPermission)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.testTag("fallback_gallery_import")
                ) {
                    Icon(Icons.Filled.PhotoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text(s.importFromGallery)
                }
            }
        }

        // Top Bar Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    if (uiState.pages.isNotEmpty()) showExitConfirm = true else onExitPreservingDraft()
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("scanner_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        uiState.template.isDualSide -> if (uiState.pages.isEmpty()) s.captureNidFront else s.captureNidBack
                        uiState.isBatchMode -> "${s.batchScan} (${uiState.pages.size})"
                        else -> s.templateName(uiState.template)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Row {
                IconButton(
                    onClick = { showGrid = !showGrid },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        if (showGrid) Icons.Filled.GridOn else Icons.Filled.GridOff,
                        contentDescription = "Grid",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = { flashEnabled = !flashEnabled },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        contentDescription = "Flash",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom Controls: Template Selector + Capture + Gallery + Batch Counter
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DocumentTemplate.entries) { tmpl ->
                    val selected = tmpl == uiState.template
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { onSelectTemplate(tmpl) }
                    ) {
                        Text(
                            text = s.templateName(tmpl),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Gallery Import (Photo Picker - zero broad storage permission)
                IconButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .testTag("scanner_gallery_button")
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = s.importFromGallery, tint = Color.White)
                }

                // Shutter Capture Button
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (uiState.isProcessing) Color.Gray else Color.White)
                        .testTag("scanner_capture_button")
                        .clickable(enabled = hasCameraPermission && cameraAvailable && !uiState.isProcessing) {
                            val cap = imageCapture ?: return@clickable
                            val outFile = createTempCaptureFile()
                            val outputOpts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                            cap.takePicture(
                                outputOpts,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        onImageCaptured(outFile)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        cameraAvailable = false
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                    }
                }

                // Page Queue / Done Button
                if (uiState.pages.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = onOpenBatchManager,
                        modifier = Modifier.testTag("scanner_done_batch_button")
                    ) {
                        Text("${uiState.pages.size} →")
                    }
                } else {
                    Spacer(Modifier.size(52.dp))
                }
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(s.confirmDiscardScanTitle) },
            text = { Text(s.confirmDiscardScanMsg) },
            confirmButton = {
                Button(onClick = {
                    showExitConfirm = false
                    onExitPreservingDraft()
                }) {
                    Text(s.save)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showExitConfirm = false
                        onDiscardAndExit()
                    }) {
                        Text(s.discard, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showExitConfirm = false }) {
                        Text(s.keepEditing)
                    }
                }
            }
        )
    }
}
