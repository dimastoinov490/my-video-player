package com.example.videoplayer.archive

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveVolumeResolutionTest {

    @Test
    fun resolvesLegacyRarVolumeToCaseInsensitiveFirstPart() = withTempDirectory { dir ->
        val first = File(dir, "Movie.RAR").apply { writeText("first") }
        val selected = File(dir, "Movie.r03").apply { writeText("part") }

        assertEquals(first.canonicalFile, ArchiveManager.resolvePrimaryVolumeFile(selected).canonicalFile)
    }

    @Test
    fun resolvesSplitZipVolumeToZipContainer() = withTempDirectory { dir ->
        val first = File(dir, "Backup.ZIP").apply { writeText("first") }
        val selected = File(dir, "Backup.z02").apply { writeText("part") }

        assertEquals(first.canonicalFile, ArchiveManager.resolvePrimaryVolumeFile(selected).canonicalFile)
    }

    @Test
    fun resolvesMultipartRarToFirstPart() = withTempDirectory { dir ->
        val first = File(dir, "Movie.part01.rar").apply { writeText("first") }
        val selected = File(dir, "Movie.part03.rar").apply { writeText("part") }

        assertEquals(first.canonicalFile, ArchiveManager.resolvePrimaryVolumeFile(selected).canonicalFile)
    }

    @Test
    fun resolvesNumericVolumeTo001() = withTempDirectory { dir ->
        val first = File(dir, "Disk.001").apply { writeText("first") }
        val selected = File(dir, "Disk.004").apply { writeText("part") }

        assertEquals(first.canonicalFile, ArchiveManager.resolvePrimaryVolumeFile(selected).canonicalFile)
    }

    @Test
    fun leavesOrdinaryArchivePathUnchanged() = withTempDirectory { dir ->
        val selected = File(dir, "ordinary.zip").apply { writeText("archive") }

        assertEquals(selected.canonicalFile, ArchiveManager.resolvePrimaryVolumeFile(selected).canonicalFile)
    }

    private inline fun withTempDirectory(block: (File) -> Unit) {
        val dir = createTempDir(prefix = "archive-volume-test-")
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
