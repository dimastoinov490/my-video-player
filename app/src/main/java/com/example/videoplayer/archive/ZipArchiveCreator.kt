package com.example.videoplayer.archive

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Creates ZIP archives from local files/directories.
 *
 * The destination is reserved with createNewFile(), so an existing archive is never
 * truncated. Any output created by this operation is removed if creation fails or is
 * cancelled.
 */
object ZipArchiveCreator {
    private const val BUFFER_SIZE = 64 * 1024

    data class Progress(
        val bytesWritten: Long,
        val totalBytes: Long,
        val currentEntry: String
    )

    fun create(
        sources: List<File>,
        destination: File,
        compressionLevel: Int = Deflater.DEFAULT_COMPRESSION,
        isCancelled: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = {}
    ): File {
        require(sources.isNotEmpty()) { "Не выбраны файлы для архивации" }
        require(compressionLevel == Deflater.DEFAULT_COMPRESSION ||
            compressionLevel in Deflater.NO_COMPRESSION..Deflater.BEST_COMPRESSION) {
            "Недопустимый уровень сжатия: $compressionLevel"
        }

        val target = destination.canonicalFile
        val parent = target.parentFile
            ?: throw IOException("Не удалось определить папку назначения")
        if (!parent.isDirectory) {
            throw IOException("Папка назначения не существует: ${parent.absolutePath}")
        }
        if (target.exists()) {
            throw IOException("Файл уже существует: ${target.absolutePath}")
        }

        val entries = collectEntries(sources, target)
        val totalBytes = entries.sumOf { if (it.file.isFile) it.file.length().coerceAtLeast(0L) else 0L }

        // Reserve the exact destination atomically; never overwrite an existing file.
        if (!target.createNewFile()) {
            throw IOException("Не удалось создать файл: ${target.absolutePath}")
        }

        var bytesWritten = 0L
        var currentEntry = ""
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(target, false))).use { zip ->
                zip.setLevel(compressionLevel)
                val buffer = ByteArray(BUFFER_SIZE)

                for (item in entries) {
                    checkCancelled(isCancelled)
                    currentEntry = item.archivePath
                    val zipEntry = ZipEntry(item.archivePath)
                    if (item.file.isFile) {
                        zipEntry.time = item.file.lastModified().coerceAtLeast(0L)
                    }
                    zip.putNextEntry(zipEntry)

                    if (item.file.isFile) {
                        BufferedInputStream(FileInputStream(item.file)).use { input ->
                            while (true) {
                                checkCancelled(isCancelled)
                                val count = input.read(buffer)
                                if (count < 0) break
                                zip.write(buffer, 0, count)
                                bytesWritten += count
                                onProgress(Progress(bytesWritten, totalBytes, currentEntry))
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            return target
        } catch (t: Throwable) {
            // Only delete the file reserved by this invocation.
            runCatching { target.delete() }
            throw t
        }
    }

    private data class PendingEntry(val file: File, val archivePath: String)

    private fun collectEntries(sources: List<File>, destination: File): List<PendingEntry> {
        val result = ArrayList<PendingEntry>()
        val usedPaths = HashSet<String>()
        val visitedDirectories = HashSet<String>()

        fun addEntry(file: File, archivePath: String, root: File) {
            val canonical = file.canonicalFile
            val rootPath = root.canonicalPath.trimEnd(File.separatorChar) + File.separator
            if (canonical != root && !canonical.path.startsWith(rootPath)) {
                throw IOException("Ссылка выходит за пределы выбранной папки: ${file.absolutePath}")
            }
            if (canonical == destination) {
                throw IOException("Нельзя включить создаваемый архив в самого себя")
            }

            val cleanPath = archivePath.replace('\\\\', '/').trimStart('/')
            if (cleanPath.isBlank() || cleanPath.split('/').any { it == ".." || it == "." }) {
                throw IOException("Недопустимый путь в архиве: $archivePath")
            }
            if (!usedPaths.add(cleanPath)) {
                throw IOException("Повторяющийся путь в архиве: $cleanPath")
            }

            if (canonical.isDirectory) {
                result.add(PendingEntry(canonical, if (cleanPath.endsWith('/')) cleanPath else "$cleanPath/"))
                if (!visitedDirectories.add(canonical.path)) return
                val children = canonical.listFiles()?.sortedBy { it.name }
                    ?: throw IOException("Не удалось прочитать папку: ${canonical.absolutePath}")
                for (child in children) {
                    addEntry(child, "$cleanPath/${child.name}", root)
                }
            } else if (canonical.isFile) {
                result.add(PendingEntry(canonical, cleanPath))
            } else {
                throw IOException("Источник не является обычным файлом или папкой: ${file.absolutePath}")
            }
        }

        for (source in sources) {
            val canonical = source.canonicalFile
            if (!canonical.exists()) throw IOException("Источник не найден: ${source.absolutePath}")
            if (!canonical.isFile && !canonical.isDirectory) {
                throw IOException("Недопустимый источник: ${source.absolutePath}")
            }
            if (canonical.isDirectory) {
                val targetPath = destination.canonicalPath
                val dirPrefix = canonical.path.trimEnd(File.separatorChar) + File.separator
                if (targetPath.startsWith(dirPrefix)) {
                    throw IOException("Архив нельзя создавать внутри папки, которую он упаковывает")
                }
            }
            addEntry(canonical, canonical.name, canonical)
        }
        return result
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw CancellationException("Создание архива отменено")
        if (Thread.currentThread().isInterrupted) {
            throw CancellationException("Создание архива прервано")
        }
    }
}
