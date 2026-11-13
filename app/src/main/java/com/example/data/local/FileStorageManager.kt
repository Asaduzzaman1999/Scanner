package com.example.data.local

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.core.content.FileProvider
import com.example.core.model.DocumentTemplate
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages the on-device PDF Maker/ directory hierarchy, meaningful auto-naming,
 * thumbnails, private vault isolation, and automatic temporary cache cleanup.
 */
class FileStorageManager(private val context: Context) {

    companion object {
        const val ROOT_DIR_NAME = "PDF Maker"
        val DEFAULT_FOLDERS = listOf(
            "Scanned Documents",
            "NID",
            "Passport",
            "Certificates",
            "Receipts",
            "Images"
        )
        private const val TRASH_DIR = "Trash"
        private const val PRIVATE_DIR = "Private"
        private const val THUMBS_DIR = ".thumbnails"
        private const val TEMP_DIR = "scan_temp"
    }

    val rootDir: File
        get() = File(context.filesDir, ROOT_DIR_NAME).apply { if (!exists()) mkdirs() }

    val tempDir: File
        get() = File(context.cacheDir, TEMP_DIR).apply { if (!exists()) mkdirs() }

    val thumbsDir: File
        get() = File(rootDir, THUMBS_DIR).apply { if (!exists()) mkdirs() }

    val trashDir: File
        get() = File(rootDir, TRASH_DIR).apply { if (!exists()) mkdirs() }

    val privateDir: File
        get() = File(rootDir, PRIVATE_DIR).apply { if (!exists()) mkdirs() }

    fun ensureDefaultDirectories() {
        DEFAULT_FOLDERS.forEach { folderName ->
            getFolderDir(folderName)
        }
        trashDir
        privateDir
        thumbsDir
        tempDir
    }

    fun getFolderDir(folderName: String): File {
        val safeName = folderName.replace(Regex("[^a-zA-Z0-9 _-]"), "_").trim()
            .ifEmpty { "Scanned Documents" }
        return File(rootDir, safeName).apply { if (!exists()) mkdirs() }
    }

    fun createUniquePdfFile(folderName: String, requestedName: String): File {
        val dir = getFolderDir(folderName)
        val cleanBase = requestedName
            .removeSuffix(".pdf")
            .removeSuffix(".PDF")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifEmpty { "Scan_${currentDateStamp()}" }

        var candidate = File(dir, "$cleanBase.pdf")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${cleanBase}_$counter.pdf")
            counter++
        }
        return candidate
    }

    fun suggestFileName(template: DocumentTemplate, existingCount: Int = 0): String {
        val date = currentDateStamp()
        return when (template) {
            DocumentTemplate.NORMAL -> {
                val seq = (existingCount + 1).toString().padStart(3, '0')
                "Scan_$seq"
            }
            else -> "${template.filePrefix}_$date"
        }
    }

    fun createTempImageFile(prefix: String = "page"): File {
        return File(tempDir, "${prefix}_${System.currentTimeMillis()}_${(100..999).random()}.jpg")
    }

    fun createDraftPageFile(pageId: String): File {
        val draftsDir = File(rootDir, ".draft_pages").apply { if (!exists()) mkdirs() }
        return File(draftsDir, "$pageId.jpg")
    }

    fun createThumbnailFile(docTimestamp: Long): File {
        return File(thumbsDir, "thumb_${docTimestamp}_${(100..999).random()}.jpg")
    }

    fun moveFileToFolder(sourcePath: String, targetFolder: String): File {
        val src = File(sourcePath)
        if (!src.exists()) return src
        val destDir = getFolderDir(targetFolder)
        var dest = File(destDir, src.name)
        var idx = 1
        while (dest.exists() && dest.absolutePath != src.absolutePath) {
            val base = src.nameWithoutExtension
            dest = File(destDir, "${base}_$idx.${src.extension}")
            idx++
        }
        if (src.absolutePath != dest.absolutePath) {
            if (!src.renameTo(dest)) {
                src.copyTo(dest, overwrite = true)
                src.delete()
            }
        }
        return dest
    }

    fun moveToTrash(sourcePath: String): File {
        val src = File(sourcePath)
        if (!src.exists()) return src
        val dest = File(trashDir, "${System.currentTimeMillis()}_${src.name}")
        if (!src.renameTo(dest)) {
            src.copyTo(dest, overwrite = true)
            src.delete()
        }
        return dest
    }

    fun moveToPrivateVault(sourcePath: String): File {
        val src = File(sourcePath)
        if (!src.exists()) return src
        val dest = File(privateDir, "${System.currentTimeMillis()}_${src.name}")
        if (!src.renameTo(dest)) {
            src.copyTo(dest, overwrite = true)
            src.delete()
        }
        return dest
    }

    fun deleteFileSafely(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val f = File(path)
            if (f.exists()) f.delete()
        }
    }

    /**
     * Securely cleans temporary scan buffers older than the active session
     * or when requested by the user.
     */
    fun cleanTempCache(maxAgeMillis: Long = 0L): Long {
        var freed = 0L
        tempDir.listFiles()?.forEach { file ->
            val age = System.currentTimeMillis() - file.lastModified()
            if (maxAgeMillis <= 0L || age > maxAgeMillis) {
                freed += file.length()
                file.delete()
            }
        }
        return freed
    }

    fun clearDraftPages() {
        val draftsDir = File(rootDir, ".draft_pages")
        if (draftsDir.exists()) {
            draftsDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun hasEnoughStorage(requiredBytes: Long = 10L * 1024L * 1024L): Boolean {
        return runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes >= requiredBytes
        }.getOrDefault(true)
    }

    fun getFileProviderUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun currentDateStamp(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
