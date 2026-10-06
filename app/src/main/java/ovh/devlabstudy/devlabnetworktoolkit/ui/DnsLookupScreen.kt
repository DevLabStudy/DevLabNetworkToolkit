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
import java.net.InetAddress

@Composable
fun DnsLookupScreen() {
    var domain by remember { mutableStateOf("devlabstudy.ovh") }
    var dnsResults by remember { mutableStateOf(listOf<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("DNS Lookup", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = domain,
            onValueChange = { domain = it },
            label = { Text("Nazwa domeny") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (domain.isNotBlank()) {
                    isLoading = true
                    dnsResults = emptyList()
                    coroutineScope.launch {
                        dnsResults = performDnsLookup(domain)
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Odpytywanie DNS..." else "Sprawdź DNS")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            LazyColumn(modifier = Modifier.padding(12.dp)) {
                items(dnsResults) { res ->
                    Text(res, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

suspend fun performDnsLookup(domain: String): List<String> = withContext(Dispatchers.IO) {
    val results = mutableListOf<String>()
    try {
        val addresses = InetAddress.getAllByName(domain)
        results.add("--- Rekordy IP (A / AAAA) ---")
        addresses.forEach {
            val type = if (it.hostAddress?.contains(":") == true) "IPv6 (AAAA)" else "IPv4 (A)"
            results.add("$type: ${it.hostAddress}")
        }
        results.add("\nCanonical Host Name:")
        results.add(addresses.firstOrNull()?.canonicalHostName ?: "Brak")
    } catch (e: Exception) {
        results.add("🔴 Nie znaleziono Rekordów DNS dla $domain ($e)")
    }
    results
}