package com.example.videoplayer.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class LanDevice(
    val ip: String,
    var hostname: String = "",
    var mac: String = "",
    var vendor: String = "",
    var kind: String = "other", // phone, tablet, router, pc, other
    var isGateway: Boolean = false,
    var isServer: Boolean = false,
    var isMe: Boolean = false,
    var connected: Boolean = false,
    var rtt: Int? = null
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("ip", ip)
        if (hostname.isNotBlank()) obj.put("hostname", hostname)
        if (mac.isNotBlank()) obj.put("mac", mac)
        if (vendor.isNotBlank()) obj.put("vendor", vendor)
        obj.put("kind", kind)
        if (isGateway) obj.put("isGateway", true)
        if (isServer) obj.put("isServer", true)
        if (isMe) obj.put("isMe", true)
        if (connected) obj.put("connected", true)
        if (rtt != null) obj.put("rtt", rtt)
        return obj
    }
}

object LanScanner {
    private const val TAG = "LanScanner"
    private val PROBE_PORTS = intArrayOf(80, 443, 22, 445, 8080, 8008, 8009, 62078, 7000, 5555)

    @Volatile
    var isScanning: Boolean = false
        private set

    @Volatile
    var scanProgress: Int = 0
        private set

    @Volatile
    var lastFinishedTime: Long? = null
        private set

    @Volatile
    var lastSubnet: String = ""
        private set

    private val detectedDevices = Collections.synchronizedList(mutableListOf<LanDevice>())
    private val scanScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null

    @Volatile
    private var ouiMap: Map<String, String>? = null

    fun getScanResultJson(clientIp: String?, serverIp: String?, connectedIps: Set<String>): JSONObject {
        val root = JSONObject()
        root.put("scanning", isScanning)
        root.put("progress", if (isScanning) scanProgress else 100)
        root.put("finished", lastFinishedTime ?: JSONObject.NULL)
        root.put("subnet", lastSubnet.ifBlank { "192.168.1.0/24" })

        val arr = JSONArray()
        val list = synchronized(detectedDevices) { detectedDevices.toList() }
        for (dev in list) {
            val copy = dev.copy(
                isMe = !clientIp.isNullOrBlank() && clientIp == dev.ip,
                isServer = !serverIp.isNullOrBlank() && serverIp == dev.ip,
                connected = connectedIps.contains(dev.ip)
            )
            arr.put(copy.toJson())
        }
        root.put("devices", arr)
        return root
    }

    @Synchronized
    fun startScan(context: Context, serverIp: String?, connectedIps: Set<String>): Boolean {
        if (isScanning) return false

        isScanning = true
        scanProgress = 0
        synchronized(detectedDevices) { detectedDevices.clear() }

        loadOuiTableIfNeeded(context)

        scanJob = scanScope.launch {
            try {
                performScan(context, serverIp, connectedIps)
            } catch (e: Exception) {
                Log.e(TAG, "Scan error", e)
            } finally {
                isScanning = false
                scanProgress = 100
                lastFinishedTime = System.currentTimeMillis()
            }
        }
        return true
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        isScanning = false
    }

