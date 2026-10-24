package com.example.videoplayer.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.ShareCompat
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

object TransferManager {

    fun shareUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Video URL")
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(intent, "Send video URL"))
    }

    fun shareFile(context: Context, file: File, mimeType: String = "video/*") {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val authority = "${context.packageName}.provider"
            androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        } else {
            Uri.fromFile(file)
        }

        val intent = ShareCompat.IntentBuilder(context)
            .setType(mimeType)
            .addStream(uri)
            .setChooserTitle("Send video")
            .createChooserIntent()
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        context.startActivity(intent)
    }

    fun uploadFileToFtp(
        host: String,
        port: Int,
        username: String,
        password: String,
        file: File,
        remoteDirectory: String = "/"
    ): Boolean {
        val ftp = FTPClient()
        return try {
            ftp.connect(host, port)
            ftp.login(username, password)
            ftp.enterLocalPassiveMode()
            ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
            val remote = if (remoteDirectory.endsWith("/")) remoteDirectory else "$remoteDirectory/"
            val path = remote + file.name
            FileInputStream(file).use { input ->
                ftp.storeFile(path, input)
            }
        } catch (e: Exception) {
            false
        } finally {
            try { ftp.logout() } catch (_: Exception) {}
            try { ftp.disconnect() } catch (_: Exception) {}
        }
    }

    fun sendToLocalNetworkByFtp(
        host: String,
        port: Int,
        username: String,
        password: String,
        file: File,
        remoteDirectory: String = "/"
    ): Boolean = uploadFileToFtp(host, port, username, password, file, remoteDirectory)
}
