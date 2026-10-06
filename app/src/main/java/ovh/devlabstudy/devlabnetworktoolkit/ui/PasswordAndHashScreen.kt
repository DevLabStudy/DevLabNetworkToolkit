package ovh.devlabstudy.devlabnetworktoolkit.ui

import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordAndHashScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Hasła") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Konwertery & Hash") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedTab) {
                0 -> PasswordToolView()
                1 -> FileAndDataConvertersView()
            }
        }
    }
}

@Composable
private fun PasswordToolView() {
    var passwordLength by remember { mutableFloatStateOf(16f) }
    var useUppercase by remember { mutableStateOf(true) }
    var useLowercase by remember { mutableStateOf(true) }
    var useNumbers by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }

    var generatedPassword by remember { mutableStateOf("") }

    fun generate() {
        val chars = buildString {
            if (useUppercase) append("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
            if (useLowercase) append("abcdefghijklmnopqrstuvwxyz")
            if (useNumbers) append("0123456789")
            if (useSymbols) append("!@#$%^&*()_+-=[]{}|;:,.<>?")
        }
        if (chars.isEmpty()) {
            generatedPassword = "Wybierz przynajmniej jeden zestaw znaków!"
            return
        }
        val length = passwordLength.toInt()
        generatedPassword = (1..length)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }

    LaunchedEffect(Unit) {
        if (generatedPassword.isEmpty()) {
            generate()
        }
    }

    Text(
        text = "Generator i Tester Haseł",
        style = MaterialTheme.typography.titleMedium
    )

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SelectionContainer {
                Text(
                    text = generatedPassword.ifEmpty { "Kliknij generuj" },
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Button(
                onClick = { generate() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wygeneruj nowe hasło")
            }
        }
    }

    Text(text = "Długość: ${passwordLength.toInt()} znaków", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = passwordLength,
        onValueChange = { passwordLength = it },
        onValueChangeFinished = { generate() },
        valueRange = 8f..32f,
        steps = 23
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useUppercase, onCheckedChange = { useUppercase = it })
            Text("Wielkie litery (A-Z)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useLowercase, onCheckedChange = { useLowercase = it })
            Text("Małe litery (a-z)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useNumbers, onCheckedChange = { useNumbers = it })
            Text("Cyfry (0-9)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useSymbols, onCheckedChange = { useSymbols = it })
            Text("Znaki specjalne (!@#...)")
        }
    }
}

@Composable
private fun FileAndDataConvertersView() {
    var rawInput by remember { mutableStateOf("admin:admin123") }

    val base64Encoded = remember(rawInput) {
        try {
            Base64.encodeToString(rawInput.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        } catch (e: Exception) {
            "Błąd kodowania"
        }
    }

    val base64Decoded = remember(rawInput) {
        try {
            val decodedBytes = Base64.decode(rawInput, Base64.NO_WRAP)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "Niepoprawny Base64"
        }
    }

    val urlEncoded = remember(rawInput) {
        try {
            URLEncoder.encode(rawInput, "UTF-8")
        } catch (e: Exception) {
            "Błąd URL"
        }
    }

    val urlDecoded = remember(rawInput) {
        try {
            URLDecoder.decode(rawInput, "UTF-8")
        } catch (e: Exception) {
            "Niepoprawny URL"
        }
    }

    val sha256Hash = remember(rawInput) {
        try {
            val bytes = MessageDigest.getInstance("SHA-256").digest(rawInput.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "Błąd Hash"
        }
    }

    Text(
        text = "Konwertery i Sumy Kontrolne (Hash)",
        style = MaterialTheme.typography.titleMedium
    )

    OutlinedTextField(
        value = rawInput,
        onValueChange = { rawInput = it },
        label = { Text("Tekst do przetworzenia / odkodowania") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
        maxLines = 3
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResultRow("Base64 (Encoded):", base64Encoded)
                ResultRow("Base64 (Decoded):", base64Decoded)
                HorizontalDivider()
                ResultRow("URL Encoded:", urlEncoded)
                ResultRow("URL Decoded:", urlDecoded)
                HorizontalDivider()
                ResultRow("SHA-256:", sha256Hash)
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f)
        )
    }
}