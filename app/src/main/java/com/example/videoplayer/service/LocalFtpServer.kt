package com.example.videoplayer.service

import android.content.Context
import java.io.File
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import java.net.InetAddress
import java.net.NetworkInterface

object LocalFtpServer {
    private var server: FtpServer? = null
    var isRunning: Boolean = false
        private set
    var port: Int = 2121
        private set

    fun start(context: Context, chosenPort: Int = 2121): Boolean {
        if (isRunning) return true
        port = chosenPort
        return try {
            val serverFactory = FtpServerFactory()
            val listenerFactory = ListenerFactory()
            listenerFactory.port = port
            serverFactory.addListener("default", listenerFactory.createListener())

            // Anonymous FTP is read-only and confined to this dedicated app share folder.
            // FTP has no transport encryption here, so do not permit remote uploads or writes.
            val shareDirectory = File(
                context.getExternalFilesDir(null) ?: context.filesDir,
                "ftp_share"
            )
            if (!shareDirectory.exists() && !shareDirectory.mkdirs()) {
                throw IllegalStateException("Не удалось создать папку FTP-обмена")
            }
            if (!shareDirectory.isDirectory) {
                throw IllegalStateException("Путь FTP-обмена не является папкой")
            }

            val user = BaseUser().apply {
                name = "anonymous"
                homeDirectory = shareDirectory.canonicalPath
            }
            serverFactory.userManager.save(user)

            val createdServer = serverFactory.createServer()
            createdServer.start()
            server = createdServer
            isRunning = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            isRunning = false
            false
        }
    }

    fun stop() {
        val currentServer = server
        if (currentServer != null) {
            try {
                currentServer.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            server = null
        }
        isRunning = false
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val host = addr.hostAddress
                        if (host != null && !host.contains(":")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        // Do not advertise a made-up address when the device has no usable IPv4 address.
        return ""
    }
}
