package ovh.devlabstudy.devlabnetworktoolkit.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ovh.devlabstudy.devlabnetworktoolkit.billing.BillingManager
import ovh.devlabstudy.devlabnetworktoolkit.util.LicenseManager
import kotlin.math.pow

// --- MODELE DANYCH ---

data class SubnetResult(
    val ip: String,
    val prefix: Int,
    val netmask: String,
    val wildcard: String,
    val networkAddress: String,
    val broadcastAddress: String,
    val firstHost: String,
    val lastHost: String,
    val totalHosts: Long,
    val usableHosts: Long
)

data class VlsmRequirement(
    val id: Int,
    val name: String,
    val hostsNeeded: Int
)

data class VlsmResult(
    val name: String,
    val hostsNeeded: Int,
    val allocatedHosts: Long,
    val prefix: Int,
    val netmask: String,
    val networkAddress: String,
    val firstHost: String,
    val lastHost: String,
    val broadcastAddress: String
)

// --- GŁÓWNY EKRAN KALKULATORA SIEĆ ---

@Composable
fun SubnetCalculatorScreen(
    billingManager: BillingManager? = null,
    onUpgradeClick: () -> Unit = {} // Wywołanie zakupu/reklamy po kliknięciu
) {
    val context = LocalContext.current

    // --- INTEGRACJA LICENCJI I PŁATNOŚCI GOOGLE PLAY ---
    val licenseManager = remember { LicenseManager(context) }
    val isLicensePro by licenseManager.isPremiumFlow.collectAsState()

    val userAccessState by if (billingManager != null) {
        billingManager.userAccessState.collectAsState()
    } else {
        remember { mutableStateOf(null) }
    }

    // Dostęp do opcji VLSM odblokowany, jeśli wpisano klucz Supabase LUB zakupiono w Google Play
    val isUserPremium = isLicensePro || (userAccessState?.hasAccessToFeature(BillingManager.MOD_LAN_SCANNER) ?: false)

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Podstawowy (CIDR)") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Kalkulator VLSM ")
                        if (!isUserPremium) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Zablokowane",
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text("👑")
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // W pełni darmowy kalkulator CIDR
            CidrCalculatorView(context)
        } else {
            // Zakładka VLSM – dostępna dla użytkowników PRO
            if (isUserPremium) {
                VlsmCalculatorView(context)
            } else {
                VlsmPaywallView(onUpgradeClick = onUpgradeClick)
            }
        }
    }
}

// --- WIDOK PAYWALL DLA VLSM ---

@Composable
private fun VlsmPaywallView(onUpgradeClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Funkcja VLSM jest zablokowana",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Kalkulator Variable Length Subnet Mask (VLSM) pozwalający na zmienny podział sieci według zapotrzebowania na hosty jest dostępny wyłącznie w wersji Premium.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onUpgradeClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Odblokuj Wersję Premium")
            }
        }
    }
}

// --- SEKCJA CIDR (DARMOWA) ---

