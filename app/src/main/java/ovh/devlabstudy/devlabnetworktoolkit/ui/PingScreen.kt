package ovh.devlabstudy.devlabnetworktoolkit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress

@Composable
fun PingScreen() {
    var targetHost by remember { mutableStateOf("9.9.9.9") }
    var packetCount by remember { mutableIntStateOf(4) }
    var pingLogs by remember { mutableStateOf(listOf<String>()) }
    var isRunning by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("DevLab Ping Tool", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = targetHost,
            onValueChange = { targetHost = it },
            label = { Text("Adres IP lub domena") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Liczba pakietów:", modifier = Modifier.padding(top = 12.dp))
            listOf(4, 8, 16, 32).forEach { count ->
                FilterChip(
                    selected = packetCount == count,
                    onClick = { packetCount = count },
                    label = { Text(count.toString()) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (!isRunning && targetHost.isNotBlank()) {
                    isRunning = true
                    pingLogs = emptyList()
                    coroutineScope.launch {
                        runRealPing(targetHost, packetCount) { log ->
                            pingLogs = pingLogs + log
                        }
                        isRunning = false
                    }
                }
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRunning) "Wysyłanie..." else "Uruchom Ping ($packetCount x)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            LazyColumn(modifier = Modifier.padding(12.dp)) {
                items(pingLogs) { log ->
                    Text(log, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

suspend fun runRealPing(host: String, count: Int, onLog: (String) -> Unit) = withContext(Dispatchers.IO) {
    try {
        val address = InetAddress.getByName(host)
        val ip = address.hostAddress ?: host
        withContext(Dispatchers.Main) { onLog("Pinging $host [$ip] z $count pakietami:") }

        // Metoda 1: Wywołanie natywnej komendy ping (najdokładniejsze czasy i TTL na Androidzie)
        val process = Runtime.getRuntime().exec("ping -c $count -W 2 $ip")
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        var seqCounter = 1

        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue
            if (currentLine.contains("bytes from") || currentLine.contains("bajtów z")) {
                withContext(Dispatchers.Main) { onLog(currentLine) }
                seqCounter++
            }
        }
        process.waitFor()

        // Jeśli natywna komenda nie zwróciła wyjścia, używamy czystej Javy z poprawnym resetowaniem zegara
        if (seqCounter == 1) {
            for (i in 1..count) {
                val singleStart = System.currentTimeMillis() // Reset miernika przed KAŻDYM pakietem
                val reachable = address.isReachable(2000)
                val singleTime = System.currentTimeMillis() - singleStart

                val log = if (reachable) {
                    "Odpowiedź z $ip: seq=$i time=${singleTime}ms"
                } else {
                    "Przekroczono limit czasu żądania (seq=$i)"
                }

                withContext(Dispatchers.Main) { onLog(log) }
                Thread.sleep(200)
            }
        }

    } catch (e: Exception) {
        withContext(Dispatchers.Main) { onLog("Błąd: ${e.message}") }
    }
}