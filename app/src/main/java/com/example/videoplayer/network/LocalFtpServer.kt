package com.example.videoplayer.network

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.io.File
import java.net.NetworkInterface
import java.util.Collections

class LocalFtpServerManager(private val context: Context) {
    private var server: FtpServer? = null
    private val sharedDir = File(context.getExternalFilesDir(null), "ftp_share").apply { mkdirs() }

    fun start(): String {
        val currentServer = server
        if (currentServer != null && !currentServer.isStopped) {
            return currentAddress()
        }

        val ftpServerFactory = FtpServerFactory()
        val userManager = ftpServerFactory.userManager
        val user = BaseUser().apply {
            name = "nexora"
            password = "nexora"
            homeDirectory = sharedDir.absolutePath
            authorities = listOf(WritePermission())
        }
        userManager.save(user)

        val listenerFactory = ListenerFactory()
        listenerFactory.port = 2121
        listenerFactory.serverAddress = localIpAddress()
        ftpServerFactory.addListener("default", listenerFactory.createListener())

        val newServer = ftpServerFactory.createServer()
        newServer.start()
        server = newServer
        return currentAddress()
    }

    fun stop() {
        val currentServer = server
        if (currentServer != null) {
            try {
                currentServer.stop()
            } catch (_: Exception) {
            }
            server = null
        }
    }

    fun isRunning(): Boolean {
        val currentServer = server
        return currentServer != null && !currentServer.isStopped
    }

    fun currentAddress(): String = "ftp://${localIpAddress()}:2121"

    private fun localIpAddress(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipAddress = wifiManager?.connectionInfo?.ipAddress
        if (ipAddress != null) {
            @Suppress("DEPRECATION")
            val ip = Formatter.formatIpAddress(ipAddress)
            if (ip != null && ip != "0.0.0.0") return ip
        }

        for (networkInterface in Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (address in Collections.list(networkInterface.inetAddresses)) {
                if (!address.isLoopbackAddress && address.hostAddress.contains(".")) {
                    val host = address.hostAddress
                    if (host != "0.0.0.0") return host
                }
            }
        }

        return "127.0.0.1"
    }
}
