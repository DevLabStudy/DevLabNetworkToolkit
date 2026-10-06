package ovh.devlabstudy.devlabnetworktoolkit.ui

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import ovh.devlabstudy.devlabnetworktoolkit.billing.BillingManager
import ovh.devlabstudy.devlabnetworktoolkit.util.LicenseManager
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Locale

data class LanDevice(
    val ip: String,
    val hostname: String,
    val mac: String,
    val vendor: String,
    val responseTimeMs: Long,
    val isSelf: Boolean = false
)

data class WifiSubnetInfo(
    val isWifi: Boolean,
    val localIp: String,
    val interfaceName: String,
    val firstTwoOctets: String,
    val subnetBase: String,
    val networkPrefix: String,
    val isClassA16: Boolean,
    val gatewayIp: String
)

@Composable
fun LanScannerScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val wifiInfo = remember { getWifiSubnetDetails(context) }

    // --- INTEGRACJA LICENCJI I PŁATNOŚCI GOOGLE PLAY ---
    val licenseManager = remember { LicenseManager(context) }
    val billingManager = remember { BillingManager(context, coroutineScope) }

    val userAccessState by billingManager.userAccessState.collectAsState()
    val isLicensePro by licenseManager.isPremiumFlow.collectAsState()

    // Dostęp PRO odblokowany, jeśli wpisano klucz Supabase LUB zakupiono w Google Play
    val isPremium = isLicensePro || userAccessState.hasAccessToFeature(BillingManager.MOD_LAN_SCANNER)

    var showPaywallDialog by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(listOf<LanDevice>()) }
    var isScanning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var selectedDevice by remember { mutableStateOf<LanDevice?>(null) }

    LaunchedEffect(Unit) {
        billingManager.startConnection()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Główny nagłówek sekcji
        Text(
            text = if (isPremium) "Skaner Podsieci LAN (PRO)" else "Skaner Podsieci LAN",
            style = MaterialTheme.typography.headlineMedium,
            color = if (isPremium) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (wifiInfo.isWifi) {
            Text(
                "Interfejs: ${wifiInfo.interfaceName} (${wifiInfo.localIp}) • Sieć: ${wifiInfo.networkPrefix}",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                "Brak połączenia z siecią Wi-Fi. Połącz się z Wi-Fi, aby przeskanować LAN.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (!isScanning && wifiInfo.isWifi) {
                    isScanning = true
                    devices = emptyList()
                    progress = 0f
                    coroutineScope.launch {
                        devices = scanSubnetTargeted(
                            wifiInfo = wifiInfo,
                            gatewayIp = wifiInfo.gatewayIp,
                            selfIp = wifiInfo.localIp,
                            isPremium = isPremium
                        ) { p -> progress = p }
                        isScanning = false
                    }
                }
            },
            enabled = !isScanning && wifiInfo.isWifi,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (isScanning) "Skanowanie..."
                else if (isPremium) "Skanuj sieć (PRO)"
                else "Skanuj sieć"
            )
        }

        if (!isPremium) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = { showPaywallDialog = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Odblokuj pełną moc skanowania (PRO)")
            }
        }

        if (isScanning) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(devices) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedDevice = device },
                    colors = CardDefaults.cardColors(
                        containerColor = if (device.isSelf) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(device.ip, style = MaterialTheme.typography.titleMedium)
                                if (device.isSelf) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("(To urządzenie)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Text("${device.hostname} • ${device.vendor}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${device.responseTimeMs} ms", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    // --- DIALOG GOOGLE PAYWALL ---
    if (showPaywallDialog) {
        AlertDialog(
            onDismissRequest = { showPaywallDialog = false },
            title = { Text("Wersja PRO Skanera") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Odblokuj pełną moc silnika skanującego.")
                    Text(
                        "Wersja PRO umożliwia wielowątkowe przeszukiwanie całej podsieci w kilka sekund (do 100 wątków równolegle).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPaywallDialog = false
                        activity?.let {
                            billingManager.launchPurchaseFlow(it, BillingManager.MOD_LAN_SCANNER)
                        }
                    }
                ) {
                    Text("Kup moduł LAN Scanner")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaywallDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }

    selectedDevice?.let { dev ->
        AlertDialog(
            onDismissRequest = { selectedDevice = null },
            title = { Text("Szczegóły Urządzenia") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Adres IP: ${dev.ip}")
                    Text("Rola / Identyfikacja: ${dev.vendor}")
                    Text("Nazwa Hosta: ${dev.hostname}")
                    Text("Adres MAC: ${dev.mac}")
                    Text("Czas odpowiedzi: ${dev.responseTimeMs} ms")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDevice = null }) { Text("Zamknij") }
            }
        )
    }
}

fun getWifiSubnetDetails(context: Context): WifiSubnetInfo {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    var wifiIp = ""
    var intfName = "wlan0"

    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val intf = interfaces.nextElement()
            if (intf.name.lowercase().contains("wlan")) {
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && !addr.hostAddress.orEmpty().contains(":")) {
                        wifiIp = addr.hostAddress.orEmpty()
                        intfName = intf.name
                    }
                }
            }
        }
    } catch (_: Exception) {}

    if (wifiIp.isEmpty()) {
        val wifiNetwork = cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }

        if (wifiNetwork != null) {
            val linkProps = cm.getLinkProperties(wifiNetwork)
            linkProps?.linkAddresses?.forEach { linkAddr ->
                val host = linkAddr.address.hostAddress
                if (host != null && !linkAddr.address.isLoopbackAddress && !host.contains(":")) {
                    wifiIp = host
                }
            }
        }
    }

    if (wifiIp.isEmpty()) {
        return WifiSubnetInfo(
            isWifi = false,
            localIp = "Brak połączenia Wi-Fi",
            interfaceName = "-",
            firstTwoOctets = "",
            subnetBase = "",
            networkPrefix = "Brak sieci",
            isClassA16 = false,
            gatewayIp = ""
        )
    }

    val parts = wifiIp.split(".")
    val firstTwoOctets = if (parts.size >= 2) "${parts[0]}.${parts[1]}" else "10.0"
    val subnetBase = wifiIp.substringBeforeLast(".")

    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val dhcp = wm.dhcpInfo
    val gatewayIp = if (dhcp != null && dhcp.gateway != 0) {
        String.format(
            Locale.US,
            "%d.%d.%d.%d",
            dhcp.gateway and 0xff,
            dhcp.gateway shr 8 and 0xff,
            dhcp.gateway shr 16 and 0xff,
            dhcp.gateway shr 24 and 0xff
        )
    } else {
        "$subnetBase.1"
    }

    val isClassA16 = wifiIp.startsWith("10.0.")
    val displayPrefix = if (isClassA16) "10.0.0.0/16" else "$subnetBase.0/24"

    return WifiSubnetInfo(
        isWifi = true,
        localIp = wifiIp,
        interfaceName = intfName,
        firstTwoOctets = firstTwoOctets,
        subnetBase = subnetBase,
        networkPrefix = displayPrefix,
        isClassA16 = isClassA16,
        gatewayIp = gatewayIp
    )
}

