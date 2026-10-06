package ovh.devlabstudy.devlabnetworktoolkit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import kotlinx.coroutines.launch
import ovh.devlabstudy.devlabnetworktoolkit.billing.BillingManager
import ovh.devlabstudy.devlabnetworktoolkit.service.NetworkBackgroundService
import ovh.devlabstudy.devlabnetworktoolkit.ui.*
import ovh.devlabstudy.devlabnetworktoolkit.util.LicenseManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                MainAppScreen()
            }
        }
    }
}

enum class AppTool(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    PING("Ping", "Test opóźnień ICMP", Icons.Default.PlayArrow),
    INFO("Info", "IP, Wi-Fi, interfejsy", Icons.Default.Info),
    LAN_SCANNER("Skaner LAN", "Wykrywaj urządzenia", Icons.Default.Devices),
    PORT_SCANNER("Skaner Portów", "Skanowanie portów TCP", Icons.Default.Search),
    SUBNET_CALC("Kalkulator", "Obliczanie podsieci CIDR/VLSM", Icons.Default.Calculate),
    CONVERTER("Konwerter", "Systemy liczbowe & Dysk", Icons.Default.Transform),
    TRACEROUTE("Traceroute", "Śledź trasę pakietów", Icons.Default.Route),
    DNS_LOOKUP("DNS", "Rekordy A, AAAA, MX", Icons.Default.Dns),
    WOL("Wake-on-LAN", "Zdalnie włączaj PC", Icons.Default.PowerSettingsNew),
    SPEEDTEST("Test Łącza", "Pomiary Jitteru", Icons.Default.Speed),
    PASSWORD_TOOL("Hasła & Hash", "Generator haseł i konwertery", Icons.Default.Lock)
}

enum class ProPlan {
    MONTHLY,
    YEARLY,
    LIFETIME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val billingManager = remember { BillingManager(context, coroutineScope) }
    val licenseManager = remember { LicenseManager(context) }

    val isBillingProPurchased by billingManager.isProPurchased.collectAsState(initial = false)
    val isProUser = isBillingProPurchased || licenseManager.isPremium()

    val prefs = remember { context.getSharedPreferences("devlab_layout_prefs", Context.MODE_PRIVATE) }

    val defaultList = AppTool.entries.toList()
    var toolsList by remember { mutableStateOf(loadSavedLayout(prefs, defaultList)) }
    var columnsCount by remember { mutableIntStateOf(prefs.getInt("columns_count", 4)) }

