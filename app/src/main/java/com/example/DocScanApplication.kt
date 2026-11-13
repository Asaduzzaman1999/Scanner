package com.example

import android.app.Application
import com.example.ads.AdManager
import com.example.data.local.AppDatabase
import com.example.data.local.FileStorageManager
import com.example.data.local.PreferencesRepository
import com.example.data.repository.DocumentRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DocScanApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set
    lateinit var fileStorage: FileStorageManager
        private set
    lateinit var repository: DocumentRepository
        private set
    lateinit var preferences: PreferencesRepository
        private set
    lateinit var adManager: AdManager
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)

        database = AppDatabase.getInstance(this)
        fileStorage = FileStorageManager(this)
        repository = DocumentRepository(database, fileStorage)
        preferences = PreferencesRepository(this)
        adManager = AdManager(this)

        appScope.launch {
            repository.initializeDefaults()
        }
        adManager.initialize()
    }
}