    private suspend fun performScan(context: Context, serverIp: String?, connectedIps: Set<String>) {
        val (subnetPrefix, gatewayIp, myIp) = getSubnetInfo(context, serverIp)
        lastSubnet = "$subnetPrefix.0/24"

        val foundMap = ConcurrentHashMap<String, LanDevice>()

        // 1. Always add server itself
        val srvIp = serverIp ?: myIp
        if (!srvIp.isNullOrBlank()) {
            val srvDevice = LanDevice(
                ip = srvIp,
                hostname = "Этот телефон (сервер)",
                kind = "phone",
                isServer = true,
                rtt = 1
            )
            foundMap[srvIp] = srvDevice
        }

        // 2. Discover mDNS devices for 5 seconds concurrently
        val mdnsHosts = ConcurrentHashMap<String, String>()
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

        if (nsdManager != null) {
            val serviceTypes = listOf("_http._tcp.", "_googlecast._tcp.", "_airplay._tcp.", "_raop._tcp.")
            for (type in serviceTypes) {
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onStartDiscoveryFailed(p0: String?, p1: Int) {}
                    override fun onStopDiscoveryFailed(p0: String?, p1: Int) {}
                    override fun onDiscoveryStarted(p0: String?) {}
                    override fun onDiscoveryStopped(p0: String?) {}
                    override fun onServiceLost(p0: NsdServiceInfo?) {}
                    override fun onServiceFound(info: NsdServiceInfo?) {
                        if (info != null) {
                            try {
                                nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                                    override fun onResolveFailed(p0: NsdServiceInfo?, p1: Int) {}
                                    override fun onServiceResolved(resolved: NsdServiceInfo?) {
                                        val host = resolved?.host?.hostAddress
                                        val name = resolved?.serviceName
                                        if (!host.isNullOrBlank() && !name.isNullOrBlank()) {
                                            mdnsHosts[host] = name
                                        }
                                    }
                                })
                            } catch (_: Exception) {}
                        }
                    }
                }
                discoveryListeners.add(listener)
                try {
                    nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (_: Exception) {}
            }
        }

        // 3. 48-worker thread pool for fast ping / socket probe
        val executor = Executors.newFixedThreadPool(48)
        val dispatcher = executor.asCoroutineDispatcher()
        val completedCount = AtomicInteger(0)
        val totalHosts = 254

        val arpTable = readArpTable()

        val jobs = (1..254).map { i ->
            scanScope.launch(dispatcher) {
                val targetIp = "$subnetPrefix.$i"
                val dev = probeHost(targetIp, gatewayIp, arpTable)
                if (dev != null) {
                    foundMap[targetIp] = dev
                }
                val c = completedCount.incrementAndGet()
                scanProgress = (c * 100) / totalHosts
            }
        }

        jobs.joinAll()
        executor.shutdown()

        // Unregister mDNS listeners
        if (nsdManager != null) {
            for (l in discoveryListeners) {
                try {
                    nsdManager.stopServiceDiscovery(l)
                } catch (_: Exception) {}
            }
        }

        // Read ARP table again for any new entries
        val updatedArp = readArpTable()

        // Enrich detected devices
        for ((ip, dev) in foundMap) {
            val mac = updatedArp[ip] ?: dev.mac
            if (mac.isNotBlank()) {
                dev.mac = mac
                dev.vendor = lookupVendor(mac)
            }
            val mdnsName = mdnsHosts[ip]
            if (!mdnsName.isNullOrBlank() && dev.hostname.isBlank()) {
                dev.hostname = mdnsName
            }
            if (ip == gatewayIp) {
                dev.isGateway = true
                dev.kind = "router"
                if (dev.hostname.isBlank()) dev.hostname = "Router"
            }
            classifyDevice(dev)
        }

        // Sort by IP numerically
        val sortedList = foundMap.values.sortedWith(Comparator { a, b ->
            val aParts = a.ip.split(".").mapNotNull { it.toIntOrNull() }
            val bParts = b.ip.split(".").mapNotNull { it.toIntOrNull() }
            for (idx in 0 until minOf(aParts.size, bParts.size)) {
                if (aParts[idx] != bParts[idx]) return@Comparator aParts[idx].compareTo(bParts[idx])
            }
            0
        })

        synchronized(detectedDevices) {
            detectedDevices.clear()
            detectedDevices.addAll(sortedList)
        }
    }

    private fun probeHost(ip: String, gatewayIp: String?, arpTable: Map<String, String>): LanDevice? {
        val startTime = System.currentTimeMillis()
        var reachable = false
        var rtt: Int? = null

        // 1. Quick isReachable (ICMP / echo)
        try {
            val addr = InetAddress.getByName(ip)
            if (addr.isReachable(280)) {
                reachable = true
                rtt = (System.currentTimeMillis() - startTime).toInt().coerceAtLeast(1)
            }
        } catch (_: Exception) {}

        // 2. TCP probe ports
        if (!reachable) {
            for (port in PROBE_PORTS) {
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(ip, port), 200)
                        reachable = true
                        rtt = (System.currentTimeMillis() - startTime).toInt().coerceAtLeast(1)
                    }
                    if (reachable) break
                } catch (_: Exception) {}
            }
        }

        // 3. Even if not responding directly, check if present in ARP cache
        val mac = arpTable[ip]
        if (!reachable && mac != null && mac != "00:00:00:00:00:00") {
            reachable = true
            rtt = 10
        }

        if (!reachable) return null

        // Reverse DNS
        var hostname = ""
        try {
            val addr = InetAddress.getByName(ip)
            val h = addr.canonicalHostName
            if (!h.isNullOrBlank() && h != ip) {
                hostname = h
            } else {
                val simple = addr.hostName
                if (!simple.isNullOrBlank() && simple != ip) {
                    hostname = simple
                }
            }
        } catch (_: Exception) {}

        return LanDevice(
            ip = ip,
            hostname = hostname,
            mac = mac ?: "",
            rtt = rtt
        )
    }

    private fun classifyDevice(dev: LanDevice) {
        if (dev.isGateway) {
            dev.kind = "router"
            return
        }

        val hn = dev.hostname.lowercase()
        val v = dev.vendor.lowercase()

        when {
            hn.contains("router") || hn.contains("gateway") || v.contains("cisco") || v.contains("tp-link") || v.contains("d-link") || v.contains("netgear") || v.contains("zyxel") || v.contains("mikrotik") -> {
                dev.kind = "router"
            }
            hn.contains("ipad") || hn.contains("tablet") || hn.contains("tab") -> {
                dev.kind = "tablet"
            }
            hn.contains("iphone") || hn.contains("android") || hn.contains("pixel") || hn.contains("galaxy") || hn.contains("xiaomi") || hn.contains("huawei") || hn.contains("redmi") || hn.contains("oppo") || hn.contains("vivo") -> {
                dev.kind = "phone"
            }
            hn.contains("desktop") || hn.contains("laptop") || hn.contains("macbook") || hn.contains("imac") || hn.contains("pc") || hn.contains("windows") || hn.contains("thinkpad") || hn.contains("dell") || hn.contains("asus") || hn.contains("hp") || hn.contains("lenovo") -> {
                dev.kind = "pc"
            }
            v.contains("apple") -> {
                dev.kind = if (hn.contains("ipad")) "tablet" else if (hn.contains("mac")) "pc" else "phone"
            }
            v.contains("samsung") || v.contains("xiaomi") || v.contains("huawei") -> {
                dev.kind = "phone"
            }
            v.contains("intel") || v.contains("dell") || v.contains("microsoft") || v.contains("hewlett") || v.contains("lenovo") || v.contains("asustek") -> {
                dev.kind = "pc"
            }
            else -> {
                if (dev.kind == "other" && dev.hostname.isNotBlank()) {
                    if (dev.hostname.contains("tv", ignoreCase = true) || dev.hostname.contains("cast", ignoreCase = true)) {
                        dev.kind = "other"
                    }
                }
            }
        }
    }

    private fun getSubnetInfo(context: Context, serverIp: String?): Triple<String, String?, String?> {
        var prefix = "192.168.1"
        var gateway: String? = null
        var myIp: String? = null

        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNet = cm.activeNetwork
                val linkProps: LinkProperties? = cm.getLinkProperties(activeNet)
                if (linkProps != null) {
                    for (route in linkProps.routes) {
                        if (route.isDefaultRoute && route.gateway is Inet4Address) {
                            gateway = route.gateway?.hostAddress
                        }
                    }
                    for (linkAddr in linkProps.linkAddresses) {
                        val addr = linkAddr.address
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val host = addr.hostAddress ?: continue
                            myIp = host
                            val parts = host.split(".")
                            if (parts.size == 4) {
                                prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (serverIp != null && serverIp != "auto") {
            val parts = serverIp.split(".")
            if (parts.size == 4) {
                prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
            }
        }

        if (gateway == null) {
            gateway = "$prefix.1"
        }

        return Triple(prefix, gateway, myIp)
    }

    private fun readArpTable(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val arp = File("/proc/net/arp")
            if (arp.exists() && arp.canRead()) {
                BufferedReader(FileReader(arp)).use { br ->
                    var line: String?
                    while (br.readLine().also { line = it } != null) {
                        val tokens = line!!.trim().split("\\s+".toRegex())
                        if (tokens.size >= 4 && tokens[0] != "IP") {
                            val ip = tokens[0]
                            val mac = tokens[3].uppercase()
                            if (mac.length == 17 && mac != "00:00:00:00:00:00") {
                                map[ip] = mac
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return map
    }

    private fun loadOuiTableIfNeeded(context: Context) {
        if (ouiMap != null) return
        val map = HashMap<String, String>()
        try {
            context.assets.open("oui.csv").use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val l = line!!.trim()
                        if (l.isBlank() || l.startsWith("#")) continue
                        val idx = l.indexOf(',')
                        if (idx > 0) {
                            val prefix = l.substring(0, idx).trim().uppercase()
                            val vendor = l.substring(idx + 1).trim()
                            map[prefix] = vendor
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading oui.csv", e)
        }
        ouiMap = map
    }

    private fun lookupVendor(mac: String): String {
        val map = ouiMap ?: return ""
        val clean = mac.replace(":", "").replace("-", "").uppercase()
        if (clean.length >= 6) {
            val prefix = clean.substring(0, 6)
            return map[prefix] ?: ""
        }
        return ""
    }
}
