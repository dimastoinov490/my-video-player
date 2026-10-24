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
        if (server != null && server!!.isRunning) {
            return currentAddress()
        }

        val ftpServerFactory = FtpServerFactory()
        val userManager = ftpServerFactory.userManager
        val user = BaseUser()
        user.name = "nexora"
        user.password = "nexora"
        user.homeDirectory = sharedDir.absolutePath
        user.authorize(WritePermission())
        userManager.save(user)

        val listenerFactory = ListenerFactory()
        listenerFactory.port = 2121
        listenerFactory.serverAddress = localIpAddress()
        ftpServerFactory.addListener("default", listenerFactory.createListener())

        server = ftpServerFactory.createServer()
        server?.start()
        return currentAddress()
    }

    fun stop() {
        try {
            server?.stop()
        } catch (_: Exception) {
        }
        server = null
    }

    fun isRunning(): Boolean = server?.isRunning == true

    fun currentAddress(): String = "ftp://${localIpAddress()}:2121"

    private fun localIpAddress(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipAddress = wifiManager?.connectionInfo?.ipAddress
        if (ipAddress != null) {
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
