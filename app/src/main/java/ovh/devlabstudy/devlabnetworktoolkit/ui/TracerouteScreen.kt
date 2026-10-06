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
fun TracerouteScreen() {
    var targetHost by remember { mutableStateOf("devlabstudy.ovh") }
    var traceLogs by remember { mutableStateOf(listOf<String>()) }
    var isTracing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Traceroute", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = targetHost,
            onValueChange = { targetHost = it },
            label = { Text("Domena lub IP docelowe") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (!isTracing && targetHost.isNotBlank()) {
                    isTracing = true
                    traceLogs = emptyList()
                    coroutineScope.launch {
                        runNativeTraceroute(targetHost) { log -> traceLogs = traceLogs + log }
                        isTracing = false
                    }
                }
            },
            enabled = !isTracing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isTracing) "Analiza trasy w toku..." else "Rozpocznij Traceroute")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            LazyColumn(modifier = Modifier.padding(12.dp)) {
                items(traceLogs) { log ->
                    Text(log, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

suspend fun runNativeTraceroute(host: String, onLog: (String) -> Unit) = withContext(Dispatchers.IO) {
    try {
        val targetAddr = InetAddress.getByName(host)
        val targetIp = targetAddr.hostAddress ?: host
        withContext(Dispatchers.Main) { onLog("Cel: $host ($targetIp)") }

        val maxHops = 20
        for (ttl in 1..maxHops) {
            val startTime = System.currentTimeMillis()
            val process = Runtime.getRuntime().exec("ping -c 1 -t $ttl -W 2 $targetIp")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errorReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            val elapsedTime = System.currentTimeMillis() - startTime

            val fullOutput = output.toString()
            val extractedIp = parseIpFromPing(fullOutput)

            val logLine = when {
                extractedIp != null -> "Hop $ttl: $extractedIp ($elapsedTime ms)"
                fullOutput.contains("Time to live exceeded") || fullOutput.contains("Time Exceeded") -> {
                    val ip = parseIpFromPing(fullOutput) ?: "Odpowiedź routera"
                    "Hop $ttl: $ip ($elapsedTime ms)"
                }
                else -> "Hop $ttl: * * * (Upłynął limit czasu)"
            }

            withContext(Dispatchers.Main) { onLog(logLine) }

            if (extractedIp == targetIp || fullOutput.contains("bytes from $targetIp")) {
                withContext(Dispatchers.Main) { onLog("Trasa zakończona. Cel został osiągnięty.") }
                break
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) { onLog("Błąd wykonywania: ${e.message}") }
    }
}

fun parseIpFromPing(text: String): String? {
    val regex = Regex("""(?:\d{1,3}\.){3}\d{1,3}""")
    val matches = regex.findAll(text).map { it.value }.toList()
    return matches.firstOrNull { it != "127.0.0.1" }
}