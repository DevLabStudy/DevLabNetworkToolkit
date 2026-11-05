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
import java.net.HttpURLConnection
import java.net.URL

data class HttpInspection(
    val status: String,
    val elapsed: String,
    val headers: List<String>,
)

@Composable
fun HttpInspectorScreen() {
    var address by remember { mutableStateOf("https://devlabstudy.ovh") }
    var result by remember { mutableStateOf<HttpInspection?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("HTTP Inspector", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sprawdź kod odpowiedzi, czas i nagłówki serwera.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text("Adres URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                isLoading = true
                result = null
                error = null
                scope.launch {
                    runCatching { inspectHttp(address) }
                        .onSuccess { result = it }
                        .onFailure { error = it.message ?: "Nie udało się połączyć." }
                    isLoading = false
                }
            },
            enabled = !isLoading && address.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Sprawdzanie..." else "Sprawdź adres")
        }
        Spacer(Modifier.height(16.dp))
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        result?.let { inspection ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text(inspection.status, style = MaterialTheme.typography.titleMedium) }
                item { Text(inspection.elapsed) }
                item { Text("Nagłówki", style = MaterialTheme.typography.titleMedium) }
                items(inspection.headers) { header -> Text(header, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private suspend fun inspectHttp(address: String): HttpInspection = withContext(Dispatchers.IO) {
    val normalized = if (address.startsWith("http://") || address.startsWith("https://")) {
        address
    } else {
        "https://$address"
    }
    val connection = URL(normalized).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        val startedAt = System.nanoTime()
        val statusCode = connection.responseCode
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        HttpInspection(
            status = "HTTP $statusCode ${connection.responseMessage.orEmpty()}",
            elapsed = "Czas odpowiedzi: ${elapsedMs} ms",
            headers = connection.headerFields
                .filterKeys { it != null }
                .flatMap { (key, values) -> values.map { "$key: $it" } }
                .sorted(),
        )
    } finally {
        connection.disconnect()
    }
}

@Composable
fun PublicIpScreen() {
    var publicIp by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Publiczne IP", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Adres IPv4 widoczny dla zewnętrznych usług.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                isLoading = true
                error = null
                scope.launch {
                    runCatching { fetchPublicIp() }
                        .onSuccess { publicIp = it }
                        .onFailure { error = it.message ?: "Nie udało się pobrać adresu IP." }
                    isLoading = false
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Pobieranie..." else "Sprawdź moje IP")
        }
        Spacer(Modifier.height(20.dp))
        publicIp?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private suspend fun fetchPublicIp(): String = withContext(Dispatchers.IO) {
    URL("https://api.ipify.org").openStream().bufferedReader().use { reader ->
        reader.readText().trim().ifBlank { error("Pusta odpowiedź serwera.") }
    }
}
