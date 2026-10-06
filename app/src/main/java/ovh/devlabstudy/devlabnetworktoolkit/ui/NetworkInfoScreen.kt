package ovh.devlabstudy.devlabnetworktoolkit.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.net.NetworkInterface

@Composable
fun NetworkInfoScreen() {
    val context = LocalContext.current

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var networkDetails by remember { mutableStateOf(getNetworkDetails(context, hasLocationPermission)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasLocationPermission = isGranted
        networkDetails = getNetworkDetails(context, isGranted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Informacje o Sieci", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { networkDetails = getNetworkDetails(context, hasLocationPermission) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Odśwież")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dyskretna i jasna karta wyjaśniająca mechanizm Androida (tylko gdy nie ma uprawnień)
        if (!hasLocationPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Dostęp",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dlaczego SSID wymaga lokalizacji?",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "System Android chroni prywatność i klasyfikuje nazwę sieci Wi-Fi (SSID) jako informację powiązaną z lokalizacją. Aplikacja używa tego uprawnienia wyłącznie do odczytu SSID.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Odblokuj nazwę SSID", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(networkDetails) { detail ->
                InfoCard(label = detail.first, value = detail.second)
            }
        }
    }
}

@Composable
fun InfoCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

fun getNetworkDetails(context: Context, hasPermission: Boolean): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val activeNetwork = connectivityManager.activeNetwork
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

    val connectionType = when {
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Dane Komórkowe"
        else -> "Brak połączenia"
    }
    list.add("Typ połączenia" to connectionType)

    val localIp = getLocalIpAddress() ?: "Niedostępne"
    list.add("Lokalny adres IP" to localIp)

    if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
        val wifiInfo: WifiInfo? = wifiManager.connectionInfo
        if (wifiInfo != null) {
            val rawSsid = wifiInfo.ssid.replace("\"", "")

            val ssid = if (!hasPermission) {
                "🔒 Ukryte (Wymagane uprawnienie)"
            } else if (rawSsid == "<unknown ssid>") {
                "Włącz usługę GPS w telefonie"
            } else {
                rawSsid
            }

            list.add("Nazwa sieci (SSID)" to ssid)

            val freq = wifiInfo.frequency
            val band = when {
                freq in 2400..2500 -> "2.4 GHz"
                freq in 4900..5900 -> "5 GHz"
                freq > 5900 -> "6 GHz"
                else -> "Nieznana"
            }
            list.add("Częstotliwość" to "$freq MHz ($band)")

            val rssi = wifiInfo.rssi
            val signalQuality = WifiManager.calculateSignalLevel(rssi, 100)
            list.add("Siła sygnału" to "$rssi dBm ($signalQuality%)")
            list.add("Szybkość linku" to "${wifiInfo.linkSpeed} Mbps")
        }
    }

    val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
    val dnsServers = linkProperties?.dnsServers?.joinToString("\n") { it.hostAddress ?: "" }
    list.add("Serwery DNS" to if (!dnsServers.isNullOrEmpty()) dnsServers else "Nie wykryto")

    return list
}

fun getLocalIpAddress(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address.hostAddress?.contains(':') == false) {
                    return address.hostAddress
                }
            }
        }
    } catch (_: Exception) {}
    return null
}