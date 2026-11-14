package com.example.videoplayer.archive

import java.io.File
import java.util.concurrent.CancellationException
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ZipArchiveCreatorTest {

    @Test
    fun createsZipWithFilesAndEmptyDirectories() = withTempDirectory { root ->
        val source = File(root, "source").apply { mkdirs() }
        File(source, "hello.txt").writeText("hello archive")
        File(source, "empty").mkdirs()
        val output = File(root, "result.zip")

        ZipArchiveCreator.create(listOf(source), output)

        ZipFile(output).use { zip ->
            assertEquals("hello archive", zip.getInputStream(zip.getEntry("source/hello.txt")).bufferedReader().readText())
            assertTrue(zip.getEntry("source/empty/") != null)
        }
    }

    @Test
    fun doesNotOverwriteExistingDestination() = withTempDirectory { root ->
        val source = File(root, "input.txt").apply { writeText("new") }
        val output = File(root, "result.zip").apply { writeText("keep me") }

        try {
            ZipArchiveCreator.create(listOf(source), output)
            fail("Expected existing destination to be rejected")
        } catch (_: java.io.IOException) {
            assertEquals("keep me", output.readText())
        }
    }

    @Test
    fun removesPartialArchiveWhenCancelled() = withTempDirectory { root ->
        val source = File(root, "large.bin").apply { writeBytes(ByteArray(256 * 1024) { 7 }) }
        val output = File(root, "cancelled.zip")
        var progressCalls = 0

        try {
            ZipArchiveCreator.create(
                sources = listOf(source),
                destination = output,
                isCancelled = { progressCalls > 0 },
                onProgress = { progressCalls++ }
            )
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertFalse(output.exists())
        }
    }

    @Test
    fun rejectsDuplicateArchiveEntryNames() = withTempDirectory { root ->
        val firstDir = File(root, "one").apply { mkdirs() }
        val secondDir = File(root, "two").apply { mkdirs() }
        File(firstDir, "same.txt").writeText("one")
        File(secondDir, "same.txt").writeText("two")
        val output = File(root, "duplicate.zip")

        try {
            ZipArchiveCreator.create(
                listOf(File(firstDir, "same.txt"), File(secondDir, "same.txt")),
                output
            )
            fail("Expected duplicate entry to be rejected")
        } catch (_: java.io.IOException) {
            assertFalse(output.exists())
        }
    }

    private inline fun withTempDirectory(block: (File) -> Unit) {
        val dir = createTempDir(prefix = "zip-creator-test-")
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
