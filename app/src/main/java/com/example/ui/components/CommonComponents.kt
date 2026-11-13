package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.CompressionPreset
import com.example.core.model.PageOrientation
import com.example.core.model.PageSizeOption
import com.example.data.local.DocumentEntity
import com.example.data.local.FolderEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DocumentCard(
    doc: DocumentEntity,
    isSelected: Boolean = false,
    inSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleFavorite: () -> Unit,
    onSharePdf: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveFolder: () -> Unit,
    onEditPdf: () -> Unit,
    onPdfToImages: () -> Unit,
    onPrint: () -> Unit,
    onTogglePrivate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val formattedDate = remember(doc.modifiedAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(doc.modifiedAt))
    }
    val formattedSize = remember(doc.fileSizeBytes) {
        Formatter.formatShortFileSize(context, doc.fileSizeBytes)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("doc_card_${doc.id}")
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val thumbFile = doc.thumbnailPath?.let { File(it) }
                if (thumbFile != null && thumbFile.exists() && !doc.isPasswordProtected) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = doc.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (doc.isPasswordProtected) Icons.Filled.Lock else Icons.Filled.PictureAsPdf,
                        contentDescription = doc.title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                if (inSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (doc.isPasswordProtected) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Locked",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${s.pageCount(doc.pageCount)} • $formattedSize • $formattedDate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = doc.folderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (doc.isPrivate) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = s.privateVault,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("fav_btn_${doc.id}")
            ) {
                Icon(
                    imageVector = if (doc.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (doc.isFavorite) s.unfavorite else s.favorite,
                    tint = if (doc.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("more_btn_${doc.id}")
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(s.open) },
                        leadingIcon = { Icon(Icons.Filled.OpenInNew, null) },
                        onClick = { menuExpanded = false; onClick() }
                    )
                    DropdownMenuItem(
                        text = { Text(s.editPdf) },
                        leadingIcon = { Icon(Icons.Filled.EditNote, null) },
                        onClick = { menuExpanded = false; onEditPdf() }
                    )
                    DropdownMenuItem(
                        text = { Text(s.sharePdf) },
                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                        onClick = { menuExpanded = false; onSharePdf() }
                    )
                    DropdownMenuItem(
                        text = { Text(s.pdfToImages) },
                        leadingIcon = { Icon(Icons.Filled.Image, null) },
                        onClick = { menuExpanded = false; onPdfToImages() }
                    )
                    DropdownMenuItem(
                        text = { Text(s.printPdf) },
                        leadingIcon = { Icon(Icons.Filled.Print, null) },
                        onClick = { menuExpanded = false; onPrint() }
                    )
                    DropdownMenuItem(
                        text = { Text(s.rename) },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, null) },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(s.duplicate) },
                        leadingIcon = { Icon(Icons.Filled.FileCopy, null) },
                        onClick = { menuExpanded = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text(s.moveToFolder) },
                        leadingIcon = { Icon(Icons.Filled.DriveFileMove, null) },
                        onClick = { menuExpanded = false; onMoveFolder() }
                    )
                    DropdownMenuItem(
                        text = { Text(s.moveToPrivate) },
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        onClick = { menuExpanded = false; onTogglePrivate() }
                    )
                    onLongClick?.let { lc ->
                        DropdownMenuItem(
                            text = { Text("Select") },
                            leadingIcon = { Icon(Icons.Filled.CheckCircle, null) },
                            onClick = { menuExpanded = false; lc() }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(s.deleteToTrash, color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun SavePdfDialog(
    suggestedName: String,
    defaultFolder: String,
    defaultPageSize: PageSizeOption,
    defaultOrientation: PageOrientation,
    folders: List<FolderEntity>,
    pageCount: Int,
    onDismiss: () -> Unit,
    onConfirmSave: (
        fileName: String,
        folderName: String,
        pageSize: PageSizeOption,
        orientation: PageOrientation,
        compression: CompressionPreset,
        password: String?
    ) -> Unit
) {
    val s = LocalAppStrings.current
    var fileName by remember { mutableStateOf(suggestedName) }
    var selectedFolder by remember { mutableStateOf(defaultFolder) }
    var selectedPageSize by remember { mutableStateOf(defaultPageSize) }
    var selectedOrientation by remember { mutableStateOf(defaultOrientation) }
    var selectedCompression by remember { mutableStateOf(CompressionPreset.HIGH_QUALITY) }
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    val estKb = remember(pageCount, selectedCompression) {
        val perPage = when (selectedCompression) {
            CompressionPreset.HIGH_QUALITY -> 420
            CompressionPreset.BALANCED -> 190
            CompressionPreset.SMALL_SIZE -> 85
        }
        pageCount.coerceAtLeast(1) * perPage
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.savePdf) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text(s.renamePdf) },
                    suffix = { Text(".pdf") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_pdf_name_input")
                )

                Text(s.moveToFolder, style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val folderNames = folders.map { it.name }.ifEmpty { listOf("Scanned Documents", "NID", "Passport", "Certificates", "Receipts") }
                    items(folderNames) { folder ->
                        FilterChip(
                            selected = selectedFolder == folder,
                            onClick = { selectedFolder = folder },
                            label = { Text(folder) }
                        )
                    }
                }

                Text(s.pageSize, style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PageSizeOption.entries) { size ->
                        FilterChip(
                            selected = selectedPageSize == size,
                            onClick = { selectedPageSize = size },
                            label = { Text(size.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedOrientation == PageOrientation.PORTRAIT,
                        onClick = { selectedOrientation = PageOrientation.PORTRAIT },
                        label = { Text(s.portrait) }
                    )
                    FilterChip(
                        selected = selectedOrientation == PageOrientation.LANDSCAPE,
                        onClick = { selectedOrientation = PageOrientation.LANDSCAPE },
                        label = { Text(s.landscape) }
                    )
                }

                Text("${s.quality} (~${estKb} KB)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedCompression == CompressionPreset.HIGH_QUALITY,
                        onClick = { selectedCompression = CompressionPreset.HIGH_QUALITY },
                        label = { Text(s.highQuality) }
                    )
                    FilterChip(
                        selected = selectedCompression == CompressionPreset.BALANCED,
                        onClick = { selectedCompression = CompressionPreset.BALANCED },
                        label = { Text(s.balanced) }
                    )
                    FilterChip(
                        selected = selectedCompression == CompressionPreset.SMALL_SIZE,
                        onClick = { selectedCompression = CompressionPreset.SMALL_SIZE },
                        label = { Text(s.smallSize) }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = usePassword, onCheckedChange = { usePassword = it })
                    Text(s.passwordProtect, style = MaterialTheme.typography.bodyMedium)
                }
                if (usePassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(s.setPassword) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmSave(
                        fileName.ifBlank { suggestedName },
                        selectedFolder,
                        selectedPageSize,
                        selectedOrientation,
                        selectedCompression,
                        if (usePassword && password.isNotBlank()) password else null
                    )
                },
                modifier = Modifier.testTag("confirm_save_pdf_button")
            ) {
                Text(s.savePdf)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        }
    )
}

@Composable
fun OcrResultDialog(
    extractedText: String,
    onExportTxt: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }

    val displayedText = remember(extractedText, search) {
        if (search.isBlank()) extractedText
        else extractedText.lines().filter { it.contains(search, ignoreCase = true) }.joinToString("\n")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.ocrResultTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(s.searchInText) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = s.ocrBengaliNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 280.dp)
                ) {
                    Text(
                        text = displayedText.ifBlank { s.ocrEmptyHint },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onExportTxt(extractedText) }) {
                    Text(s.exportTxt)
                }
                Button(onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("OCR Text", extractedText))
                    Toast.makeText(context, s.copyText, Toast.LENGTH_SHORT).show()
                }) {
                    Text(s.copyText)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.done) }
        }
    )
}

