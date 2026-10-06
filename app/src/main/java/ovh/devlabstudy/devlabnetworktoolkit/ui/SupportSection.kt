package ovh.devlabstudy.devlabnetworktoolkit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ovh.devlabstudy.devlabnetworktoolkit.util.LicenseManager

@Composable
fun SupportSection(
    licenseManager: LicenseManager,
    onNavigateToLanScanner: () -> Unit,
    onNavigateToTraceroute: () -> Unit,
    onNavigateToDnsLookup: () -> Unit,
    onNavigateToWol: () -> Unit,
    onNavigateToSpeedtest: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Sekcja aktywacji klucza Premium na samej górze
        LicenseActivationCard(licenseManager = licenseManager)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Więcej Narzędzi",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        SupportToolCard(
            title = "Skaner Podsieci LAN",
            description = "Wykrywaj urządzenia w Wi-Fi",
            onClick = onNavigateToLanScanner
        )

        Spacer(modifier = Modifier.height(12.dp))

        SupportToolCard(
            title = "Traceroute",
            description = "Śledź trasę pakietów do serwera",
            onClick = onNavigateToTraceroute
        )

        Spacer(modifier = Modifier.height(12.dp))

        SupportToolCard(
            title = "DNS Lookup",
            description = "Rekordy A, AAAA, MX dla domen",
            onClick = onNavigateToDnsLookup
        )

        Spacer(modifier = Modifier.height(12.dp))

        SupportToolCard(
            title = "Wake-on-LAN",
            description = "Zdalnie włączaj komputery w sieci",
            onClick = onNavigateToWol
        )

        Spacer(modifier = Modifier.height(12.dp))

        SupportToolCard(
            title = "Test Jakości Łącza",
            description = "Pomiary Jitteru i opóźnień",
            onClick = onNavigateToSpeedtest
        )
    }
}

@Composable
fun LicenseActivationCard(licenseManager: LicenseManager) {
    var keyInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isPremiumActive by remember { mutableStateOf(licenseManager.isPremium()) }
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPremiumActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isPremiumActive) "Konto Premium Aktywne 🚀" else "Aktywuj Licencję Premium",
                style = MaterialTheme.typography.titleMedium,
                color = if (isPremiumActive)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isPremiumActive) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it.uppercase() },
                    label = { Text("Wpisz kod (np. DEVLAB-XXXX-YYYY)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (keyInput.isNotBlank()) {
                            isLoading = true
                            statusMessage = ""
                            scope.launch {
                                val result = licenseManager.activateKey(keyInput.trim())
                                isLoading = false
                                result.fold(
                                    onSuccess = { msg ->
                                        statusMessage = msg
                                        isPremiumActive = true
                                    },
                                    onFailure = { err ->
                                        statusMessage = err.message ?: "Błąd aktywacji klucza"
                                    }
                                )
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (isLoading) "Weryfikacja..." else "Aktywuj")
                }

                if (statusMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPremiumActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dziękujemy za wsparcie! Masz dostęp do wszystkich funkcji.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun SupportToolCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}