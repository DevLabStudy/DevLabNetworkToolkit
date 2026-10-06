package ovh.devlabstudy.devlabnetworktoolkit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import kotlin.math.abs

@Composable
fun MetricCard(title: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .width(160.dp)
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SpeedtestScreen() {
    var isTesting by remember { mutableStateOf(false) }
    var avgPing by remember { mutableStateOf("-") }
    var jitter by remember { mutableStateOf("-") }
    var maxSpike by remember { mutableStateOf("-") }
    var packetLoss by remember { mutableStateOf("-") }
    var downloadSpeed by remember { mutableStateOf("-") }
    var uploadSpeed by remember { mutableStateOf("-") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Speedtest i Analiza Łącza", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Pomiary stabilności, pobierania i wysyłania", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.Center) {
                MetricCard("Średni Ping", avgPing)
                MetricCard("Jitter", jitter)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                MetricCard("Max Spike", maxSpike)
                MetricCard("Utrata pakietów", packetLoss)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Download", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(downloadSpeed, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Upload", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(uploadSpeed, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (!isTesting) {
                    isTesting = true
                    downloadSpeed = "Mierzenie..."
                    uploadSpeed = "Oczekiwanie..."
                    coroutineScope.launch {
                        runFullSpeedTest { avg, jit, spike, loss, dl, ul ->
                            avgPing = avg
                            jitter = jit
                            maxSpike = spike
                            packetLoss = loss
                            downloadSpeed = dl
                            uploadSpeed = ul
                        }
                        isTesting = false
                    }
                }
            },
            enabled = !isTesting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isTesting) "Trwa test łączności..." else "Uruchom Pełny Speedtest")
        }
    }
}

suspend fun runFullSpeedTest(
    onResult: (String, String, String, String, String, String) -> Unit
) = withContext(Dispatchers.IO) {
    // 1. Ping i stabilność
    val timesMs = mutableListOf<Double>()
    var lostPackets = 0
    val totalPings = 15
    val target = InetAddress.getByName("1.1.1.1")

    for (i in 1..totalPings) {
        val startTime = System.nanoTime()
        if (target.isReachable(500)) {
            val endTime = System.nanoTime()
            val elapsedMs = (endTime - startTime) / 1_000_000.0
            timesMs.add(elapsedMs)
        } else {
            lostPackets++
        }
        Thread.sleep(20)
    }

    var avgStr = "-"
    var jitStr = "-"
    var maxStr = "-"
    var lossStr = "0%"

    if (timesMs.isNotEmpty()) {
        val avg = timesMs.average()
        val maxMs = timesMs.maxOrNull() ?: 0.0

        var jitterSum = 0.0
        for (i in 0 until timesMs.size - 1) {
            jitterSum += abs(timesMs[i] - timesMs[i + 1])
        }
        val jit = if (timesMs.size > 1) jitterSum / (timesMs.size - 1) else 0.0
        val lossPct = ((lostPackets.toDouble() / totalPings) * 100).toInt()

        avgStr = String.format("%.1f ms", avg)
        jitStr = String.format("%.2f ms", jit)
        maxStr = String.format("%.1f ms", maxMs)
        lossStr = "$lossPct%"
    }

    // 2. Wielowątkowy Pobieranie (Download)
    var downloadResult = "Błąd"
    try {
        val startTime = System.currentTimeMillis()
        val threadsCount = 4
        val deferreds = (1..threadsCount).map {
            async(Dispatchers.IO) {
                var bytesReadTotal = 0L
                try {
                    val url = URL("https://speed.cloudflare.com/__down?bytes=50000000")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.connectTimeout = 4000
                    conn.readTimeout = 6000

                    val stream = conn.inputStream
                    val buf = ByteArray(32768)
                    var r: Int
                    val taskStart = System.currentTimeMillis()
                    while (stream.read(buf).also { r = it } != -1) {
                        bytesReadTotal += r
                        if ((System.currentTimeMillis() - taskStart) >= 5000) break
                    }
                    stream.close()
                    conn.disconnect()
                } catch (_: Exception) {}
                bytesReadTotal
            }
        }

        val totalBytes = deferreds.awaitAll().sum()
        val elapsedTimeSec = (System.currentTimeMillis() - startTime) / 1000.0
        if (elapsedTimeSec > 0 && totalBytes > 0) {
            val megaBits = (totalBytes * 8) / 1_000_000.0
            val speedMbps = megaBits / elapsedTimeSec
            downloadResult = String.format("%.1f Mbps", speedMbps)
        }
    } catch (_: Exception) {
        downloadResult = "Błąd"
    }

    // 3. Wysyłanie (Upload)
    var uploadResult = "Błąd"
    try {
        val startTime = System.currentTimeMillis()
        val payload = ByteArray(32768)
        var totalUploadedBytes = 0L

        val url = URL("https://speed.cloudflare.com/__up")
        val conn = url.openConnection() as HttpURLConnection
        conn.doOutput = true
        conn.requestMethod = "POST"
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setChunkedStreamingMode(32768)
        conn.connectTimeout = 4000
        conn.readTimeout = 6000

        val os: OutputStream = conn.outputStream
        val uploadStart = System.currentTimeMillis()
        while ((System.currentTimeMillis() - uploadStart) < 4000) {
            os.write(payload)
            totalUploadedBytes += payload.size
        }
        os.flush()
        os.close()

        val elapsedTimeSec = (System.currentTimeMillis() - startTime) / 1000.0
        if (elapsedTimeSec > 0 && totalUploadedBytes > 0) {
            val megaBits = (totalUploadedBytes * 8) / 1_000_000.0
            val speedMbps = megaBits / elapsedTimeSec
            uploadResult = String.format("%.1f Mbps", speedMbps)
        }
    } catch (_: Exception) {
        uploadResult = "Ograniczenie"
    }

    withContext(Dispatchers.Main) {
        onResult(avgStr, jitStr, maxStr, lossStr, downloadResult, uploadResult)
    }
}