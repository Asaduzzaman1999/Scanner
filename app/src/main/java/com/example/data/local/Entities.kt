package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val thumbnailPath: String? = null,
    val folderName: String = "Scanned Documents",
    val templateId: String = "normal",
    val pageCount: Int = 1,
    val fileSizeBytes: Long = 0L,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val isPrivate: Boolean = false,
    val isPasswordProtected: Boolean = false,
    val ocrTextIndex: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val name: String,
    val isSystemDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Persists unfinished batch/single scans so users never lose scanned pages
 * if they exit the app or switch tasks unexpectedly.
 */
@Entity(tableName = "scan_drafts")
data class ScanDraftEntity(
    @PrimaryKey val draftId: String = "active_scan_draft",
    val templateId: String = "normal",
    val pagesJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis()
)
