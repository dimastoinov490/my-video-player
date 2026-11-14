package com.example.videoplayer.archive

import android.net.Uri
import java.io.File

sealed class ArchiveSource {
    abstract val displayName: String

    data class FilePath(
        val path: String,
        override val displayName: String = File(path).name
    ) : ArchiveSource()

    data class ContentUri(
        val uri: Uri,
        override val displayName: String
    ) : ArchiveSource()
}

data class ArchiveEntryItem(
    val index: Int,
    val path: String,
    val name: String,
    val parentPath: String,
    val isDirectory: Boolean,
    val size: Long,
    val packedSize: Long,
    val isEncrypted: Boolean,
    val isVideo: Boolean,
    val lastModifiedMs: Long,
    val crc: String,
    val isVirtualFolder: Boolean = false
)

data class ArchiveInspectResult(
    val source: ArchiveSource,
    val archiveName: String,
    val formatName: String,
    val isEncrypted: Boolean,
    val isMultiVolume: Boolean,
    val entries: List<ArchiveEntryItem>
)

sealed class ExtractionProgress {
    data object Preparing : ExtractionProgress()
    data class Progress(
        val percent: Int,
        val bytesProcessed: Long,
        val totalBytes: Long,
        val currentFile: String
    ) : ExtractionProgress()

    data class Completed(
        val outputFiles: List<File>,
        val destinationPath: String
    ) : ExtractionProgress()

    data class Error(
        val message: String,
        val requiresPassword: Boolean = false
    ) : ExtractionProgress()

    data object Cancelled : ExtractionProgress()
}

data class ArchivePlaybackSpec(
    val source: ArchiveSource,
    val entryPath: String,
    val entryIndex: Int,
    val entrySize: Long,
    val entryName: String,
    val isEncrypted: Boolean,
    val isMultiVolume: Boolean,
    val formatName: String,
    val password: String? = null
)

class ArchivePasswordRequiredException(
    message: String = "Архив защищён паролем"
) : Exception(message)

class ArchiveCancelledException : Exception("Операция отменена")

object ArchiveFileUtils {
    private val ARCHIVE_EXTS = setOf(
        "zip", "rar", "7z", "tar", "gz", "tgz", "tbz2", "bz2", "xz", "txz", "iso", "cbz", "cbr"
    )
    private val VIDEO_EXTS = setOf(
        "mp4", "mkv", "avi", "ts", "webm", "mov", "m4v", "flv", "3gp", "wmv", "mpg", "mpeg", "mts", "m2ts"
    )
    private val PART_RAR_REGEX = Regex("""\.part\d+\.rar$""", RegexOption.IGNORE_CASE)
    private val SPLIT_NUM_REGEX = Regex("""\.\d{3}$""")
    private val OLD_RAR_VOL_REGEX = Regex("""\.r\d{2}$""", RegexOption.IGNORE_CASE)
    private val ZIP_VOL_REGEX = Regex("""\.z\d{2}$""", RegexOption.IGNORE_CASE)

    fun isArchiveFileName(name: String): Boolean {
        val lower = name.lowercase().trim()
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tar.bz2") || lower.endsWith(".tar.xz")) return true
        if (PART_RAR_REGEX.containsMatchIn(lower) || lower.endsWith(".001")) return true
        val ext = lower.substringAfterLast('.', "")
        return ext in ARCHIVE_EXTS
    }

    fun isVideoEntryName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTS
    }

    fun isMultiVolumeFileName(name: String): Boolean {
        val lower = name.lowercase().trim()
        return PART_RAR_REGEX.containsMatchIn(lower) ||
            SPLIT_NUM_REGEX.containsMatchIn(lower) ||
            OLD_RAR_VOL_REGEX.containsMatchIn(lower) ||
            ZIP_VOL_REGEX.containsMatchIn(lower)
    }

    fun guessVideoMimeType(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "ts", "mts", "m2ts" -> "video/mp2t"
            "mov" -> "video/quicktime"
            "flv" -> "video/x-flv"
            "3gp" -> "video/3gpp"
            else -> "application/octet-stream"
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes Б"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(java.util.Locale.US, "%.1f КБ", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(java.util.Locale.US, "%.2f МБ", mb)
        val gb = mb / 1024.0
        return String.format(java.util.Locale.US, "%.2f ГБ", gb)
    }
}
