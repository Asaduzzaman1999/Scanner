package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ads.TestBannerAdView
import com.example.core.i18n.LocalAppStrings
import com.example.core.model.SortOrder
import com.example.data.local.DocumentEntity
import com.example.data.local.FolderEntity
import com.example.processing.pdf.PdfPrintHelper
import com.example.ui.components.DocumentCard
import com.example.ui.components.EmptyStateIllustration
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDocumentsScreen(
    documents: List<DocumentEntity>,
    privateDocuments: List<DocumentEntity>,
    folders: List<FolderEntity>,
    searchQuery: String,
    selectedFolder: String?,
    sortOrder: SortOrder,
    onSearchChange: (String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onChangeSort: (SortOrder) -> Unit,
    onCreateFolder: (String) -> Unit,
    onOpenDocument: (DocumentEntity) -> Unit,
    onEditDocument: (DocumentEntity) -> Unit,
    onPdfToImages: (DocumentEntity) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity, String) -> Unit,
    onDuplicateDocument: (DocumentEntity) -> Unit,
    onMoveDocumentFolder: (DocumentEntity, String) -> Unit,
    onTogglePrivate: (DocumentEntity) -> Unit,
    onDeleteToTrash: (DocumentEntity) -> Unit,
    onMergeSelected: (List<DocumentEntity>, String) -> Unit,
    onSharePdf: (DocumentEntity) -> Intent?,
    onBack: () -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showPrivateVault by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Long>() }
    var renameTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var moveTarget by remember { mutableStateOf<DocumentEntity?>(null) }

    val displayedDocs = if (showPrivateVault) privateDocuments else documents

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (selectionMode) "${selectedIds.size} selected" else if (showPrivateVault) s.privateVault else s.myDocuments)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectionMode = false
                            selectedIds.clear()
                        } else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectionMode) {
                        if (selectedIds.size >= 2) {
                            IconButton(onClick = {
                                val chosen = displayedDocs.filter { selectedIds.contains(it.id) }
                                onMergeSelected(chosen, "Merged_${System.currentTimeMillis() % 10000}")
                                selectionMode = false
                                selectedIds.clear()
                            }) {
                                Icon(Icons.Filled.MergeType, contentDescription = s.mergePdf)
                            }
                        }
                        IconButton(onClick = {
                            displayedDocs.filter { selectedIds.contains(it.id) }.forEach(onDeleteToTrash)
                            selectionMode = false
                            selectedIds.clear()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = s.deleteToTrash)
                        }
                    } else {
                        IconButton(onClick = { showPrivateVault = !showPrivateVault }) {
                            Icon(
                                if (showPrivateVault) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                contentDescription = s.privateVault
                            )
                        }
                        IconButton(onClick = { showNewFolderDialog = true }) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = s.createFolder)
                        }
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = s.sortBy)
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(s.sortDateNewest) },
                                    onClick = { sortMenuOpen = false; onChangeSort(SortOrder.DATE_DESC) }
                                )
                                DropdownMenuItem(
                                    text = { Text(s.sortDateOldest) },
                                    onClick = { sortMenuOpen = false; onChangeSort(SortOrder.DATE_ASC) }
                                )
                                DropdownMenuItem(
                                    text = { Text(s.sortName) },
                                    onClick = { sortMenuOpen = false; onChangeSort(SortOrder.NAME_ASC) }
                                )
                                DropdownMenuItem(
                                    text = { Text(s.sortSize) },
                                    onClick = { sortMenuOpen = false; onChangeSort(SortOrder.SIZE_DESC) }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            TestBannerAdView(modifier = Modifier.navigationBarsPadding())
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(s.searchDocuments) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("mydocs_search_input")
            )

            if (!showPrivateVault) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFolder == null,
                            onClick = { onSelectFolder(null) },
                            label = { Text(s.allFolders) }
                        )
                    }
                    items(folders) { folder ->
                        FilterChip(
                            selected = selectedFolder == folder.name,
                            onClick = { onSelectFolder(folder.name) },
                            label = { Text(folder.name) }
                        )
                    }
                }
            }

            if (displayedDocs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyStateIllustration(title = s.noRecentDocs, subtitle = s.noRecentDocsHint)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedDocs, key = { it.id }) { doc ->
                        val isSel = selectedIds.contains(doc.id)
                        DocumentCard(
                            doc = doc,
                            isSelected = isSel,
                            inSelectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) {
                                    if (isSel) selectedIds.remove(doc.id) else selectedIds.add(doc.id)
                                } else {
                                    onOpenDocument(doc)
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                if (!selectedIds.contains(doc.id)) selectedIds.add(doc.id)
                            },
                            onToggleFavorite = { onToggleFavorite(doc) },
                            onSharePdf = {
                                onSharePdf(doc)?.let { context.startActivity(Intent.createChooser(it, s.sharePdf)) }
                            },
                            onRename = { renameTarget = doc },
                            onDuplicate = { onDuplicateDocument(doc) },
                            onMoveFolder = { moveTarget = doc },
                            onEditPdf = { onEditDocument(doc) },
                            onPdfToImages = { onPdfToImages(doc) },
                            onPrint = { PdfPrintHelper.printPdf(context, File(doc.filePath), doc.title) },
                            onTogglePrivate = { onTogglePrivate(doc) },
                            onDelete = { onDeleteToTrash(doc) }
                        )
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(s.createFolder) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text(s.folderName) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onCreateFolder(folderName)
                    showNewFolderDialog = false
                }) { Text(s.confirm) }
            },
            dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text(s.cancel) } }
        )
    }

    renameTarget?.let { target ->
        var name by remember { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(s.renamePdf) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                Button(onClick = { onRenameDocument(target, name); renameTarget = null }) { Text(s.save) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(s.cancel) } }
        )
    }

    moveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text(s.moveToFolder) },
            text = {
                Column {
                    folders.forEach { folder ->
                        TextButton(
                            onClick = { onMoveDocumentFolder(target, folder.name); moveTarget = null },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(folder.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moveTarget = null }) { Text(s.cancel) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favorites: List<DocumentEntity>,
    onOpenDocument: (DocumentEntity) -> Unit,
    onEditDocument: (DocumentEntity) -> Unit,
    onPdfToImages: (DocumentEntity) -> Unit,
    onToggleFavorite: (DocumentEntity) -> Unit,
    onDuplicateDocument: (DocumentEntity) -> Unit,
    onTogglePrivate: (DocumentEntity) -> Unit,
    onDeleteToTrash: (DocumentEntity) -> Unit,
    onSharePdf: (DocumentEntity) -> Intent?,
    onBack: () -> Unit
) {
    val s = LocalAppStrings.current
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(s.favorites) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateIllustration(title = s.favorites, subtitle = s.emptyFavoritesHint)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(favorites, key = { it.id }) { doc ->
                    DocumentCard(
                        doc = doc,
                        onClick = { onOpenDocument(doc) },
                        onToggleFavorite = { onToggleFavorite(doc) },
                        onSharePdf = {
                            onSharePdf(doc)?.let { context.startActivity(Intent.createChooser(it, s.sharePdf)) }
                        },
                        onRename = {},
                        onDuplicate = { onDuplicateDocument(doc) },
                        onMoveFolder = {},
                        onEditPdf = { onEditDocument(doc) },
                        onPdfToImages = { onPdfToImages(doc) },
                        onPrint = { PdfPrintHelper.printPdf(context, File(doc.filePath), doc.title) },
                        onTogglePrivate = { onTogglePrivate(doc) },
                        onDelete = { onDeleteToTrash(doc) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    trashedDocs: List<DocumentEntity>,
    onRestore: (DocumentEntity) -> Unit,
    onPermanentDelete: (DocumentEntity) -> Unit,
    onEmptyTrash: () -> Unit,
    onBack: () -> Unit
) {
    val s = LocalAppStrings.current
    var confirmEmptyAll by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(s.trash) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (trashedDocs.isNotEmpty()) {
                        TextButton(onClick = { confirmEmptyAll = true }) {
                            Text(s.deleteAll, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (trashedDocs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateIllustration(title = s.trash, subtitle = s.emptyTrashHint)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(trashedDocs, key = { it.id }) { doc ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.title, style = MaterialTheme.typography.titleMedium)
                                Text(s.pageCount(doc.pageCount), style = MaterialTheme.typography.bodyMedium)
                            }
                            TextButton(onClick = { onRestore(doc) }) {
                                Text(s.restore)
                            }
                            IconButton(onClick = { onPermanentDelete(doc) }) {
                                Icon(Icons.Filled.DeleteForever, contentDescription = s.deletePermanently, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmptyAll) {
        AlertDialog(
            onDismissRequest = { confirmEmptyAll = false },
            title = { Text(s.deleteAll) },
            text = { Text(s.emptyTrashHint) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onEmptyTrash()
                        confirmEmptyAll = false
                    }
                ) { Text(s.deletePermanently) }
            },
            dismissButton = { TextButton(onClick = { confirmEmptyAll = false }) { Text(s.cancel) } }
        )
    }
}
