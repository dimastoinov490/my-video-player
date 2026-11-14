package com.example.videoplayer.archive

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.provider.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.sf.sevenzipjbinding.ArchiveFormat
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IArchiveOpenCallback
import net.sf.sevenzipjbinding.IArchiveOpenVolumeCallback
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import net.sf.sevenzipjbinding.impl.VolumedArchiveInStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object ArchiveManager {
    private const val PASSWORD_MARKER = "__NEXORA_PASSWORD_REQUIRED__"
    private const val CANCELLED_MARKER = "__NEXORA_CANCELLED__"

    private val initialized = AtomicBoolean(false)
    private val initLock = Any()

    fun ensureInitialized() {
        if (initialized.get()) return
        synchronized(initLock) {
            if (initialized.get()) return
            try {
                SevenZip.initSevenZipFromPlatformJAR()
                initialized.set(true)
            } catch (e: SevenZipNativeInitializationException) {
                throw IllegalStateException("Не удалось инициализировать нативный движок архивов: ${e.message}", e)
            }
        }
    }

    fun hasAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    fun buildAllFilesAccessIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun resolveDisplayName(context: Context, uri: Uri): String {
        try {
            if (uri.scheme == "file") {
                val pathName = uri.path?.substringAfterLast('/')
                if (!pathName.isNullOrBlank()) return pathName
            }
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Throwable) {}
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "archive" } ?: "archive"
    }

    fun defaultExtractDir(archiveName: String): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val base = archiveName.substringBeforeLast('.').ifBlank { "Archive" }
        return File(File(downloads, "NexoraExtracted"), base)
    }

    suspend fun inspectArchive(
        context: Context,
        source: ArchiveSource,
        password: String? = null
    ): ArchiveInspectResult = withContext(Dispatchers.IO) {
        ensureInitialized()
        openSession(context, source, password).use { session ->
            val archive = session.archive
            val itemCount = archive.numberOfItems
            val entries = ArrayList<ArchiveEntryItem>(itemCount)
            var anyEncrypted = false

            for (i in 0 until itemCount) {
                val rawPath = (archive.getProperty(i, PropID.PATH) as? String)
                    ?.replace('\\', '/')
                    ?.trim('/')
                    ?.ifBlank { null }
                    ?: "item_$i"
                val isDir = (archive.getProperty(i, PropID.IS_FOLDER) as? Boolean) ?: false
                val size = (archive.getProperty(i, PropID.SIZE) as? Number)?.toLong() ?: 0L
                val packed = (archive.getProperty(i, PropID.PACKED_SIZE) as? Number)?.toLong() ?: 0L
                val encrypted = (archive.getProperty(i, PropID.ENCRYPTED) as? Boolean) ?: false
                if (encrypted) anyEncrypted = true

                val mtime = (archive.getProperty(i, PropID.LAST_MODIFICATION_TIME) as? Date)?.time ?: 0L
                val crcNum = (archive.getProperty(i, PropID.CRC) as? Number)?.toInt()
                val crcStr = if (crcNum != null) String.format(Locale.US, "%08X", crcNum) else ""
                val name = rawPath.substringAfterLast('/')
                val parentPath = if (rawPath.contains('/')) rawPath.substringBeforeLast('/') else ""

                entries.add(
                    ArchiveEntryItem(
                        index = i,
                        path = rawPath,
                        name = name,
                        parentPath = parentPath,
                        isDirectory = isDir,
                        size = size,
                        packedSize = packed,
                        isEncrypted = encrypted,
                        isVideo = !isDir && ArchiveFileUtils.isVideoEntryName(name),
                        lastModifiedMs = mtime,
                        crc = crcStr
                    )
                )
            }

            val formatName = archive.archiveFormat?.name ?: detectFormatLabel(source.displayName)
            val isMultiVolume = ArchiveFileUtils.isMultiVolumeFileName(source.displayName)

            ArchiveInspectResult(
                source = source,
                archiveName = source.displayName,
                formatName = formatName,
                isEncrypted = anyEncrypted || session.callback.passwordRequested,
                isMultiVolume = isMultiVolume,
                entries = entries
            )
        }
    }

    /**
     * Строит содержимое указанной папки внутри архива без распаковки.
     * Синтезирует виртуальные папки, если в архиве сохранены только полные пути файлов.
     */
    fun listFolderEntries(
        allEntries: List<ArchiveEntryItem>,
        currentSubPath: String
    ): List<ArchiveEntryItem> {
        val prefix = currentSubPath.trim('/').let { if (it.isEmpty()) "" else "$it/" }
        val dirs = LinkedHashMap<String, ArchiveEntryItem>()
        val files = ArrayList<ArchiveEntryItem>()

        for (entry in allEntries) {
            val cleanPath = entry.path.trim('/')
            if (cleanPath.isEmpty()) continue
            if (prefix.isNotEmpty() && !cleanPath.startsWith(prefix)) continue

            val relative = if (prefix.isEmpty()) cleanPath else cleanPath.removePrefix(prefix)
            if (relative.isEmpty()) continue

            val slashIdx = relative.indexOf('/')
            if (slashIdx >= 0) {
                val folderName = relative.substring(0, slashIdx)
                val folderPath = "$prefix$folderName"
                if (!dirs.containsKey(folderPath)) {
                    dirs[folderPath] = ArchiveEntryItem(
                        index = -1,
                        path = folderPath,
                        name = folderName,
                        parentPath = currentSubPath.trim('/'),
                        isDirectory = true,
                        size = 0L,
                        packedSize = 0L,
                        isEncrypted = entry.isEncrypted,
                        isVideo = false,
                        lastModifiedMs = entry.lastModifiedMs,
                        crc = "",
                        isVirtualFolder = true
                    )
                }
            } else if (entry.isDirectory) {
                dirs[cleanPath] = entry
            } else {
                files.add(entry)
            }
        }

        val sortedDirs = dirs.values.sortedBy { it.name.lowercase(Locale.getDefault()) }
        val sortedFiles = files.sortedBy { it.name.lowercase(Locale.getDefault()) }
        return sortedDirs + sortedFiles
    }

    fun collectEntryIndicesForFolder(
        allEntries: List<ArchiveEntryItem>,
        folderSubPath: String
    ): IntArray {
        val clean = folderSubPath.trim('/')
        if (clean.isEmpty()) {
            return allEntries.filter { it.index >= 0 }.map { it.index }.toIntArray()
        }
        val prefix = "$clean/"
        return allEntries
            .filter { it.index >= 0 && (it.path.trim('/') == clean || it.path.trim('/').startsWith(prefix)) }
            .map { it.index }
            .toIntArray()
    }

    /**
     * Распаковка выбранных индексов (или всего архива, если entryIndices == null)
     * с потоком прогресса Flow<ExtractionProgress> и кооперативной отменой.
     */
    fun extract(
        context: Context,
        source: ArchiveSource,
        destinationDir: File,
        entryIndices: IntArray? = null,
        password: String? = null,
        stripRootPrefix: String? = null
    ): Flow<ExtractionProgress> = callbackFlow {
        trySend(ExtractionProgress.Preparing)
        val cancelledFlag = AtomicBoolean(false)
        val createdFiles = ArrayList<File>()

        val workerJob = launch(Dispatchers.IO) {
            try {
                ensureInitialized()
                if (!destinationDir.exists() && !destinationDir.mkdirs()) {
                    trySend(ExtractionProgress.Error("Не удалось создать папку: ${destinationDir.absolutePath}"))
                    close()
                    return@launch
                }
                val destCanonical = destinationDir.canonicalPath
                var lastEmitTimeMs = 0L
                var lastPercent = -1

                openSession(context, source, password).use { session ->
                    val archive = session.archive
                    val totalItems = archive.numberOfItems
                    val targetIndices = entryIndices ?: IntArray(totalItems) { it }
                    var totalDeclaredBytes = 0L
                    for (idx in targetIndices) {
                        if (idx in 0 until totalItems) {
                            val sz = (archive.getProperty(idx, PropID.SIZE) as? Number)?.toLong() ?: 0L
                            totalDeclaredBytes += sz.coerceAtLeast(0L)
                        }
                    }

                    var currentEntryPath = ""
                    var currentOutStream: BufferedOutputStream? = null
                    var currentOutFile: File? = null
                    var hadPasswordError = false
                    var opErrorMessage: String? = null
                    val prefixToStrip = stripRootPrefix?.trim('/')?.let { if (it.isEmpty()) "" else "$it/" } ?: ""

                    val extractCallback = object : IArchiveExtractCallback, ICryptoGetTextPassword {
                        private fun ensureActive() {
                            if (cancelledFlag.get() || isClosedForSend) {
                                cancelledFlag.set(true)
                                throw SevenZipException(CANCELLED_MARKER)
                            }
                        }

                        override fun cryptoGetTextPassword(): String {
                            if (password.isNullOrEmpty()) {
                                hadPasswordError = true
                                throw SevenZipException(PASSWORD_MARKER)
                            }
                            return password
                        }

                        override fun setTotal(total: Long) {
                            if (total > 0L) totalDeclaredBytes = total
                        }

                        override fun setCompleted(complete: Long) {
                            ensureActive()
                            val denom = totalDeclaredBytes.coerceAtLeast(1L)
                            val pct = ((complete * 100L) / denom).toInt().coerceIn(0, 100)
                            val now = System.currentTimeMillis()
                            if (pct != lastPercent || now - lastEmitTimeMs >= 100L) {
                                lastPercent = pct
                                lastEmitTimeMs = now
                                trySend(
                                    ExtractionProgress.Progress(
                                        percent = pct,
                                        bytesProcessed = complete,
                                        totalBytes = totalDeclaredBytes,
                                        currentFile = currentEntryPath
                                    )
                                )
                            }
                        }

                        override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
                            ensureActive()
                            closeCurrentStream()
                            if (extractAskMode != ExtractAskMode.EXTRACT) return null

                            val rawPath = (archive.getProperty(index, PropID.PATH) as? String)
                                ?.replace('\\', '/')
                                ?.trim('/')
                                ?.ifBlank { null }
                                ?: "file_$index"
                            val isDir = (archive.getProperty(index, PropID.IS_FOLDER) as? Boolean) ?: false

                            val relPath = if (prefixToStrip.isNotEmpty() && rawPath.startsWith(prefixToStrip)) {
                                rawPath.removePrefix(prefixToStrip).ifBlank { rawPath.substringAfterLast('/') }
                            } else {
                                rawPath
                            }

                            currentEntryPath = relPath
                            val targetFile = File(destinationDir, relPath)
                            if (!targetFile.canonicalPath.startsWith(destCanonical)) {
                                throw SevenZipException("Небезопасный путь в архиве: $rawPath")
                            }

                            if (isDir) {
                                targetFile.mkdirs()
                                return null
                            }

                            targetFile.parentFile?.mkdirs()
                            val bos = BufferedOutputStream(FileOutputStream(targetFile), 64 * 1024)
                            currentOutStream = bos
                            currentOutFile = targetFile
                            createdFiles.add(targetFile)

                            return ISequentialOutStream { data ->
                                ensureActive()
                                bos.write(data)
                                data.size
                            }
                        }

                        override fun prepareOperation(extractAskMode: ExtractAskMode) {
                            ensureActive()
                        }

                        override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
                            closeCurrentStream()
                            if (extractOperationResult != ExtractOperationResult.OK) {
                                currentOutFile?.let {
                                    if (it.exists()) it.delete()
                                    createdFiles.remove(it)
                                }
                                if (extractOperationResult == ExtractOperationResult.WRONG_PASSWORD) {
                                    hadPasswordError = true
                                    throw SevenZipException(PASSWORD_MARKER)
                                } else if (extractOperationResult == ExtractOperationResult.DATAERROR && !password.isNullOrEmpty()) {
                                    hadPasswordError = true
                                    opErrorMessage = "Неверный пароль или повреждённый файл"
                                } else {
                                    opErrorMessage = "Ошибка извлечения: $extractOperationResult"
                                }
                            }
                            currentOutFile = null
                        }

                        fun closeCurrentStream() {
                            try {
                                currentOutStream?.flush()
                                currentOutStream?.close()
                            } catch (_: Throwable) {}
                            currentOutStream = null
                        }
                    }

                    try {
                        archive.extract(targetIndices, false, extractCallback)
                    } finally {
                        extractCallback.closeCurrentStream()
                    }

                    if (cancelledFlag.get()) {
                        createdFiles.forEach { runCatching { if (it.exists()) it.delete() } }
                        trySend(ExtractionProgress.Cancelled)
                    } else if (hadPasswordError) {
                        createdFiles.forEach { runCatching { if (it.exists()) it.delete() } }
                        trySend(
                            ExtractionProgress.Error(
                                message = opErrorMessage ?: "Требуется правильный пароль",
                                requiresPassword = true
                            )
                        )
                    } else if (opErrorMessage != null) {
                        trySend(ExtractionProgress.Error(opErrorMessage!!))
                    } else {
                        if (createdFiles.isNotEmpty()) {
                            MediaScannerConnection.scanFile(
                                context,
                                createdFiles.map { it.absolutePath }.toTypedArray(),
                                null,
                                null
                            )
                        }
                        trySend(ExtractionProgress.Progress(100, totalDeclaredBytes, totalDeclaredBytes, currentEntryPath))
                        trySend(ExtractionProgress.Completed(createdFiles.toList(), destinationDir.absolutePath))
                    }
                }
            } catch (e: ArchivePasswordRequiredException) {
                createdFiles.forEach { runCatching { if (it.exists()) it.delete() } }
                trySend(ExtractionProgress.Error(e.message ?: "Требуется пароль", requiresPassword = true))
            } catch (e: SevenZipException) {
                createdFiles.forEach { runCatching { if (it.exists()) it.delete() } }
                val msg = e.message.orEmpty()
                when {
                    cancelledFlag.get() || msg.contains(CANCELLED_MARKER) -> trySend(ExtractionProgress.Cancelled)
                    isPasswordError(e) -> trySend(ExtractionProgress.Error("Неверный пароль или архив зашифрован", requiresPassword = true))
                    else -> trySend(ExtractionProgress.Error("Ошибка архиватора: ${e.message ?: "Неизвестная ошибка"}"))
                }
            } catch (e: CancellationException) {
                cancelledFlag.set(true)
                createdFiles.forEach { runCatching { if (it.exists()) it.delete() } }
                trySend(ExtractionProgress.Cancelled)
            } catch (t: Throwable) {
                createdFiles.forEach { runCatching { if (it.exists()) it.delete() } }
                trySend(ExtractionProgress.Error(t.message ?: "Ошибка распаковки архива"))
            }
            close()
        }

        awaitClose {
            cancelledFlag.set(true)
            workerJob.cancel()
        }
    }

    /**
     * Потоково извлекает один файл из архива (блокирующий вызов для фонового потока ArchiveStreamServer).
     */
    fun extractSingleEntryToFile(
        context: Context,
        source: ArchiveSource,
        entryIndex: Int,
        outputFile: File,
        password: String?,
        isCancelled: () -> Boolean,
        onBytesWritten: (Long) -> Unit
    ) {
        ensureInitialized()
        outputFile.parentFile?.mkdirs()

        openSession(context, source, password).use { session ->
            val archive = session.archive
            var writtenTotal = 0L
            var fos: BufferedOutputStream? = null
            var opResult: ExtractOperationResult = ExtractOperationResult.OK

            val callback = object : IArchiveExtractCallback, ICryptoGetTextPassword {
                private fun checkAbort() {
                    if (isCancelled()) throw SevenZipException(CANCELLED_MARKER)
                }

                override fun cryptoGetTextPassword(): String {
                    if (password.isNullOrEmpty()) throw SevenZipException(PASSWORD_MARKER)
                    return password
                }

                override fun setTotal(total: Long) {}
                override fun setCompleted(complete: Long) {
                    checkAbort()
                }

                override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
                    checkAbort()
                    if (extractAskMode != ExtractAskMode.EXTRACT || index != entryIndex) return null
                    val out = BufferedOutputStream(FileOutputStream(outputFile), 64 * 1024)
                    fos = out
                    return ISequentialOutStream { data ->
                        checkAbort()
                        out.write(data)
                        writtenTotal += data.size
                        // Периодически сбрасываем в файл, чтобы HTTP Range сервер мог сразу отдавать данные VLC
                        if (writtenTotal % (256 * 1024) < data.size) {
                            out.flush()
                        }
                        onBytesWritten(writtenTotal)
                        data.size
                    }
                }

                override fun prepareOperation(extractAskMode: ExtractAskMode) {
                    checkAbort()
                }

                override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
                    opResult = extractOperationResult
                    try {
                        fos?.flush()
                        fos?.close()
                    } catch (_: Throwable) {}
                    fos = null
                }
            }

            try {
                archive.extract(intArrayOf(entryIndex), false, callback)
            } finally {
                try {
                    fos?.flush()
                    fos?.close()
                } catch (_: Throwable) {}
            }

            if (isCancelled()) throw ArchiveCancelledException()
            if (opResult == ExtractOperationResult.WRONG_PASSWORD) {
                throw ArchivePasswordRequiredException("Неверный пароль для видеофайла")
            }
            if (opResult != ExtractOperationResult.OK) {
                throw IOException("Не удалось извлечь видео из архива ($opResult)")
            }
        }
    }

    private fun openSession(
        context: Context,
        source: ArchiveSource,
        password: String?
    ): ArchiveOpenSession {
        val openedStreams = ArrayList<Closeable>()
        val openCallback = OpenArchiveCallback(source, password, openedStreams)

        try {
            val primaryStream: IInStream = when (source) {
                is ArchiveSource.FilePath -> {
                    val file = File(source.path)
                    if (!file.exists()) {
                        throw IOException("Файл не найден: ${source.path}")
                    }
                    if (!file.canRead()) {
                        throw SecurityException("Нет доступа к файлу: ${source.path}")
                    }
                    if (file.name.endsWith(".001", ignoreCase = true)) {
                        VolumedArchiveInStream(file.absolutePath, openCallback).also {
                            openedStreams.add(it)
                        }
                    } else {
                        val raf = RandomAccessFile(file, "r")
                        openedStreams.add(raf)
                        RandomAccessFileInStream(raf).also { openedStreams.add(it) }
                    }
                }

                is ArchiveSource.ContentUri -> {
                    openSafInStream(context, source.uri, source.displayName, openedStreams)
                }
            }

            val inArchive = SevenZip.openInArchive(null, primaryStream, openCallback)
            return ArchiveOpenSession(inArchive, openedStreams, openCallback)
        } catch (e: SevenZipException) {
            openedStreams.forEach { runCatching { it.close() } }
            if (openCallback.passwordRequested || isPasswordError(e)) {
                throw ArchivePasswordRequiredException("Архив зашифрован. Введите пароль.")
            }
            throw e
        } catch (t: Throwable) {
            openedStreams.forEach { runCatching { it.close() } }
            throw t
        }
    }

    private fun openSafInStream(
        context: Context,
        uri: Uri,
        displayName: String,
        openedStreams: MutableList<Closeable>
    ): IInStream {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Не удалось открыть ParcelFileDescriptor для $uri")
        val fis = FileInputStream(pfd.fileDescriptor)
        val channel = fis.channel

        val isSeekable = try {
            channel.size() >= 0L && channel.position(0L) != null
        } catch (_: Throwable) {
            false
        }

        if (isSeekable) {
            val stream = SeekableChannelInStream(channel, Closeable {
                runCatching { fis.close() }
                runCatching { pfd.close() }
            })
            openedStreams.add(stream)
            return stream
        }

        // Если SAF-провайдер отдал pipe (не поддерживает seek), сохраняем во временный кэш
        val cacheDir = File(context.cacheDir, "archive_saf_cache").apply { mkdirs() }
        val tempFile = File.createTempFile("saf_", "_${displayName.replace('/', '_')}", cacheDir)
        fis.use { input ->
            pfd.use {
                FileOutputStream(tempFile).use { out ->
                    input.copyTo(out, 64 * 1024)
                }
            }
        }
        val raf = RandomAccessFile(tempFile, "r")
        val rafCloseable = Closeable {
            runCatching { raf.close() }
            runCatching { tempFile.delete() }
        }
        openedStreams.add(rafCloseable)
        return RandomAccessFileInStream(raf).also { openedStreams.add(it) }
    }

    private fun isPasswordError(e: SevenZipException): Boolean {
        var cur: Throwable? = e
        while (cur != null) {
            val msg = cur.message.orEmpty()
            if (msg.contains(PASSWORD_MARKER) ||
                msg.contains("password", ignoreCase = true) ||
                msg.contains("encrypted", ignoreCase = true) ||
                msg.contains("WRONG_PASSWORD", ignoreCase = true)
            ) {
                return true
            }
            cur = cur.cause
        }
        return false
    }

    private fun detectFormatLabel(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> "TAR.GZ"
            lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> "TAR.BZ2"
            lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> "TAR.XZ"
            lower.contains(".part") && lower.endsWith(".rar") -> "RAR (Multi-Volume)"
            else -> lower.substringAfterLast('.', "ARCHIVE").uppercase(Locale.US)
        }
    }

    private class ArchiveOpenSession(
        val archive: IInArchive,
        private val streams: List<Closeable>,
        val callback: OpenArchiveCallback
    ) : Closeable {
        override fun close() {
            runCatching { archive.close() }
            streams.asReversed().forEach { runCatching { it.close() } }
        }
    }

    private class OpenArchiveCallback(
        private val source: ArchiveSource,
        private val password: String?,
        private val openedStreams: MutableList<Closeable>
    ) : IArchiveOpenCallback, ICryptoGetTextPassword, IArchiveOpenVolumeCallback {

        @Volatile
        var passwordRequested: Boolean = false
            private set

        private val baseDir: File? = (source as? ArchiveSource.FilePath)?.let { File(it.path).parentFile }
        private val streamCache = HashMap<String, File>()

        override fun setTotal(files: Long?, bytes: Long?) {}
        override fun setCompleted(files: Long?, bytes: Long?) {}

        override fun cryptoGetTextPassword(): String {
            passwordRequested = true
            if (password.isNullOrEmpty()) {
                throw SevenZipException(PASSWORD_MARKER)
            }
            return password
        }

        override fun getProperty(propID: PropID): Any? {
            return when (propID) {
                PropID.NAME -> {
                    when (source) {
                        is ArchiveSource.FilePath -> File(source.path).name
                        is ArchiveSource.ContentUri -> source.displayName
                    }
                }
                PropID.SIZE -> {
                    (source as? ArchiveSource.FilePath)?.let { File(it.path).length() }
                }
                else -> null
            }
        }

        override fun getStream(filename: String): IInStream? {
            val dir = baseDir ?: return null
            val candidate = File(filename).takeIf { it.isAbsolute && it.exists() }
                ?: File(dir, File(filename).name)
            if (!candidate.exists() || !candidate.isFile || !candidate.canRead()) {
                return null
            }
            streamCache[candidate.absolutePath] = candidate
            val raf = RandomAccessFile(candidate, "r")
            openedStreams.add(raf)
            return RandomAccessFileInStream(raf).also { openedStreams.add(it) }
        }
    }

    private class SeekableChannelInStream(
        private val channel: FileChannel,
        private val closer: Closeable
    ) : IInStream {

        @Synchronized
        override fun seek(offset: Long, seekOrigin: Int): Long {
            val targetPos = when (seekOrigin) {
                IInStream.SEEK_SET -> offset
                IInStream.SEEK_CUR -> channel.position() + offset
                IInStream.SEEK_END -> channel.size() + offset
                else -> throw SevenZipException("Unsupported seek origin: $seekOrigin")
            }
            channel.position(targetPos.coerceAtLeast(0L))
            return channel.position()
        }

        @Synchronized
        override fun read(data: ByteArray): Int {
            if (data.isEmpty()) return 0
            val buf = ByteBuffer.wrap(data)
            val read = channel.read(buf)
            return if (read < 0) 0 else read
        }

        override fun close() {
            closer.close()
        }
    }
}