suspend fun scanSubnetTargeted(
    wifiInfo: WifiSubnetInfo,
    gatewayIp: String,
    selfIp: String,
    isPremium: Boolean,
    onProgress: (Float) -> Unit
): List<LanDevice> = withContext(Dispatchers.IO) {
    val arpMap = getArpTable()
    val foundDevices = mutableListOf<LanDevice>()

    val maxParallelism = if (isPremium) 100 else 8
    val dispatcher = Dispatchers.IO.limitedParallelism(maxParallelism)

    val subnetsToScan = if (wifiInfo.isClassA16) {
        (0..15).map { "${wifiInfo.firstTwoOctets}.$it" } + listOf("${wifiInfo.firstTwoOctets}.100", "${wifiInfo.firstTwoOctets}.254")
    } else {
        listOf(wifiInfo.subnetBase)
    }

    val totalSubnets = subnetsToScan.size
    var completedSubnets = 0

    for (base in subnetsToScan) {
        ensureActive()
        val deferreds = (1..254).map { last ->
            async(dispatcher) {
                val ip = "$base.$last"
                val isSelf = (ip == selfIp)
                val startTime = System.currentTimeMillis()

                try {
                    val address = InetAddress.getByName(ip)
                    val reachable = isSelf || address.isReachable(120) || isPortOpen(ip, 80, 80) || isPortOpen(ip, 445, 80)

                    if (reachable) {
                        val time = if (isSelf) 0L else System.currentTimeMillis() - startTime
                        val mac = arpMap[ip] ?: "Brak (Android API)"

                        var hostName = getNetBIOSName(ip)
                        if (hostName == "Brak nazwy" || hostName.isEmpty()) {
                            hostName = try {
                                val canonical = address.canonicalHostName
                                if (canonical != ip) canonical else address.hostName
                            } catch (_: Exception) { "Brak nazwy" }
                        }

                        val vendor = identifyWithFingerprint(ip, gatewayIp, isSelf, hostName)
                        LanDevice(ip, hostName, mac, vendor, time, isSelf)
                    } else null
                } catch (_: Exception) { null }
            }
        }

        val results = deferreds.awaitAll().filterNotNull()
        foundDevices.addAll(results)

        completedSubnets++
        withContext(Dispatchers.Main) {
            onProgress(completedSubnets.toFloat() / totalSubnets)
        }
    }

    return@withContext foundDevices.sortedBy { ipToLong(it.ip) }
}

