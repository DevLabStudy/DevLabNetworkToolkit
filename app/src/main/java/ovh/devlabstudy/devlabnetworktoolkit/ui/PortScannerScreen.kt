package ovh.devlabstudy.devlabnetworktoolkit.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.devlabstudy.devlabnetworktoolkit.billing.BillingManager
import ovh.devlabstudy.devlabnetworktoolkit.util.LicenseManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

@Composable
fun PortScannerScreen(billingManager: BillingManager? = null) {
    val context = LocalContext.current
    val activity = context as? Activity

    // --- INTEGRACJA LICENCJI I PŁATNOŚCI GOOGLE PLAY ---
    val licenseManager = remember { LicenseManager(context) }
    val isLicensePro by licenseManager.isPremiumFlow.collectAsState()

    val userAccessState by if (billingManager != null) {
        billingManager.userAccessState.collectAsState()
    } else {
        remember { mutableStateOf(null) }
    }

    // Dostęp do UDP odblokowany, jeśli wpisano klucz Supabase LUB zakupiono w Google Play
    val hasUdpAccess = isLicensePro || (userAccessState?.hasAccessToFeature(BillingManager.MOD_PORT_SCANNER) ?: false)

    var showPaywallDialog by remember { mutableStateOf(false) }

    // Automatyczne wykrywanie bramy domyślnej
    val defaultTargetIp = remember {
        val localIp = getLocalIpAddress()
        if (localIp != null && localIp.contains(".")) {
            localIp.substringBeforeLast(".") + ".1"
        } else {
            "192.168.1.1"
        }
    }

    var targetHost by remember { mutableStateOf(defaultTargetIp) }
    var scanProtocol by remember { mutableIntStateOf(0) } // 0 = TCP (FREE), 1 = UDP (PRO)
    var scanResults by remember { mutableStateOf(listOf<String>()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val commonTcpPorts = listOf(
        21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP",
        53 to "DNS", 80 to "HTTP", 110 to "POP3", 143 to "IMAP",
        443 to "HTTPS", 445 to "SMB", 3306 to "MySQL", 3389 to "RDP",
        8080 to "HTTP-Alt"
    )

    val commonUdpPorts = listOf(
        53 to "DNS", 67 to "DHCP Server", 68 to "DHCP Client",
        69 to "TFTP", 123 to "NTP", 137 to "NetBIOS",
        161 to "SNMP", 500 to "IKE (VPN)", 1900 to "SSDP/UPnP", 5353 to "mDNS"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Skaner Portów", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = targetHost,
            onValueChange = { targetHost = it },
            label = { Text("Adres IP celu") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Wybór TCP (Free) vs UDP (PRO)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = scanProtocol == 0,
                onClick = { if (!isScanning) scanProtocol = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("TCP (Darmowy)")
            }
            SegmentedButton(
                selected = scanProtocol == 1,
                onClick = {
                    if (!isScanning) {
                        if (hasUdpAccess) {
                            scanProtocol = 1
                        } else {
                            showPaywallDialog = true
                        }
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text(if (hasUdpAccess) "UDP (PRO)" else "UDP (PRO)")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (isScanning) {
                    scanJob?.cancel()
                    isScanning = false
                    scanResults = scanResults + "--- Skanowanie anulowane ---"
                } else if (targetHost.isNotBlank()) {
                    if (scanProtocol == 1 && !hasUdpAccess) {
                        showPaywallDialog = true
                        return@Button
                    }

                    isScanning = true
                    scanResults = emptyList()
                    scanJob = coroutineScope.launch {
                        if (scanProtocol == 0) {
                            scanTcpPorts(targetHost, commonTcpPorts) { scanResults = scanResults + it }
                        } else {
                            scanUdpPorts(targetHost, commonUdpPorts) { scanResults = scanResults + it }
                        }
                        isScanning = false
                    }
                }
            },
            colors = if (isScanning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            else ButtonDefaults.buttonColors(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isScanning) "Anuluj skanowanie" else "Skanuj ${if (scanProtocol == 0) "TCP" else "UDP"}")
        }

        if (!hasUdpAccess) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = { showPaywallDialog = true }) {
                Text("Odblokuj skanowanie UDP (5 zł/rok lub Subskrypcja)")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(scanResults) { result ->
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }

    // --- DIALOG ZAKUPU MODUŁU PORT SCANNER / PRO ---
    if (showPaywallDialog) {
        AlertDialog(
            onDismissRequest = { showPaywallDialog = false },
            title = { Text("Odblokuj Skaner UDP") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Skanowanie protokołu UDP pozwala wykrywać usługi takie jak DNS, DHCP, VPN, mDNS i serwery gier.")
                    Text(
                        "Możesz odblokować ten moduł na rok za 5 zł lub uzyskać pełny dostęp do wszystkich narzędzi w ramach abonamentu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPaywallDialog = false
                        activity?.let { act ->
                            billingManager?.launchPurchaseFlow(act, BillingManager.MOD_PORT_SCANNER)
                        }
                    }
                ) {
                    Text("Odblokuj moduł (5 zł / rok)")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPaywallDialog = false
                        activity?.let { act ->
                            billingManager?.launchPurchaseFlow(act, BillingManager.SUB_YEARLY)
                        }
                    }
                ) {
                    Text("Pełny PRO (25 zł / rok)")
                }
            }
        )
    }
}

suspend fun scanTcpPorts(host: String, ports: List<Pair<Int, String>>, onResult: (String) -> Unit) {
    withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onResult("Rozpoczynanie skanowania TCP dla $host...") }
        for ((port, service) in ports) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 350)
                socket.close()
                withContext(Dispatchers.Main) { onResult("🟢 TCP $port ($service) - OTWARTY") }
            } catch (_: Exception) {}
        }
        withContext(Dispatchers.Main) { onResult("--- Skanowanie TCP zakończone ---") }
    }
}

suspend fun scanUdpPorts(host: String, ports: List<Pair<Int, String>>, onResult: (String) -> Unit) {
    withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onResult("Rozpoczynanie skanowania UDP dla $host...") }
        val address = InetAddress.getByName(host)

        for ((port, service) in ports) {
            try {
                val socket = DatagramSocket()
                socket.soTimeout = 800
                val data = ByteArray(8)
                val packet = DatagramPacket(data, data.size, address, port)
                socket.send(packet)

                val receivePacket = DatagramPacket(ByteArray(1024), 1024)
                try {
                    socket.receive(receivePacket)
                    withContext(Dispatchers.Main) { onResult("🟢 UDP $port ($service) - OTWARTY (Otrzymano odpowiedź)") }
                } catch (e: java.net.SocketTimeoutException) {
                    withContext(Dispatchers.Main) { onResult("🟡 UDP $port ($service) - OTWARTY | FILTROWANY") }
                }
                socket.close()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult("🔴 UDP $port ($service) - ZAMKNIĘTY / BŁĄD") }
            }
        }
        withContext(Dispatchers.Main) { onResult("--- Skanowanie UDP zakończone ---") }
    }
}