package com.example.videoplayer.archive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveFileUtilsTest {

    @Test
    fun recognizesLegacyRarVolumes() {
        assertTrue(ArchiveFileUtils.isArchiveFileName("movie.r00"))
        assertTrue(ArchiveFileUtils.isArchiveFileName("movie.R23"))
    }

    @Test
    fun recognizesSplitZipVolumes() {
        assertTrue(ArchiveFileUtils.isArchiveFileName("backup.z01"))
        assertTrue(ArchiveFileUtils.isArchiveFileName("backup.Z99"))
    }

    @Test
    fun recognizesMultipartAndNumericVolumes() {
        assertTrue(ArchiveFileUtils.isArchiveFileName("movie.part02.rar"))
        assertTrue(ArchiveFileUtils.isArchiveFileName("disk.002"))
    }

    @Test
    fun doesNotTreatOrdinaryFilesAsArchives() {
        assertFalse(ArchiveFileUtils.isArchiveFileName("movie.mp4"))
        assertFalse(ArchiveFileUtils.isArchiveFileName("notes.txt"))
    }
}
