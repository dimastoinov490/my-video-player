package com.example.videoplayer.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import java.io.File
import java.net.NetworkInterface

object TransferManager {

    private const val HTTP_PORT = 8765

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
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } else {
            @Suppress("DEPRECATION")
            Uri.fromFile(file)
        }

        val intent = ShareCompat.IntentBuilder(context)
            .setType(mimeType)
            .setStream(uri)
            .setChooserTitle("Share video")
            .createChooserIntent()
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        context.startActivity(intent)
    }

    fun buildLocalDownloadUrl(file: File): String {
        val host = localIpAddress()
        val safeName = file.name.replace(" ", "%20")
        return "http://$host:$HTTP_PORT/download/$safeName"
    }

    private fun localIpAddress(): String {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { network ->
                network.inetAddresses?.toList()?.forEach { address ->
                    if (!address.isLoopbackAddress && address.hostAddress != null && address.hostAddress.contains('.') && !address.hostAddress.contains(':')) {
                        return address.hostAddress
                    }
                }
            }
        } catch (_: Exception) {
            // fallback
        }
        return "127.0.0.1"
    }
}
