package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DocScanApplication
import com.example.core.model.AppLanguage
import com.example.core.model.CompressionPreset
import com.example.core.model.DocumentTemplate
import com.example.core.model.PageSizeOption
import com.example.core.model.ScanPageItem
import com.example.core.model.SortOrder
import com.example.core.model.ThemeMode
import com.example.data.local.DocumentEntity
import com.example.data.local.FolderEntity
import com.example.data.local.UserPreferences
import com.example.security.AppLockManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DocScanApplication
    val repository = app.repository
    val preferences = app.preferences
    val fileStorage = app.fileStorage
    val adManager = app.adManager

    val userPrefs: StateFlow<UserPreferences> = preferences.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private val _isPrivateVaultUnlocked = MutableStateFlow(false)
    val isPrivateVaultUnlocked: StateFlow<Boolean> = _isPrivateVaultUnlocked.asStateFlow()

    val recentDocuments: StateFlow<List<DocumentEntity>> = repository.recentDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocuments: StateFlow<List<DocumentEntity>> = repository.activeDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val privateDocuments: StateFlow<List<DocumentEntity>> = repository.privateDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDocuments: StateFlow<List<DocumentEntity>> = repository.favoriteDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedDocuments: StateFlow<List<DocumentEntity>> = repository.trashedDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> = repository.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDraft: StateFlow<Pair<DocumentTemplate, List<ScanPageItem>>?> = repository.activeDraft
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // My Documents filtering & sorting state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _selectedTemplateFilter = MutableStateFlow<String?>(null)
    val selectedTemplateFilter: StateFlow<String?> = _selectedTemplateFilter.asStateFlow()

    val filteredDocuments: StateFlow<List<DocumentEntity>> = combine(
        allDocuments,
        _searchQuery,
        _selectedFolder,
        _sortOrder,
        _selectedTemplateFilter
    ) { docs, query, folder, sort, templateFilter ->
        val filtered = docs.filter { doc ->
            val matchesQuery = query.isBlank() ||
                doc.title.contains(query, ignoreCase = true) ||
                doc.ocrTextIndex.contains(query, ignoreCase = true) ||
                doc.folderName.contains(query, ignoreCase = true)
            val matchesFolder = folder == null || doc.folderName.equals(folder, ignoreCase = true)
            val matchesTemplate = templateFilter == null || doc.templateId == templateFilter
            matchesQuery && matchesFolder && matchesTemplate
        }
        when (sort) {
            SortOrder.DATE_DESC -> filtered.sortedByDescending { it.modifiedAt }
            SortOrder.DATE_ASC -> filtered.sortedBy { it.modifiedAt }
            SortOrder.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.fileSizeBytes }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun showStatus(msg: String) {
        _statusMessage.value = msg
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setSelectedFolder(folder: String?) { _selectedFolder.value = folder }
    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }
    fun setTemplateFilter(templateId: String?) { _selectedTemplateFilter.value = templateId }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { preferences.setLanguage(lang) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { preferences.setOnboardingCompleted(true) }
    }

    fun setDefaultPageSize(size: PageSizeOption) {
        viewModelScope.launch { preferences.setDefaultPageSize(size) }
    }

    fun setDefaultCompression(preset: CompressionPreset) {
        viewModelScope.launch { preferences.setDefaultCompression(preset) }
    }

    fun setPin(pin: String?) {
        viewModelScope.launch {
            preferences.setPinHash(pin?.let { AppLockManager.hashPin(it) })
            if (pin == null) _isAppUnlocked.value = true
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setBiometricEnabled(enabled) }
    }

    fun verifyAndUnlockApp(pin: String): Boolean {
        val ok = AppLockManager.verifyPin(pin, userPrefs.value.pinHash)
        if (ok) _isAppUnlocked.value = true
        return ok
    }

    fun unlockWithBiometric() {
        _isAppUnlocked.value = true
    }

    fun unlockPrivateVault() {
        _isPrivateVaultUnlocked.value = true
    }

    fun createFolder(name: String) {
        viewModelScope.launch { repository.createCustomFolder(name) }
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch { repository.toggleFavorite(doc) }
    }

    fun renameDocument(doc: DocumentEntity, newName: String) {
        viewModelScope.launch { repository.renameDocument(doc, newName) }
    }

    fun duplicateDocument(doc: DocumentEntity) {
        viewModelScope.launch { repository.duplicateDocument(doc) }
    }

    fun moveDocumentToFolder(doc: DocumentEntity, folder: String) {
        viewModelScope.launch { repository.moveToFolder(doc, folder) }
    }

    fun togglePrivateVault(doc: DocumentEntity) {
        viewModelScope.launch { repository.togglePrivateVault(doc) }
    }

    fun moveToTrash(doc: DocumentEntity) {
        viewModelScope.launch { repository.moveToTrash(doc) }
    }

    fun restoreFromTrash(doc: DocumentEntity) {
        viewModelScope.launch { repository.restoreFromTrash(doc) }
    }

    fun permanentlyDelete(doc: DocumentEntity) {
        viewModelScope.launch { repository.permanentlyDelete(doc) }
    }

    fun emptyTrash() {
        viewModelScope.launch { repository.emptyTrash() }
    }

    fun markOpened(doc: DocumentEntity) {
        viewModelScope.launch { repository.markOpened(doc) }
    }

    fun discardDraft() {
        viewModelScope.launch { repository.clearScanDraft() }
    }

    fun cleanTempCache(onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val freed = fileStorage.cleanTempCache(0L)
            onDone(freed)
        }
    }

    fun createSharePdfIntent(doc: DocumentEntity): Intent? {
        val file = File(doc.filePath)
        if (!file.exists()) return null
        val uri = fileStorage.getFileProviderUri(file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, doc.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createShareMultipleImagesIntent(uris: List<Uri>): Intent {
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
