package ovh.devlabstudy.devlabnetworktoolkit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress

@Composable
fun TracerouteScreen() {
    var targetHost by remember { mutableStateOf("devlabstudy.ovh") }
    var traceLogs by remember { mutableStateOf(listOf<String>()) }
    var isTracing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Automatyczne przewijanie na dół przy nowym logu
    LaunchedEffect(traceLogs.size) {
        if (traceLogs.isNotEmpty()) listState.animateScrollToItem(traceLogs.lastIndex)
    }

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
                        runNativeTraceroute(targetHost.trim()) { log -> traceLogs = traceLogs + log }
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
            LazyColumn(state = listState, modifier = Modifier.padding(12.dp)) {
                items(traceLogs) { log ->
                    Text(
                        log,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

private val ipv4Regex = Regex("""(\d{1,3}(?:\.\d{1,3}){3})""")
private val fromRegex = Regex("""[Ff]rom\s+(\d{1,3}(?:\.\d{1,3}){3})""")
private val timeRegex = Regex("""time[=<]\s*([\d.]+)\s*ms""")

suspend fun runNativeTraceroute(host: String, onLog: (String) -> Unit) = withContext(Dispatchers.IO) {
    suspend fun log(msg: String) = withContext(Dispatchers.Main) { onLog(msg) }

    try {
        // Wymuszamy IPv4 – ping z TTL na IPv6 zwykle nie działa tak samo
        val targetAddr = InetAddress.getAllByName(host).firstOrNull { it is Inet4Address }
        if (targetAddr == null) {
            log("Nie znaleziono adresu IPv4 dla: $host")
            return@withContext
        }
        val targetIp = targetAddr.hostAddress ?: host
        log("Cel: $host ($targetIp)")

        val maxHops = 30
        var debugShown = false

        for (ttl in 1..maxHops) {
            val start = System.currentTimeMillis()

            val output = try {
                val process = ProcessBuilder(
                    "/system/bin/ping", "-c", "1", "-n", "-t", ttl.toString(), "-W", "2", targetIp
                ).redirectErrorStream(true).start()

                try {
                    process.inputStream.bufferedReader().readText()
                } finally {
                    process.waitFor()
                    process.destroy()
                }
            } catch (e: Exception) {
                log("Hop $ttl: Błąd uruchomienia ping (${e.localizedMessage ?: "Nieznany"})")
                break
            }

            val elapsed = System.currentTimeMillis() - start
            val lower = output.lowercase()

            val ttlExceeded = lower.contains("time to live exceeded") ||
                    lower.contains("ttl exceeded") ||
                    lower.contains("time exceeded")

            // Odpowiedź od routera pośredniego
            val routerIp = fromRegex.find(output)?.groupValues?.get(1)
                ?: if (ttlExceeded) ipv4Regex.find(output)?.groupValues?.get(1) else null

            // Odpowiedź od celu (echo reply)
            val reachedTarget = !ttlExceeded &&
                    (lower.contains("bytes from") || lower.contains("1 received")) &&
                    !lower.contains("100% packet loss")

            when {
                reachedTarget -> {
                    val ms = timeRegex.find(output)?.groupValues?.get(1) ?: "$elapsed"
                    log("Hop $ttl: $targetIp ($ms ms)")
                    log("Trasa zakończona. Cel został osiągnięty.")
                    break
                }
                routerIp != null -> {
                    log("Hop $ttl: $routerIp ($elapsed ms)")
                }
                else -> {
                    log("Hop $ttl: * * * (Upłynął limit czasu)")
                    // Diagnostyka: pokaż surowy wynik ping tylko raz
                    if (!debugShown) {
                        debugShown = true
                        val raw = output.trim().lines().take(4).joinToString(" | ")
                        log("   [debug ping]: ${raw.ifBlank { "(pusta odpowiedź)" }}")
                    }
                }
            }
        }
    } catch (e: Exception) {
        log("Błąd inicjalizacji: ${e.message}")
    }
}