    var activeTool by remember { mutableStateOf(AppTool.LAN_SCANNER) }
    var isEditMode by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }
    var showProThankYouDialog by remember { mutableStateOf(false) }
    var selectedForSwapIndex by remember { mutableStateOf<Int?>(null) }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    val mainPanelTools = toolsList.take(columnsCount)
    val extraTools = toolsList.drop(columnsCount)

    LaunchedEffect(Unit) {
        billingManager.startConnection()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val activity = context as? ComponentActivity
            activity?.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    LaunchedEffect(toolsList, columnsCount) {
        saveLayout(prefs, toolsList, columnsCount)
    }

    if (showProDialog) {
        ProPaywallDialog(
            billingManager = billingManager,
            licenseManager = licenseManager,
            onDismiss = { showProDialog = false }
        )
    }

    if (showProThankYouDialog) {
        ProThankYouDialog(onDismiss = { showProThankYouDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = activeTool.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = {
                        NetworkBackgroundService.startPingMonitor(context, "8.8.8.8")
                    }) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = "Skanuj w tle")
                    }

                    if (!isProUser) {
                        Button(
                            onClick = { showProDialog = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFFFD700), Color(0xFFFFA500))
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PRZEJDŹ PRO",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0xFF2E7D32),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable { showProThankYouDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "PRO",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { topBarPadding ->
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 130.dp,
            sheetContent = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .navigationBarsPadding()
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnsCount),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 8.dp)
                    ) {
                        itemsIndexed(
                            items = mainPanelTools,
                            key = { _, tool -> "main_${tool.name}" }
                        ) { index, tool ->
                            val isSwapSelected = selectedForSwapIndex == index

                            ToolCard(
                                tool = tool,
                                isSelected = tool == activeTool,
                                isSwapSelected = isSwapSelected,
                                isMainPanel = true,
                                onClick = {
                                    if (isEditMode) {
                                        if (selectedForSwapIndex == null) {
                                            selectedForSwapIndex = index
                                        } else if (selectedForSwapIndex == index) {
                                            selectedForSwapIndex = null
                                        } else {
                                            val mutable = toolsList.toMutableList()
                                            val firstIdx = selectedForSwapIndex!!
                                            val temp = mutable[firstIdx]
                                            mutable[firstIdx] = mutable[index]
                                            mutable[index] = temp
                                            toolsList = mutable
                                            selectedForSwapIndex = null
                                        }
                                    } else {
                                        activeTool = tool
                                    }
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEditMode) "Tryb edycji (kliknij 2 ikonki aby zamienić)" else "Wszystkie narzędzia",
                            style = MaterialTheme.typography.titleMedium
                        )

                        IconButton(onClick = {
                            isEditMode = !isEditMode
                            coroutineScope.launch {
                                if (isEditMode) {
                                    sheetState.expand()
                                }
                            }
                        }) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = "Edytuj panel"
                            )
                        }
                    }

                    if (isEditMode) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Kolumny:", style = MaterialTheme.typography.labelMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    (2..5).forEach { count ->
                                        FilterChip(
                                            selected = columnsCount == count,
                                            onClick = { columnsCount = count },
                                            label = { Text("$count") }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            thickness = 1.5.dp
                        )
                        Text(
                            text = "  ▼ Menu  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            thickness = 1.5.dp
                        )
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnsCount),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        itemsIndexed(
                            items = extraTools,
                            key = { _, tool -> "extra_${tool.name}" }
                        ) { index, tool ->
                            val realIndex = index + columnsCount
                            val isSwapSelected = selectedForSwapIndex == realIndex

                            ToolCard(
                                tool = tool,
                                isSelected = tool == activeTool,
                                isSwapSelected = isSwapSelected,
                                isMainPanel = false,
                                onClick = {
                                    if (isEditMode) {
                                        if (selectedForSwapIndex == null) {
                                            selectedForSwapIndex = realIndex
                                        } else if (selectedForSwapIndex == realIndex) {
                                            selectedForSwapIndex = null
                                        } else {
                                            val mutable = toolsList.toMutableList()
                                            val firstIdx = selectedForSwapIndex!!
                                            val temp = mutable[firstIdx]
                                            mutable[firstIdx] = mutable[realIndex]
                                            mutable[realIndex] = temp
                                            toolsList = mutable
                                            selectedForSwapIndex = null
                                        }
                                    } else {
                                        activeTool = tool
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(topBarPadding)
                    .padding(innerPadding)
            ) {
                when (activeTool) {
                    AppTool.PING -> PingScreen()
                    AppTool.INFO -> NetworkInfoScreen()
                    AppTool.LAN_SCANNER -> LanScannerScreen()
                    AppTool.PORT_SCANNER -> PortScannerScreen()
                    AppTool.SUBNET_CALC -> SubnetCalculatorScreen()
                    AppTool.CONVERTER -> ConverterScreen()
                    AppTool.TRACEROUTE -> TracerouteScreen()
                    AppTool.DNS_LOOKUP -> DnsLookupScreen()
                    AppTool.WOL -> WolScreen()
                    AppTool.SPEEDTEST -> SpeedtestScreen()
                    AppTool.PASSWORD_TOOL -> PasswordAndHashScreen()
                }
            }
        }
    }
}