fun ipToLong(ip: String): Long {
    val parts = ip.split(".")
    if (parts.size != 4) return 0L
    return (parts[0].toLong() shl 24) + (parts[1].toLong() shl 16) + (parts[2].toLong() shl 8) + parts[3].toLong()
}

fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
    return try {
        val socket = Socket()
        socket.connect(InetSocketAddress(ip, port), timeoutMs)
        socket.close()
        true
    } catch (_: Exception) {
        false
    }
}

fun identifyWithFingerprint(ip: String, gatewayIp: String, isSelf: Boolean, hostName: String): String {
    if (isSelf) return "Twój Smartfon Android"

    val lowerHost = hostName.lowercase()

    // 1. Sprawdzenie specyficznych nazw hostów
    if (lowerHost.contains("proxmox")) return "Serwer Proxmox VE"
    if (lowerHost.contains("switch") || lowerHost.contains("sw-") || lowerHost.contains("tplink")) return "Switch zarządzalny"
    if (lowerHost.contains("mikrotik")) return if (ip == gatewayIp) "Router MikroTik" else "Switch / AP MikroTik"
    if (lowerHost.contains("synology") || lowerHost.contains("qnap")) return "Serwer NAS"
    if (lowerHost.contains("raspberry")) return "Raspberry Pi"
    if (lowerHost.contains("gaming") || lowerHost.contains("pc") || lowerHost.contains("desktop")) return "Komputer PC (Windows)"

    // 2. Kontrola portów usługowych
    val hasProxmoxPort = isPortOpen(ip, 8006, 80)
    if (hasProxmoxPort) return "Serwer Proxmox VE"

    val hasMediaPort = isPortOpen(ip, 8096, 80) || isPortOpen(ip, 32400, 80)
    if (hasMediaPort) return "Serwer Mediów (Jellyfin/Plex)"

    val hasDbPort = isPortOpen(ip, 3306, 80) || isPortOpen(ip, 5432, 80) || isPortOpen(ip, 6379, 80)
    if (hasDbPort) return "Serwer Baz Danych"

    val hasWeb = isPortOpen(ip, 80, 80) || isPortOpen(ip, 443, 80) || isPortOpen(ip, 8080, 80) || isPortOpen(ip, 8443, 80)
    val hasSmb = isPortOpen(ip, 445, 80) || isPortOpen(ip, 139, 80)
    val hasSsh = isPortOpen(ip, 22, 80)
    val hasTelnet = isPortOpen(ip, 23, 80)
    val hasSnmp = isPortOpen(ip, 161, 80)
    val hasDns = isPortOpen(ip, 53, 80)

    if (ip == gatewayIp) {
        if (hasWeb || hasDns || hasSsh) return "Główny Router / Brama"
        return "Główny Router"
    }

    if (hasWeb && (hasSnmp || hasTelnet || !hasSmb)) {
        if (hasSsh || hasSnmp || hasTelnet) return "Switch zarządzalny / AP"
        return "Urządzenie SIECIOWE / Panel Web"
    }

    if (hasSmb) return "Komputer Windows / NAS (SMB)"
    if (hasSsh) return "Serwer Linux / SSH"

    return "Urządzenie LAN"
}

fun getNetBIOSName(ip: String): String {
    return try {
        val socket = DatagramSocket()
        socket.soTimeout = 120

        val query = byteArrayOf(
            0x80.toByte(), 0x94.toByte(), 0x00, 0x00, 0x00, 0x01, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x20, 0x43, 0x4B, 0x41, 0x41, 0x41,
            0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
            0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
            0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x00, 0x00, 0x21, 0x00, 0x01
        )

        val address = InetAddress.getByName(ip)
        val packet = DatagramPacket(query, query.size, address, 137)
        socket.send(packet)

        val buffer = ByteArray(1024)
        val receivePacket = DatagramPacket(buffer, buffer.size)
        socket.receive(receivePacket)
        socket.close()

        if (receivePacket.length > 57) {
            val numberOfNames = buffer[56].toInt() and 0xFF
            if (numberOfNames > 0) {
                val nameBytes = ByteArray(15)
                System.arraycopy(buffer, 57, nameBytes, 0, 15)
                val rawName = String(nameBytes, Charsets.US_ASCII).trim()
                if (rawName.isNotBlank()) return rawName
            }
        }
        "Brak nazwy"
    } catch (_: Exception) {
        "Brak nazwy"
    }
}

fun getArpTable(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    try {
        File("/proc/net/arp").forEachLine { line ->
            val tokens = line.split(Regex("\\s+"))
            if (tokens.size >= 4 && tokens[3] != "00:00:00:00:00:00") {
                map[tokens[0]] = tokens[3].uppercase()
            }
        }
    } catch (_: Exception) {}
    return map
}