package ovh.devlabstudy.devlabnetworktoolkit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

@Composable
fun WolScreen() {
    var macAddress by remember { mutableStateOf("00:11:22:33:44:55") }
    var broadcastIp by remember { mutableStateOf("255.255.255.255") }
    var statusText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Wake-on-LAN (WoL)", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Zdalne wybudzanie maszyn w sieci", style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(12.dp))

        // Baner informacyjny ws. wymogów sprzętowych
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Informacja",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Wymagania techniczne WoL", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Samo wysłanie pakietu z aplikacji nie wystarczy! Urządzenie docelowe musi mieć włączoną obsługę Wake-on-LAN w BIOS/UEFI, opcję wybudzania we właściwościach karty sieciowej w systemie oraz być połączone kablem Ethernet (lub wspierać WoWLAN).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = macAddress,
            onValueChange = { macAddress = it },
            label = { Text("Adres MAC urządzenia") },
            placeholder = { Text("AA:BB:CC:DD:EE:FF") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = broadcastIp,
            onValueChange = { broadcastIp = it },
            label = { Text("Adres Broadcast (domyślnie 255.255.255.255)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    statusText = sendMagicPacket(macAddress, broadcastIp)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(" Wyślij Magic Packet")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (statusText.isNotEmpty()) {
            Text(statusText, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

suspend fun sendMagicPacket(macStr: String, broadcastIpStr: String): String = withContext(Dispatchers.IO) {
    try {
        val macBytes = parseMac(macStr)
        val bytes = ByteArray(6 + 16 * macBytes.size)
        for (i in 0..5) bytes[i] = 0xFF.toByte()
        for (i in 6 until bytes.size) bytes[i] = macBytes[i % 6]

        val address = InetAddress.getByName(broadcastIpStr)
        val packet = DatagramPacket(bytes, bytes.size, address, 9)
        val socket = DatagramSocket()
        socket.broadcast = true
        socket.send(packet)
        socket.close()

        "🟢 Pomyślnie wysłano pakiet Magic Packet do $macStr"
    } catch (e: Exception) {
        "🔴 Błąd formatu MAC lub sieci: ${e.message}"
    }
}

fun parseMac(macStr: String): ByteArray {
    val hex = macStr.replace(":", "").replace("-", "")
    require(hex.length == 12) { "Format MAC musi posiadać 12 znaków hex" }
    val bytes = ByteArray(6)
    for (i in 0 until 6) {
        bytes[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return bytes
}