@Composable
fun EmptyStateIllustration(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_docs),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 200.dp, height = 150.dp)
                .clip(RoundedCornerShape(20.dp))
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AppLockGateScreen(
    biometricAvailable: Boolean,
    onVerifyPin: (String) -> Boolean,
    onRequestBiometric: () -> Unit
) {
    val s = LocalAppStrings.current
    var enteredPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = s.appLockTitle,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(s.appName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(s.enterPin, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { idx ->
                    val filled = idx < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                    )
                }
            }

            if (errorMsg != null) {
                Spacer(Modifier.height(12.dp))
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(28.dp))

            val digits = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("BIO", "0", "DEL")
            )
            digits.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    row.forEach { key ->
                        when (key) {
                            "BIO" -> {
                                if (biometricAvailable) {
                                    FilledTonalIconButton(
                                        onClick = onRequestBiometric,
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Icon(Icons.Filled.Fingerprint, contentDescription = s.biometricLock)
                                    }
                                } else {
                                    Spacer(Modifier.size(64.dp))
                                }
                            }
                            "DEL" -> {
                                FilledTonalIconButton(
                                    onClick = {
                                        if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                    },
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                                }
                            }
                            else -> {
                                FilledTonalButton(
                                    onClick = {
                                        if (enteredPin.length < 4) {
                                            enteredPin += key
                                            errorMsg = null
                                            if (enteredPin.length == 4) {
                                                val ok = onVerifyPin(enteredPin)
                                                if (!ok) {
                                                    errorMsg = "Invalid PIN"
                                                    enteredPin = ""
                                                }
                                            }
                                        }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Text(key, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
