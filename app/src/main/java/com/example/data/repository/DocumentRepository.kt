package com.example.data.repository

import com.example.core.model.BackupStorageProvider
import com.example.core.model.DocumentTemplate
import com.example.core.model.ScanPageItem
import com.example.data.local.AppDatabase
import com.example.data.local.DocumentEntity
import com.example.data.local.FileStorageManager
import com.example.data.local.FolderEntity
import com.example.data.local.ScanDraftEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class DocumentRepository(
    private val db: AppDatabase,
    val fileStorage: FileStorageManager,
    private val backupProvider: BackupStorageProvider? = null
) {
    private val docDao = db.documentDao()
    private val folderDao = db.folderDao()
    private val draftDao = db.scanDraftDao()

    private val json = Json { ignoreUnknownKeys = true }

    val activeDocuments: Flow<List<DocumentEntity>> = docDao.observeActiveDocuments()
    val privateDocuments: Flow<List<DocumentEntity>> = docDao.observePrivateDocuments()
    val favoriteDocuments: Flow<List<DocumentEntity>> = docDao.observeFavoriteDocuments()
    val trashedDocuments: Flow<List<DocumentEntity>> = docDao.observeTrashedDocuments()
    val recentDocuments: Flow<List<DocumentEntity>> = docDao.observeRecentDocuments(8)
    val folders: Flow<List<FolderEntity>> = folderDao.observeAllFolders()

    val activeDraft: Flow<Pair<DocumentTemplate, List<ScanPageItem>>?> =
        draftDao.observeActiveDraft().map { entity ->
            if (entity == null) null
            else {
                val template = DocumentTemplate.entries.find { it.id == entity.templateId }
                    ?: DocumentTemplate.NORMAL
                val pages = runCatching {
                    json.decodeFromString<List<ScanPageItem>>(entity.pagesJson)
                }.getOrDefault(emptyList()).filter { File(it.processedImagePath).exists() || File(it.originalImagePath).exists() }
                if (pages.isEmpty()) null else template to pages
            }
        }

    suspend fun initializeDefaults() {
        fileStorage.ensureDefaultDirectories()
        val defaults = FileStorageManager.DEFAULT_FOLDERS.map {
            FolderEntity(name = it, isSystemDefault = true)
        }
        folderDao.insertAll(defaults)
        // Clean stale temp files older than 24 hours automatically
        fileStorage.cleanTempCache(maxAgeMillis = 24L * 60L * 60L * 1000L)
    }

    suspend fun saveDocumentRecord(doc: DocumentEntity): Long {
        return docDao.insert(doc)
    }

    suspend fun getDocumentById(id: Long): DocumentEntity? = docDao.getById(id)

    fun observeDocumentById(id: Long): Flow<DocumentEntity?> = docDao.observeById(id)

    suspend fun markOpened(doc: DocumentEntity) {
        docDao.update(doc.copy(lastOpenedAt = System.currentTimeMillis()))
    }

    suspend fun toggleFavorite(doc: DocumentEntity) {
        docDao.update(doc.copy(isFavorite = !doc.isFavorite, modifiedAt = System.currentTimeMillis()))
    }

    suspend fun renameDocument(doc: DocumentEntity, newTitle: String) {
        val clean = newTitle.removeSuffix(".pdf").removeSuffix(".PDF").trim()
        if (clean.isEmpty()) return
        val oldFile = File(doc.filePath)
        val newFile = if (oldFile.exists()) {
            val target = File(oldFile.parentFile, "$clean.pdf")
            if (!target.exists() && oldFile.renameTo(target)) target else oldFile
        } else oldFile

        docDao.update(
            doc.copy(
                title = clean,
                filePath = newFile.absolutePath,
                modifiedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun duplicateDocument(doc: DocumentEntity): Long? {
        val src = File(doc.filePath)
        if (!src.exists()) return null
        val copyFile = fileStorage.createUniquePdfFile(doc.folderName, "${doc.title}_Copy")
        src.copyTo(copyFile, overwrite = true)
        val thumbCopy = doc.thumbnailPath?.let { thumb ->
            val srcThumb = File(thumb)
            if (srcThumb.exists()) {
                fileStorage.createThumbnailFile(System.currentTimeMillis()).also {
                    srcThumb.copyTo(it, overwrite = true)
                }.absolutePath
            } else null
        }
        return docDao.insert(
            doc.copy(
                id = 0,
                title = copyFile.nameWithoutExtension,
                filePath = copyFile.absolutePath,
                thumbnailPath = thumbCopy,
                fileSizeBytes = copyFile.length(),
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                lastOpenedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun moveToFolder(doc: DocumentEntity, targetFolder: String) {
        val moved = fileStorage.moveFileToFolder(doc.filePath, targetFolder)
        docDao.update(
            doc.copy(
                folderName = targetFolder,
                filePath = moved.absolutePath,
                isPrivate = false,
                modifiedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun togglePrivateVault(doc: DocumentEntity) {
        if (!doc.isPrivate) {
            val moved = fileStorage.moveToPrivateVault(doc.filePath)
            docDao.update(
                doc.copy(
                    isPrivate = true,
                    filePath = moved.absolutePath,
                    modifiedAt = System.currentTimeMillis()
                )
            )
        } else {
            val restored = fileStorage.moveFileToFolder(doc.filePath, doc.folderName)
            docDao.update(
                doc.copy(
                    isPrivate = false,
                    filePath = restored.absolutePath,
                    modifiedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun moveToTrash(doc: DocumentEntity) {
        val trashedFile = fileStorage.moveToTrash(doc.filePath)
        docDao.update(
            doc.copy(
                isTrashed = true,
                filePath = trashedFile.absolutePath,
                modifiedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun restoreFromTrash(doc: DocumentEntity) {
        val restoredFile = if (doc.isPrivate) {
            fileStorage.moveToPrivateVault(doc.filePath)
        } else {
            fileStorage.moveFileToFolder(doc.filePath, doc.folderName)
        }
        docDao.update(
            doc.copy(
                isTrashed = false,
                filePath = restoredFile.absolutePath,
                modifiedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun permanentlyDelete(doc: DocumentEntity) {
        fileStorage.deleteFileSafely(doc.filePath)
        fileStorage.deleteFileSafely(doc.thumbnailPath)
        docDao.deleteById(doc.id)
    }

    suspend fun emptyTrash() {
        val trashed = docDao.getAllTrashedOnce()
        trashed.forEach { doc ->
            fileStorage.deleteFileSafely(doc.filePath)
            fileStorage.deleteFileSafely(doc.thumbnailPath)
        }
        docDao.deleteAllTrashed()
    }

    suspend fun updateDocumentMetadata(doc: DocumentEntity) {
        docDao.update(doc.copy(modifiedAt = System.currentTimeMillis()))
    }

    suspend fun createCustomFolder(name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty()) {
            fileStorage.getFolderDir(clean)
            folderDao.insert(FolderEntity(name = clean, isSystemDefault = false))
        }
    }

    suspend fun saveScanDraft(template: DocumentTemplate, pages: List<ScanPageItem>) {
        if (pages.isEmpty()) {
            draftDao.clearDraft()
            return
        }
        draftDao.saveDraft(
            ScanDraftEntity(
                draftId = "active_scan_draft",
                templateId = template.id,
                pagesJson = json.encodeToString(pages),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearScanDraft() {
        draftDao.clearDraft()
        fileStorage.clearDraftPages()
    }

    suspend fun isBackupConnected(): Boolean = backupProvider?.isConnected() == true
}
