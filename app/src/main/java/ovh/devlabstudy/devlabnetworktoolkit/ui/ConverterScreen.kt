package ovh.devlabstudy.devlabnetworktoolkit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ovh.devlabstudy.devlabnetworktoolkit.R
import ovh.devlabstudy.devlabnetworktoolkit.util.ConverterUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen() {
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
                text = { Text(stringResource(R.string.tab_number_systems)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.tab_data_storage)) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(stringResource(R.string.tab_ip_converter)) }
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
                0 -> NumberSystemsView()
                1 -> DataAndStorageCalculatorView()
                2 -> IpConverterView()
            }
        }
    }
}

@Composable
private fun NumberSystemsView() {
    val context = LocalContext.current

    var decInput by remember { mutableStateOf("255") }
    var binInput by remember { mutableStateOf("11111111") }
    var octInput by remember { mutableStateOf("377") }
    var hexInput by remember { mutableStateOf("FF") }

    var activeField by remember { mutableStateOf("DEC") }

    val decimalValue = remember(decInput, binInput, octInput, hexInput, activeField) {
        when (activeField) {
            "DEC" -> decInput.toLongOrNull() ?: 0L
            "BIN" -> binInput.toLongOrNull(2) ?: 0L
            "OCT" -> octInput.toLongOrNull(8) ?: 0L
            "HEX" -> hexInput.toLongOrNull(16) ?: 0L
            else -> 0L
        }
    }

    LaunchedEffect(decimalValue) {
        if (activeField != "DEC") decInput = decimalValue.toString()
        if (activeField != "BIN") binInput = java.lang.Long.toBinaryString(decimalValue)
        if (activeField != "OCT") octInput = java.lang.Long.toOctalString(decimalValue)
        if (activeField != "HEX") hexInput = java.lang.Long.toHexString(decimalValue).uppercase()
    }

    val shareTitle = stringResource(R.string.share_report_title)
    val reportText = buildString {
        append("--- KONWERTER SYSTEMÓW LICZBOWYCH ---\n")
        append("DEC: $decInput\n")
        append("BIN: $binInput\n")
        append("OCT: $octInput\n")
        append("HEX: 0x$hexInput")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.title_number_systems),
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = {
            ConverterUtils.exportAndShareData(context, shareTitle, reportText)
        }) {
            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.content_desc_share))
        }
    }

    OutlinedTextField(
        value = decInput,
        onValueChange = {
            activeField = "DEC"
            decInput = it.filter { char -> char.isDigit() }
        },
        label = { Text(stringResource(R.string.label_decimal_value)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    OutlinedTextField(
        value = binInput,
        onValueChange = {
            activeField = "BIN"
            binInput = it.filter { char -> char == '0' || char == '1' }
        },
        label = { Text(stringResource(R.string.label_binary)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    OutlinedTextField(
        value = octInput,
        onValueChange = {
            activeField = "OCT"
            octInput = it.filter { char -> char in '0'..'7' }
        },
        label = { Text(stringResource(R.string.label_octal)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )

    OutlinedTextField(
        value = hexInput,
        onValueChange = {
            activeField = "HEX"
            hexInput = it.filter { char -> char.isDigit() || char.uppercaseChar() in 'A'..'F' }.uppercase()
        },
        label = { Text(stringResource(R.string.label_hex)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
}

@Composable
private fun DataAndStorageCalculatorView() {
    val context = LocalContext.current

    var storageInput by remember { mutableStateOf("1000") }
    var speedInput by remember { mutableStateOf("300") }
    var fileSizeInput by remember { mutableStateOf("50") }

    val gbVal = remember(storageInput) { storageInput.toDoubleOrNull() ?: 0.0 }
    val speedMbps = remember(speedInput) { speedInput.toDoubleOrNull() ?: 0.0 }
    val fileSizeGb = remember(fileSizeInput) { fileSizeInput.toDoubleOrNull() ?: 0.0 }

    val bytesDecimal = remember(gbVal) { gbVal * 1_000_000_000.0 }
    val gibBinary = remember(bytesDecimal) { bytesDecimal / (1024.0 * 1024.0 * 1024.0) }
    val tibBinary = remember(gibBinary) { gibBinary / 1024.0 }

    val speedMBps = remember(speedMbps) { speedMbps / 8.0 }
    val downloadTimeFormatted = remember(speedMbps, fileSizeGb) {
        ConverterUtils.calculateDownloadTime(speedMbps, fileSizeGb)
    }

    val gibFormatted = remember(gibBinary) { ConverterUtils.formatDecimal(gibBinary) }
    val tibFormatted = remember(tibBinary) { ConverterUtils.formatDecimal(tibBinary) }
    val bytesFormatted = remember(bytesDecimal) { bytesDecimal.toLong().toString() }
    val speedMBpsFormatted = remember(speedMBps) { ConverterUtils.formatDecimal(speedMBps) }

    val reportText = stringResource(R.string.data_report_header) + "\n" +
            stringResource(R.string.report_storage, gbVal.toString(), gibFormatted, tibFormatted) + "\n" +
            stringResource(R.string.report_speed, speedMbps.toString(), speedMBpsFormatted) + "\n" +
            stringResource(R.string.report_file_size, fileSizeGb.toString()) + "\n" +
            stringResource(R.string.report_download_time, downloadTimeFormatted)

    val shareTitle = stringResource(R.string.share_report_title)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.title_data_storage),
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = {
            ConverterUtils.exportAndShareData(context, shareTitle, reportText)
        }) {
            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.content_desc_share))
        }
    }

    OutlinedTextField(
        value = storageInput,
        onValueChange = { storageInput = it },
        label = { Text(stringResource(R.string.label_disk_size)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ResultRow(stringResource(R.string.label_gib_system), "$gibFormatted GiB")
                ResultRow(stringResource(R.string.label_tib_system), "$tibFormatted TiB")
                ResultRow(stringResource(R.string.label_bytes_decimal), "$bytesFormatted B")
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    Text(
        text = stringResource(R.string.title_speed_download),
        style = MaterialTheme.typography.titleMedium
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = speedInput,
            onValueChange = { speedInput = it },
            label = { Text(stringResource(R.string.label_speed_mbps)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = fileSizeInput,
            onValueChange = { fileSizeInput = it },
            label = { Text(stringResource(R.string.label_file_size_gb)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ResultRow(stringResource(R.string.label_speed_mb_s), "$speedMBpsFormatted MB/s")
                ResultRow(stringResource(R.string.label_download_time), downloadTimeFormatted)
            }
        }
    }
}

@Composable
private fun IpConverterView() {
    val context = LocalContext.current
    var ipInput by remember { mutableStateOf("192.168.1.1") }
    val ipDetails = remember(ipInput) { ConverterUtils.convertIpAddress(ipInput) }

    val shareTitle = stringResource(R.string.share_report_title)
    val reportText = if (ipDetails != null) {
        "--- KONWERTER IP ---\nAdres: $ipInput\nBin: ${ipDetails.binary}\nHex: ${ipDetails.hex}\nInt: ${ipDetails.integerVal}"
    } else {
        "--- KONWERTER IP ---\nBłędny adres: $ipInput"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.title_ip_converter),
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = {
            ConverterUtils.exportAndShareData(context, shareTitle, reportText)
        }) {
            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.content_desc_share))
        }
    }

    OutlinedTextField(
        value = ipInput,
        onValueChange = { ipInput = it },
        label = { Text(stringResource(R.string.label_ipv4_address)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = ipDetails == null && ipInput.isNotEmpty(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
    )

    if (ipDetails != null) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            SelectionContainer {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ResultRow(stringResource(R.string.label_binary_form), ipDetails.binary)
                    ResultRow(stringResource(R.string.label_hex_form), ipDetails.hex)
                    ResultRow(stringResource(R.string.label_integer_val), ipDetails.integerVal.toString())
                }
            }
        }
    } else if (ipInput.isNotEmpty()) {
        Text(
            text = stringResource(R.string.error_invalid_ip),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
        )
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}