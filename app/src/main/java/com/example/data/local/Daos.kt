package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE isTrashed = 0 AND isPrivate = 0 ORDER BY lastOpenedAt DESC")
    fun observeActiveDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isTrashed = 0 AND isPrivate = 1 ORDER BY modifiedAt DESC")
    fun observePrivateDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isTrashed = 0 AND isPrivate = 0 AND isFavorite = 1 ORDER BY modifiedAt DESC")
    fun observeFavoriteDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isTrashed = 1 ORDER BY modifiedAt DESC")
    fun observeTrashedDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isTrashed = 0 AND isPrivate = 0 ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeRecentDocuments(limit: Int = 8): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<DocumentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: DocumentEntity): Long

    @Update
    suspend fun update(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM documents WHERE isTrashed = 1")
    suspend fun getAllTrashedOnce(): List<DocumentEntity>

    @Query("DELETE FROM documents WHERE isTrashed = 1")
    suspend fun deleteAllTrashed()
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY isSystemDefault DESC, name ASC")
    fun observeAllFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(folders: List<FolderEntity>)

    @Query("DELETE FROM folders WHERE name = :name AND isSystemDefault = 0")
    suspend fun deleteCustomFolder(name: String)
}

@Dao
interface ScanDraftDao {
    @Query("SELECT * FROM scan_drafts WHERE draftId = 'active_scan_draft' LIMIT 1")
    fun observeActiveDraft(): Flow<ScanDraftEntity?>

    @Query("SELECT * FROM scan_drafts WHERE draftId = 'active_scan_draft' LIMIT 1")
    suspend fun getActiveDraftOnce(): ScanDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: ScanDraftEntity)

    @Query("DELETE FROM scan_drafts WHERE draftId = 'active_scan_draft'")
    suspend fun clearDraft()
}