@Composable
fun ToolCard(
    tool: AppTool,
    isSelected: Boolean,
    isSwapSelected: Boolean,
    isMainPanel: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (isSwapSelected) 1.1f else 1.0f, label = "scale")

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSwapSelected -> MaterialTheme.colorScheme.tertiaryContainer
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isMainPanel -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .border(
                width = if (isSwapSelected) 2.dp else if (isMainPanel) 1.5.dp else 0.dp,
                color = if (isSwapSelected) MaterialTheme.colorScheme.tertiary else if (isMainPanel) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = if (isSwapSelected) MaterialTheme.colorScheme.onTertiaryContainer else if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- OKNO PODZIĘKOWAŃ DLA UŻYTKOWNIKÓW PRO ---
@Composable
fun ProThankYouDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF4CAF50), Color(0xFF1B5E20)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Wersja PRO jest aktywna!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Dziękuję, że wspierasz mój projekt! Korzystasz z pełnej wersji DevLab Network Toolkit bez żadnych ograniczeń.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Masz pytania, pomysł na funkcję lub chcesz napisać?",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:devlabstudy@devlabstudy.ovh")
                            putExtra(Intent.EXTRA_SUBJECT, "DevLab Network Toolkit - Kontakt Ogólny")
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "devlabstudy@devlabstudy.ovh",
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Znalazłeś błąd w aplikacji?",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:bledy@devlabstudy.ovh")
                            putExtra(Intent.EXTRA_SUBJECT, "DevLab Network Toolkit - Zgłoszenie Błędu")
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "bledy@devlabstudy.ovh",
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33334D)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Zamknij",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ProPaywallDialog(
    billingManager: BillingManager? = null,
    licenseManager: LicenseManager? = null,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedPlan by remember { mutableStateOf(ProPlan.LIFETIME) }

    var keyInput by remember { mutableStateOf("") }
    var keyStatusMessage by remember { mutableStateOf("") }
    var isActivatingKey by remember { mutableStateOf(false) }

    val prices by billingManager?.productPrices?.collectAsState(initial = emptyMap()) ?: remember { mutableStateOf(emptyMap()) }

    val lifetimePriceText = prices[BillingManager.LIFETIME_PRO] ?: "99,99 zł"
    val yearlyPriceText = prices[BillingManager.SUB_YEARLY] ?: "49,99 zł"
    val monthlyPriceText = prices[BillingManager.SUB_MONTHLY] ?: "9,99 zł"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2C)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFFFFD700), Color(0xFF8B6508)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "DevLab Toolkit PRO",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = "Odblokuj zaawansowany monitoring w tle, pełną historię oraz brak reklam.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedPlan == ProPlan.LIFETIME) Color(0xFF2D2B52) else Color(0xFF161622)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (selectedPlan == ProPlan.LIFETIME) 2.dp else 1.dp,
                            color = if (selectedPlan == ProPlan.LIFETIME) Color(0xFFFFD700) else Color.DarkGray,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedPlan = ProPlan.LIFETIME }
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Dostęp Wieczysty",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "$lifetimePriceText / opłata jednorazowa",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            RadioButton(
                                selected = selectedPlan == ProPlan.LIFETIME,
                                onClick = { selectedPlan = ProPlan.LIFETIME },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFD700))
                            )
                        }

                        Surface(
                            color = Color(0xFFFFD700),
                            shape = RoundedCornerShape(bottomStart = 8.dp, topEnd = 14.dp),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Text(
                                text = "NAJPOPULARNIEJSZE",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedPlan == ProPlan.YEARLY) Color(0xFF2D2B52) else Color(0xFF161622)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (selectedPlan == ProPlan.YEARLY) 2.dp else 1.dp,
                            color = if (selectedPlan == ProPlan.YEARLY) Color(0xFFFFD700) else Color.DarkGray,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedPlan = ProPlan.YEARLY }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Plan Roczny",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "$yearlyPriceText / rok",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        RadioButton(
                            selected = selectedPlan == ProPlan.YEARLY,
                            onClick = { selectedPlan = ProPlan.YEARLY },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFD700))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedPlan == ProPlan.MONTHLY) Color(0xFF2D2B52) else Color(0xFF161622)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (selectedPlan == ProPlan.MONTHLY) 2.dp else 1.dp,
                            color = if (selectedPlan == ProPlan.MONTHLY) Color(0xFFFFD700) else Color.DarkGray,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedPlan = ProPlan.MONTHLY }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Plan Miesięczny",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "$monthlyPriceText / miesiąc",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        RadioButton(
                            selected = selectedPlan == ProPlan.MONTHLY,
                            onClick = { selectedPlan = ProPlan.MONTHLY },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFD700))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it.uppercase() },
                        label = { Text("Masz kod promocyjny?", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFD700),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedLabelColor = Color(0xFFFFD700)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (keyInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                if (licenseManager != null) {
                                    isActivatingKey = true
                                    keyStatusMessage = ""
                                    coroutineScope.launch {
                                        val result = licenseManager.activateKey(keyInput.trim())
                                        isActivatingKey = false
                                        result.fold(
                                            onSuccess = { msg ->
                                                keyStatusMessage = msg
                                                onDismiss()
                                            },
                                            onFailure = { err ->
                                                keyStatusMessage = err.message ?: "Błąd klucza"
                                            }
                                        )
                                    }
                                }
                            },
                            enabled = !isActivatingKey,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33334D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isActivatingKey) "Aktywacja..." else "Użyj kodu licencji",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (keyStatusMessage.isNotEmpty()) {
                        Text(
                            text = keyStatusMessage,
                            color = Color.Red,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (activity != null && billingManager != null) {
                            val productId = when (selectedPlan) {
                                ProPlan.LIFETIME -> BillingManager.LIFETIME_PRO
                                ProPlan.YEARLY -> BillingManager.SUB_YEARLY
                                ProPlan.MONTHLY -> BillingManager.SUB_MONTHLY
                            }
                            billingManager.launchPurchaseFlow(activity, productId)
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when (selectedPlan) {
                            ProPlan.LIFETIME -> "Kup Na Zawsze ($lifetimePriceText)"
                            ProPlan.YEARLY -> "Wypróbuj Roczny ($yearlyPriceText)"
                            ProPlan.MONTHLY -> "Aktywuj Miesięczny ($monthlyPriceText)"
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text("Może później", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun loadSavedLayout(prefs: SharedPreferences, defaultList: List<AppTool>): List<AppTool> {
    val savedOrder = prefs.getString("tools_order", null) ?: return defaultList
    val names = savedOrder.split(",")
    val loaded = names.mapNotNull { name ->
        try { AppTool.valueOf(name) } catch (_: Exception) { null }
    }
    return if (loaded.size == defaultList.size) loaded else defaultList
}

private fun saveLayout(prefs: SharedPreferences, list: List<AppTool>, columns: Int) {
    val orderString = list.joinToString(",") { it.name }
    prefs.edit {
        putString("tools_order", orderString)
        putInt("columns_count", columns)
    }
}