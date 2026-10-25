package com.example.videoplayer.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI
import java.util.Locale

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

    fun createLocalTransferServer(file: File): HttpServer? {
        return try {
            val baseDir = file.parentFile ?: file.absoluteFile.parentFile
            val server = HttpServer.create(java.net.InetSocketAddress(localIpAddress(), HTTP_PORT), 0)
            server.createContext("/download") { exchange ->
                val requested = exchange.requestURI.path.substringAfterLast('/')
                val target = File(baseDir, requested)
                if (target.exists() && target.isFile) {
                    val bytes = target.readBytes()
                    exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                    exchange.responseHeaders.add("Content-Disposition", "attachment; filename=\"${target.name}\"")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { output -> output.write(bytes) }
                } else {
                    exchange.sendResponseHeaders(404, -1)
                }
            }
            server.start()
            server
        } catch (_: Exception) {
            null
        }
    }

    fun stopServer(server: HttpServer?) {
        server?.stop(0)
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
            // fallback below
        }
        return "192.168.1.1"
    }
}