@Composable
private fun CidrCalculatorView(context: Context) {
    var ipInput by remember { mutableStateOf("192.168.1.1") }
    var prefixInput by remember { mutableStateOf("24") }
    var result by remember { mutableStateOf<SubnetResult?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                label = { Text("Adres IP (np. 192.168.1.1)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        item {
            OutlinedTextField(
                value = prefixInput,
                onValueChange = { prefixInput = it },
                label = { Text("Maska / Prefix (np. 24)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        item {
            Button(
                onClick = {
                    val prefix = prefixInput.toIntOrNull()
                    if (isValidIp(ipInput) && prefix != null && prefix in 0..32) {
                        result = calculateSubnet(ipInput, prefix)
                    } else {
                        Toast.makeText(context, "Wprowadź poprawny adres IP i prefix (0-32)", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Oblicz")
            }
        }

        result?.let { res ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "Wyniki obliczeń CIDR:", fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        ResultRow("Adres sieci:", res.networkAddress)
                        ResultRow("Maska podsieci:", res.netmask)
                        ResultRow("Wildcard (Maska odwrotna):", res.wildcard)
                        ResultRow("Adres rozgłoszeniowy (Broadcast):", res.broadcastAddress)
                        ResultRow("Pierwszy użyteczny host:", res.firstHost)
                        ResultRow("Ostatni użyteczny host:", res.lastHost)
                        ResultRow("Liczba wszystkich adresów:", res.totalHosts.toString())
                        ResultRow("Użyteczne hosty:", res.usableHosts.toString())
                    }
                }
            }
        }
    }
}

// --- SEKCJA VLSM (PREMIUM) ---

@Composable
private fun VlsmCalculatorView(context: Context) {
    var baseIpInput by remember { mutableStateOf("192.168.1.0") }
    var basePrefixInput by remember { mutableStateOf("24") }
    var requirements by remember {
        mutableStateOf(
            listOf(
                VlsmRequirement(1, "LAN 1", 50),
                VlsmRequirement(2, "LAN 2", 20)
            )
        )
    }
    var nextId by remember { mutableIntStateOf(3) }
    var vlsmResults by remember { mutableStateOf<List<VlsmResult>>(emptyList()) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Główna sieć wejściowa:", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = baseIpInput,
                    onValueChange = { baseIpInput = it },
                    label = { Text("Adres IP") },
                    modifier = Modifier.weight(2f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = basePrefixInput,
                    onValueChange = { basePrefixInput = it },
                    label = { Text("Prefix /") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Wymagane podsieci:", fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    requirements = requirements + VlsmRequirement(nextId, "LAN $nextId", 10)
                    nextId++
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj podsieć")
                }
            }
        }

        items(requirements) { req ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = req.name,
                    onValueChange = { newName ->
                        requirements = requirements.map { if (it.id == req.id) it.copy(name = newName) else it }
                    },
                    label = { Text("Nazwa") },
                    modifier = Modifier.weight(1.5f)
                )
                OutlinedTextField(
                    value = req.hostsNeeded.toString(),
                    onValueChange = { newHosts ->
                        val hosts = newHosts.toIntOrNull() ?: 0
                        requirements = requirements.map { if (it.id == req.id) it.copy(hostsNeeded = hosts) else it }
                    },
                    label = { Text("Hosty") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                IconButton(onClick = {
                    requirements = requirements.filter { it.id != req.id }
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "Usuń")
                }
            }
        }

        item {
            Button(
                onClick = {
                    val prefix = basePrefixInput.toIntOrNull()
                    if (isValidIp(baseIpInput) && prefix != null && prefix in 0..32) {
                        vlsmResults = calculateVlsm(baseIpInput, prefix, requirements)
                        if (vlsmResults.isEmpty() && requirements.isNotEmpty()) {
                            Toast.makeText(context, "Za mała sieć główna dla tylu hostów!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Niepoprawny główny IP lub prefix", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Oblicz VLSM")
            }
        }

        if (vlsmResults.isNotEmpty()) {
            items(vlsmResults) { res ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "${res.name} (Wymagano: ${res.hostsNeeded}, Alokowano: ${res.allocatedHosts})", fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        ResultRow("Adres sieci:", "${res.networkAddress}/${res.prefix}")
                        ResultRow("Maska:", res.netmask)
                        ResultRow("Pierwszy host:", res.firstHost)
                        ResultRow("Ostatni host:", res.lastHost)
                        ResultRow("Broadcast:", res.broadcastAddress)
                    }
                }
            }
        }
    }
}

// --- POMOCNICZE KOMPONENTY UI ---

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

// --- LOGIKA OBLICZENIOWA MATEMATYKI SIECIOWEJ ---

private fun isValidIp(ip: String): Boolean {
    val parts = ip.split(".")
    if (parts.size != 4) return false
    return parts.all { part ->
        val num = part.toIntOrNull()
        num != null && num in 0..255
    }
}

private fun subnetIpToLong(ip: String): Long {
    val parts = ip.split(".")
    var result = 0L
    for (part in parts) {
        result = (result shl 8) + part.toLong()
    }
    return result
}

private fun subnetLongToIp(ipLong: Long): String {
    return "${(ipLong shr 24) and 255}.${(ipLong shr 16) and 255}.${(ipLong shr 8) and 255}.${ipLong and 255}"
}

private fun calculateSubnet(ip: String, prefix: Int): SubnetResult {
    val ipLong = subnetIpToLong(ip)
    val maskLong = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
    val wildcardLong = maskLong.inv() and 0xFFFFFFFFL

    val networkLong = ipLong and maskLong
    val broadcastLong = networkLong or wildcardLong

    val totalHosts = 2.0.pow((32 - prefix).toDouble()).toLong()
    val usableHosts = if (prefix >= 31) 0L else totalHosts - 2L

    val firstHostLong = if (usableHosts > 0) networkLong + 1L else networkLong
    val lastHostLong = if (usableHosts > 0) broadcastLong - 1L else broadcastLong

    return SubnetResult(
        ip = ip,
        prefix = prefix,
        netmask = subnetLongToIp(maskLong),
        wildcard = subnetLongToIp(wildcardLong),
        networkAddress = subnetLongToIp(networkLong),
        broadcastAddress = subnetLongToIp(broadcastLong),
        firstHost = subnetLongToIp(firstHostLong),
        lastHost = subnetLongToIp(lastHostLong),
        totalHosts = totalHosts,
        usableHosts = usableHosts
    )
}

private fun calculateVlsm(baseIp: String, basePrefix: Int, reqs: List<VlsmRequirement>): List<VlsmResult> {
    val sortedReqs = reqs.sortedByDescending { it.hostsNeeded }
    val baseMaskLong = if (basePrefix == 0) 0L else (0xFFFFFFFFL shl (32 - basePrefix)) and 0xFFFFFFFFL
    var currentIpLong = subnetIpToLong(baseIp) and baseMaskLong

    val baseTotalHosts = 2.0.pow((32 - basePrefix).toDouble()).toLong()
    val baseEndIpLong = currentIpLong + baseTotalHosts - 1L

    val results = mutableListOf<VlsmResult>()

    for (req in sortedReqs) {
        val neededWithOverhead = req.hostsNeeded + 2
        var hostBits = 0
        while ((1L shl hostBits) < neededWithOverhead) {
            hostBits++
        }
        if (hostBits < 2) hostBits = 2

        val prefix = 32 - hostBits
        val allocatedHosts = (1L shl hostBits)

        val maskLong = (0xFFFFFFFFL shl hostBits) and 0xFFFFFFFFL
        val wildcardLong = maskLong.inv() and 0xFFFFFFFFL

        val networkLong = currentIpLong
        val broadcastLong = networkLong or wildcardLong

        if (broadcastLong > baseEndIpLong) {
            return emptyList()
        }

        val firstHostLong = networkLong + 1L
        val lastHostLong = broadcastLong - 1L

        results.add(
            VlsmResult(
                name = req.name,
                hostsNeeded = req.hostsNeeded,
                allocatedHosts = allocatedHosts - 2L,
                prefix = prefix,
                netmask = subnetLongToIp(maskLong),
                networkAddress = subnetLongToIp(networkLong),
                firstHost = subnetLongToIp(firstHostLong),
                lastHost = subnetLongToIp(lastHostLong),
                broadcastAddress = subnetLongToIp(broadcastLong)
            )
        )

        currentIpLong = broadcastLong + 1L
    }

    return results
}