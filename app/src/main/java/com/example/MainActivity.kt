package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.AlertHistory
import com.example.data.database.StockAlert
import com.example.data.repository.StockRepository
import com.example.ui.StockAgentViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = androidx.lifecycle.ViewModelProvider(this)[StockAgentViewModel::class.java]
        handleIntent(intent, viewModel)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MidnightNavy),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    StockAgentMainScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val viewModel = androidx.lifecycle.ViewModelProvider(this)[StockAgentViewModel::class.java]
        handleIntent(intent, viewModel)
    }

    private fun handleIntent(intent: Intent?, viewModel: StockAgentViewModel) {
        if (intent?.getBooleanExtra("show_alert_detail", false) == true) {
            val ticker = intent.getStringExtra("ticker") ?: ""
            val message = intent.getStringExtra("message") ?: ""
            val detail = intent.getStringExtra("detail") ?: ""
            viewModel.setActiveNotification(ticker, message, detail)
        }
    }
}

@Composable
fun StockAgentMainScreen(
    modifier: Modifier = Modifier,
    viewModel: StockAgentViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val allAlerts by viewModel.allAlerts.collectAsStateWithLifecycle()
    val allHistory by viewModel.allHistory.collectAsStateWithLifecycle()
    val activeNotification by viewModel.activeNotification.collectAsStateWithLifecycle()

    if (activeNotification != null) {
        val (ticker, message, detail) = activeNotification!!
        
        AlertDialog(
            onDismissRequest = { viewModel.clearActiveNotification() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Alerta Centinela",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Informe Centinela: $ticker",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = message,
                        color = ElectricBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                    Divider(color = BorderBlue.copy(alpha = 0.5f))
                    Text(
                        text = detail,
                        color = LightText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearActiveNotification() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Cerrar Informe", color = MidnightNavy, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CardSlate,
            shape = RoundedCornerShape(16.dp)
        )
    }

    val context = LocalContext.current

    var hasNotificationPermission by remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            )
        } else {
            mutableStateOf(true)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
            if (!isGranted) {
                Toast.makeText(context, "Las alertas no se mostrarán en la barra de estado del móvil sin este permiso.", Toast.LENGTH_LONG).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightNavy)
    ) {
        // App Header
        AppHeader(onRunAgentClick = {
            val wasChecking = viewModel.isCheckingAlerts.value
            viewModel.runMonitoringAgent()
            if (wasChecking) {
                Toast.makeText(context, "Vigilancia de bolsa detenida.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Iniciando agente autónomo de bolsa...", Toast.LENGTH_SHORT).show()
            }
        }, viewModel = viewModel)

        // Navigation Tabs Row
        TabSelector(
            selectedTab = selectedTab,
            onTabSelected = { viewModel.setSelectedTab(it) }
        )

        Divider(color = BorderBlue, thickness = 1.dp)

        // Screen Body with nice transitions
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(viewModel = viewModel, activeAlerts = allAlerts)
                1 -> SetupAlertScreen(viewModel = viewModel)
                2 -> HistoryScreen(viewModel = viewModel, historyLogs = allHistory)
                3 -> TechnicalAssistantScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun AppHeader(onRunAgentClick: () -> Unit, viewModel: StockAgentViewModel) {
    val isChecking by viewModel.isCheckingAlerts.collectAsStateWithLifecycle()
    val checkingStatus by viewModel.checkingStatus.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = "Stock Agent Icon",
                    tint = EmeraldGreen,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Centinela Bolsa",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                    Text(
                        text = "Agente Autónomo de Alertas",
                        fontSize = 11.sp,
                        color = GrayText
                    )
                }
            }

            // High-fidelity active monitoring button
            Button(
                onClick = onRunAgentClick,
                enabled = true,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isChecking) CoralRed else EmeraldGreen,
                    contentColor = if (isChecking) Color.White else MidnightNavy
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("run_agent_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = if (isChecking) Icons.Default.Cancel else Icons.Default.PlayArrow,
                    contentDescription = "Run Surveillance",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isChecking) "Detener" else "Vigilar Bolsa",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Monitoring activity line logs
        if (checkingStatus.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(CardSlate.copy(alpha = 0.5f))
                    .border(1.dp, BorderBlue, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Status",
                    tint = if (isChecking) AmberGold else EmeraldGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = checkingStatus,
                    fontSize = 11.sp,
                    color = LightText,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TabSelector(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab,
        containerColor = MidnightNavy,
        contentColor = ElectricBlue,
        edgePadding = 12.dp,
        divider = {},
        indicator = { tabPositions ->
            if (tabPositions.isNotEmpty()) {
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = ElectricBlue,
                    height = 2.dp
                )
            }
        }
    ) {
        val tabs = listOf(
            TabItem("Watchlist", Icons.Default.ShowChart),
            TabItem("Configurar", Icons.Default.AddAlert),
            TabItem("Historial", Icons.Default.Notifications),
            TabItem("AI Coach", Icons.Default.Psychology)
        )

        tabs.forEachIndexed { idx, tab ->
            Tab(
                selected = selectedTab == idx,
                onClick = { onTabSelected(idx) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        modifier = Modifier.size(20.dp),
                        tint = if (selectedTab == idx) ElectricBlue else GrayText
                    )
                },
                text = {
                    Text(
                        text = tab.title,
                        fontSize = 12.sp,
                        fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == idx) LightText else GrayText
                    )
                },
                modifier = Modifier.height(56.dp)
            )
        }
    }
}

data class TabItem(val title: String, val icon: ImageVector)

@Composable
fun DashboardScreen(viewModel: StockAgentViewModel, activeAlerts: List<StockAlert>) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchedQuote by viewModel.searchedQuote.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val errorMsg by viewModel.searchError.collectAsStateWithLifecycle()
    val presetsList by viewModel.presets.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Real-time stock validation row
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Confirmar Cotización Real",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                    Text(
                        text = "Introduce el ticker de la acción (ej: SAN.MC, TEF.MC, ^IBEX, AAPL) para cotejar en tiempo real.",
                        fontSize = 11.sp,
                        color = GrayText,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Ticker de Bolsa", color = GrayText, fontSize = 14.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = BorderBlue,
                                focusedTextColor = LightText,
                                unfocusedTextColor = LightText,
                                focusedContainerColor = MidnightNavy,
                                unfocusedContainerColor = MidnightNavy
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { viewModel.performSearch() },
                            modifier = Modifier
                                .height(56.dp)
                                .testTag("search_ticker_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(color = MidnightNavy, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search stock info",
                                    tint = MidnightNavy
                                )
                            }
                        }
                    }

                    if (errorMsg != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = errorMsg!!, color = CoralRed, fontSize = 12.sp)
                    }
                }
            }
        }

        // Search quote response card + Line Chart
        item {
            searchedQuote?.let { quote ->
                val isPositive = quote.changePercent >= 0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSlate),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isPositive) EmeraldGreen.copy(alpha = 0.5f) else CoralRed.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = quote.ticker,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = LightText
                                    )
                                    if (quote.isFallback) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "SIMULADO",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AmberGold,
                                            modifier = Modifier
                                                .background(AmberGold.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .border(0.5.dp, AmberGold, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = when (quote.ticker) {
                                        "^IBEX" -> "Ibex 35 de Madrid"
                                        "SAN.MC" -> "Banco Santander S.A."
                                        "TEF.MC" -> "Telefónica S.A."
                                        "BBVA.MC" -> "Banco Bilbao Vizcaya"
                                        "AAPL" -> "Apple Inc."
                                        else -> "Cotización de Bolsa"
                                    },
                                    fontSize = 12.sp,
                                    color = GrayText
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (quote.ticker == "^IBEX") {
                                        String.format(Locale.ROOT, "%,.1f pto", quote.price)
                                    } else {
                                        String.format(Locale.ROOT, "%.2f €/$$", quote.price)
                                    },
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isPositive) EmeraldGreen else CoralRed
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = "Trend direction",
                                        tint = if (isPositive) EmeraldGreen else CoralRed,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = String.format(Locale.ROOT, "%.2f%%", quote.changePercent),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPositive) EmeraldGreen else CoralRed
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom Vector Live Trading-App Chart!
                        StockChart(
                            prices = quote.pricesHistory,
                            isPositive = isPositive,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "Cierre previo", fontSize = 10.sp, color = GrayText)
                                Text(text = String.format("%.2f", quote.previousClose), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LightText)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Volumen diario", fontSize = 10.sp, color = GrayText)
                                Text(text = formatVolume(quote.volume), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LightText)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val isBullish = if (quote.pricesHistory.size >= 2) quote.price > quote.pricesHistory.first() else false
                                Text(text = "Tendencia (5d)", fontSize = 10.sp, color = GrayText)
                                Text(
                                    text = if (isBullish) "📈 Alcista" else "📉 Bajista",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBullish) EmeraldGreen else CoralRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = BorderBlue.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(14.dp))

                        val matchingAlerts = activeAlerts.filter { it.ticker.uppercase() == quote.ticker.uppercase() }
                        if (matchingAlerts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "🛡️ Alertas Activas en Vigilancia:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            matchingAlerts.forEach { alert ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MidnightNavy, RoundedCornerShape(8.dp))
                                        .border(1.dp, BorderBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (alert.alertTrend?.startsWith("TRADERS:") == true) {
                                                    "Comité: " + alert.alertTrend.substringAfter("TRADERS:")
                                                } else "Parámetros Numéricos",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ElectricBlue
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Text(
                                                    text = if (alert.isActive) "ACTIVA" else "DESACTIVADA",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (alert.isActive) EmeraldGreen else GrayText,
                                                    modifier = Modifier
                                                        .background(if (alert.isActive) EmeraldGreen.copy(alpha = 0.15f) else BorderBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        .border(0.5.dp, if (alert.isActive) EmeraldGreen else BorderBlue, RoundedCornerShape(4.dp))
                                                        .clickable { viewModel.toggleAlertActive(alert) }
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Borrar alerta",
                                                    tint = CoralRed,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { viewModel.deleteAlert(alert) }
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        val details = mutableListOf<String>()
                                        alert.minBuyPrice?.let { minB -> alert.maxBuyPrice?.let { maxB -> details.add("Horquilla: $minB - $maxB") } }
                                        alert.stopLoss?.let { details.add("Stop Loss: $it") }
                                        if (alert.takeProfit2 != null) {
                                            alert.takeProfit?.let { details.add("TP 1: $it") }
                                            details.add("TP 2: ${alert.takeProfit2}")
                                        } else {
                                            alert.takeProfit?.let { details.add("Take Profit: $it") }
                                        }
                                        alert.minPrice?.let { details.add("Mín: $it") }
                                        alert.maxPrice?.let { details.add("Máx: $it") }
                                        alert.minVolume?.let { details.add("Vol Mín: $it") }
                                        alert.pctChange?.let { details.add("Oscilación: ±$it%") }

                                        if (details.isNotEmpty()) {
                                            Text(
                                                text = details.joinToString("  •  "),
                                                fontSize = 11.sp,
                                                color = GrayText
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = BorderBlue.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(14.dp))

                        val isPreset = presetsList.any { it.first.uppercase() == quote.ticker.uppercase() }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (isPreset) {
                                        viewModel.removePreset(quote.ticker)
                                    } else {
                                        viewModel.addPreset(quote.ticker, when (quote.ticker) {
                                            "^IBEX" -> "IBEX 35"
                                            "SAN.MC" -> "Santander"
                                            "TEF.MC" -> "Telefónica"
                                            "BBVA.MC" -> "BBVA"
                                            "AAPL" -> "Apple"
                                            else -> quote.ticker
                                        })
                                    }
                                },
                                border = BorderStroke(1.dp, if (isPreset) CoralRed else EmeraldGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isPreset) CoralRed else EmeraldGreen
                                )
                            ) {
                                Icon(
                                    imageVector = if (isPreset) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Presets",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isPreset) CoralRed else EmeraldGreen
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPreset) "Quitar Preset" else "Añadir Preset",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active alert conditions overview
        item {
            Text(
                text = "Tus Alertas en Vigilancia (${activeAlerts.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LightText,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (activeAlerts.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardSlate)
                        .border(1.dp, BorderBlue, RoundedCornerShape(8.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterNone,
                        contentDescription = "No alerts",
                        tint = GrayText,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Vigilancia vacía",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                    Text(
                        text = "No tienes alertas de bolsa configuradas. Ve a la sección 'Configurar' para entrenar tu agente de vigilancia.",
                        fontSize = 11.sp,
                        color = GrayText,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(activeAlerts) { alert ->
                AlertRuleCard(alert = alert, onToggleActive = { viewModel.toggleAlertActive(alert) }, onDelete = { viewModel.deleteAlert(alert) })
            }
        }
    }
}

@Composable
fun AlertRuleCard(alert: StockAlert, onToggleActive: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSlate),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (alert.isActive) ElectricBlue.copy(alpha = 0.5f) else BorderBlue)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = alert.ticker, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LightText)
                    Text(text = alert.name, fontSize = 12.sp, color = GrayText)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = alert.isActive,
                        onCheckedChange = { onToggleActive() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldGreen,
                            checkedTrackColor = EmeraldGreen.copy(alpha = 0.4f),
                            uncheckedThumbColor = GrayText,
                            uncheckedTrackColor = MidnightNavy
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp) // Minimum tappable size
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete alert", tint = CoralRed)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Limits description chip row
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                spacing = 6.dp
            ) {
                alert.minBuyPrice?.let {
                    LimitChip(label = "Horquilla Mín", value = "$it", icon = Icons.Default.ArrowDownward, tint = ElectricBlue)
                }
                alert.maxBuyPrice?.let {
                    LimitChip(label = "Horquilla Máx", value = "$it", icon = Icons.Default.ArrowUpward, tint = ElectricBlue)
                }
                alert.minPrice?.let {
                    LimitChip(label = "Suelo", value = "$it", icon = Icons.Default.ArrowDownward, tint = CoralRed)
                }
                alert.maxPrice?.let {
                    LimitChip(label = "Techo", value = "$it", icon = Icons.Default.ArrowUpward, tint = EmeraldGreen)
                }
                alert.minVolume?.let {
                    LimitChip(label = "Vol Mín", value = formatVolume(it), icon = Icons.Default.TrendingUp, tint = AmberGold)
                }
                alert.pctChange?.let {
                    LimitChip(label = "Variación", value = "±$it%", icon = Icons.Default.CompareArrows, tint = ElectricBlue)
                }
                alert.stopLoss?.let {
                    LimitChip(label = "Stop Loss", value = "$it", icon = Icons.Default.ReportProblem, tint = CoralRed)
                }
                if (alert.takeProfit2 != null) {
                    alert.takeProfit?.let {
                        LimitChip(label = "TP 1", value = "$it", icon = Icons.Default.MonetizationOn, tint = EmeraldGreen)
                    }
                    alert.takeProfit2.let {
                        LimitChip(label = "TP 2", value = "$it", icon = Icons.Default.MonetizationOn, tint = EmeraldGreen)
                    }
                } else {
                    alert.takeProfit?.let {
                        LimitChip(label = "Take Profit", value = "$it", icon = Icons.Default.MonetizationOn, tint = EmeraldGreen)
                    }
                }
                if (alert.alertTrend != "NONE") {
                    val isBullish = alert.alertTrend == "BULLISH"
                    val isSecretSentinel = alert.alertTrend?.startsWith("TRADERS:") == true
                    LimitChip(
                        label = if (isSecretSentinel) "Mesa AI" else "Tendencia",
                        value = if (isSecretSentinel) "Centinela" else if (isBullish) "Alcista" else "Bajista",
                        icon = if (isBullish) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        tint = if (isBullish) EmeraldGreen else CoralRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = "Push active", tint = EmeraldGreen, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Notificaciones Móviles Activas", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = EmeraldGreen)
            }
        }
    }
}

@Composable
fun LimitChip(label: String, value: String, icon: ImageVector, tint: Color) {
    Row(
        modifier = Modifier
            .padding(vertical = 3.dp)
            .background(MidnightNavy, RoundedCornerShape(6.dp))
            .border(1.dp, BorderBlue, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "$label: ", fontSize = 11.sp, color = GrayText)
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightText)
    }
}

// Custom simple FlowRow Composable for chip wraps
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        var rowWidth = 0
        var rowHeight = 0
        var totalHeight = 0
        val horizontalSpacing = spacing.roundToPx()
        val verticalSpacing = spacing.roundToPx()

        val placeables = measurables.map { measurable ->
            measurable.measure(constraints)
        }

        placeables.forEach { placeable ->
            if (rowWidth + placeable.width > constraints.maxWidth) {
                totalHeight += rowHeight + verticalSpacing
                rowWidth = 0
                rowHeight = 0
            }
            rowWidth += placeable.width + horizontalSpacing
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        totalHeight += rowHeight

        layout(constraints.maxWidth, maxOf(totalHeight, 0)) {
            var x = 0
            var y = 0
            var maxH = 0
            placeables.forEach { placeable ->
                if (x + placeable.width > constraints.maxWidth) {
                    y += maxH + verticalSpacing
                    x = 0
                    maxH = 0
                }
                placeable.place(x, y)
                x += placeable.width + horizontalSpacing
                maxH = maxOf(maxH, placeable.height)
            }
        }
    }
}

@Composable
fun SetupAlertScreen(viewModel: StockAgentViewModel) {
    val prefillTicker by viewModel.configTickerPrefill.collectAsStateWithLifecycle()
    val prefillName by viewModel.configNamePrefill.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()

    var ticker by remember { mutableStateOf("SAN.MC") }
    var name by remember { mutableStateOf("Banco Santander") }

    LaunchedEffect(prefillTicker, prefillName) {
        if (prefillTicker.isNotEmpty()) {
            ticker = prefillTicker
            name = prefillName
            viewModel.clearConfigurePrefill()
        }
    }

    var minPrice by remember { mutableStateOf("") }
    var maxPrice by remember { mutableStateOf("") }
    var minVolume by remember { mutableStateOf("") }
    var pctChange by remember { mutableStateOf("") }
    var stopLoss by remember { mutableStateOf("") }
    var takeProfit by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("robertoruizmena@gmail.com") }

    val trends = listOf("Ninguna", "ALCISTA", "BAJISTA")
    var selectedTrendIndex by remember { mutableStateOf(0) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderBlue)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Añadir Alerta de Bolsa",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )
                Text(
                    text = "Introduce el ticker y establece los umbrales financieros personalizados que dispararán la automatización del agente de bolsa.",
                    fontSize = 11.sp,
                    color = GrayText,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Presets
                Text(text = "Presets de Ticker comunes (Toca para seleccionar, 'x' para quitar):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightText)
                Spacer(modifier = Modifier.height(6.dp))
                if (presets.isEmpty()) {
                    Text(
                        text = "No hay presets de ticker guardados. Búscalos en la pestaña principal para añadirlos aquí.",
                        fontSize = 11.sp,
                        color = GrayText
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        spacing = 6.dp
                    ) {
                        presets.forEach { preset ->
                            Row(
                                modifier = Modifier
                                    .background(MidnightNavy, RoundedCornerShape(6.dp))
                                    .border(1.dp, BorderBlue, RoundedCornerShape(6.dp))
                                    .clickable {
                                        ticker = preset.first
                                        name = preset.second
                                    }
                                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = preset.first,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricBlue
                                )
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { viewModel.removePreset(preset.first) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Eliminar Preset",
                                        tint = CoralRed,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Basic details
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = ticker,
                        onValueChange = { ticker = it },
                        label = { Text("Ticker (ej. SAN.MC)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = ElectricBlue,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre (ej. Santander)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = ElectricBlue,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Suelo and Techo price limits
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = minPrice,
                        onValueChange = { minPrice = it },
                        label = { Text("Suelo (Precio Mín)", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = ElectricBlue,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = maxPrice,
                        onValueChange = { maxPrice = it },
                        label = { Text("Techo (Precio Máx)", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = ElectricBlue,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stop loss and take profit
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = stopLoss,
                        onValueChange = { stopLoss = it },
                        label = { Text("Stop Loss (SL)", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Límite pérdidas", fontSize = 11.sp, color = GrayText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CoralRed,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = CoralRed,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = takeProfit,
                        onValueChange = { takeProfit = it },
                        label = { Text("Take Profit (TP)", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Metas rentabilidad", fontSize = 11.sp, color = GrayText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmeraldGreen,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = EmeraldGreen,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Min Volume & Volatility percentage
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = minVolume,
                        onValueChange = { minVolume = it },
                        label = { Text("Volumen Mín (Transacciones)", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.3f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = ElectricBlue,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = pctChange,
                        onValueChange = { pctChange = it },
                        label = { Text("Variación Mín %", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("ej. 2.5", fontSize = 11.sp, color = GrayText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = BorderBlue,
                            focusedLabelColor = ElectricBlue,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Trend targeting drop-down / row of selectors
                Text(
                    text = "Gatillo de Tendencia para este Activo:",
                    fontSize = 11.sp,
                    color = GrayText,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trends.forEachIndexed { index, tr ->
                        val isSelected = selectedTrendIndex == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isSelected) ElectricBlue else MidnightNavy, RoundedCornerShape(8.dp))
                                .border(1.dp, if (isSelected) ElectricBlue else BorderBlue, RoundedCornerShape(8.dp))
                                .clickable { selectedTrendIndex = index }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MidnightNavy else LightText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Info Card about Mobile Push alerts
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MidnightNavy),
                    border = BorderStroke(1.dp, BorderBlue.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Push Active",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Notificaciones Push Activas",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightText
                            )
                            Text(
                                text = "El agente enviará notificaciones instantáneas a tu teléfono móvil al cumplirse las condiciones técnicas.",
                                fontSize = 10.sp,
                                color = GrayText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Add Button
                Button(
                    onClick = {
                        val minP = minPrice.toDoubleOrNull()
                        val maxP = maxPrice.toDoubleOrNull()
                        val minV = minVolume.toLongOrNull()
                        val pctC = pctChange.toDoubleOrNull()
                        val sl = stopLoss.toDoubleOrNull()
                        val tp = takeProfit.toDoubleOrNull()

                        if (ticker.isBlank()) {
                            return@Button
                        }

                        viewModel.addAlert(
                            ticker = ticker,
                            name = name,
                            minP = minP,
                            maxP = maxP,
                            minV = minV,
                            pctC = pctC,
                            sl = sl,
                            tp = tp,
                            trend = trends[selectedTrendIndex],
                            email = "Dispositivo Móvil"
                        )

                        // Clear fields and switch to tab 0
                        minPrice = ""
                        maxPrice = ""
                        minVolume = ""
                        pctChange = ""
                        stopLoss = ""
                        takeProfit = ""
                        viewModel.setSelectedTab(0)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("add_alert_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = "Savealert rule")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Guardar Parámetros de Alerta", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(viewModel: StockAgentViewModel, historyLogs: List<AlertHistory>) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Historial Log y Alertas",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )
                Text(
                    text = "Alertas transaccionadas y confirmadas por el Agente.",
                    fontSize = 11.sp,
                    color = GrayText
                )
            }

            if (historyLogs.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearLog() },
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("clear_logs_button")
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear logs", tint = CoralRed)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Vaciar", color = CoralRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (historyLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "All monitored ok",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sin incidencias disparadas",
                        color = LightText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "El agente está vigilando la bolsa. Aquí aparecerán los reportes cuando los activos superen tus umbrales o condiciones.",
                        color = GrayText,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(historyLogs) { h ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSlate),
                        border = BorderStroke(1.dp, when (h.alertType) {
                            "STOP_LOSS", "MIN_PRICE" -> CoralRed.copy(alpha = 0.5f)
                            "TAKE_PROFIT", "MAX_PRICE" -> EmeraldGreen.copy(alpha = 0.5f)
                            else -> BorderBlue
                        }),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (h.alertType) {
                                            "MIN_PRICE", "STOP_LOSS" -> Icons.Default.Warning
                                            "MAX_PRICE", "TAKE_PROFIT" -> Icons.Default.MonetizationOn
                                            "TREND" -> Icons.Default.TrendingUp
                                            else -> Icons.Default.Info
                                        },
                                        contentDescription = null,
                                        tint = when (h.alertType) {
                                            "STOP_LOSS", "MIN_PRICE" -> CoralRed
                                            "TAKE_PROFIT", "MAX_PRICE" -> EmeraldGreen
                                            "TREND" -> ElectricBlue
                                            else -> AmberGold
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = h.ticker,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LightText
                                    )
                                }

                                Text(
                                    text = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()).format(Date(h.timestamp)),
                                    fontSize = 11.sp,
                                    color = GrayText
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = h.message,
                                fontSize = 13.sp,
                                color = LightText,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Simulate living push notifier
                                Button(
                                    onClick = {
                                        viewModel.sendPushNotification(h.ticker, h.message)
                                        Toast.makeText(context, "Se ha disparado la notificación en la barra de estado.", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("simulate_push_button_${h.id}"),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "Simular Notificación",
                                        tint = MidnightNavy,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Probar Push",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MidnightNavy
                                    )
                                }

                                // Share analytical documentation
                                OutlinedButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "Reporte Móvil Bolsa: ${h.ticker}")
                                            putExtra(Intent.EXTRA_TEXT, h.emailContent)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Compartir Reporte Bolsa..."))
                                    },
                                    border = BorderStroke(1.dp, BorderBlue),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(44.dp)
                                        .testTag("share_report_button_${h.id}"),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Compartir Reporte",
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Compartir Reporte",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElectricBlue
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TechnicalAssistantScreen(viewModel: StockAgentViewModel) {
    val searchedQuote by viewModel.searchedQuote.collectAsStateWithLifecycle()
    val isRunning by viewModel.isAiRunning.collectAsStateWithLifecycle()
    val advice by viewModel.aiRecommendation.collectAsStateWithLifecycle()
    val selectedTraders by viewModel.selectedTraders.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val context = LocalContext.current

    var slInput by remember(searchedQuote?.ticker) { mutableStateOf("") }
    var tpInput by remember(searchedQuote?.ticker) { mutableStateOf("") }
    var tp2Input by remember(searchedQuote?.ticker) { mutableStateOf("") }
    var minBuyPriceInput by remember(searchedQuote?.ticker) { mutableStateOf("") }
    var maxBuyPriceInput by remember(searchedQuote?.ticker) { mutableStateOf("") }

    fun parseReportValue(text: String, key: String): Double? {
        val line = text.split("\n").find { it.contains(key) } ?: return null
        val valueStr = line.substringAfter(key).trim()
        val match = Regex("""([0-9]+(?:\.[0-9]+)?)""").find(valueStr)
        return match?.value?.toDoubleOrNull()
    }

    LaunchedEffect(advice) {
        advice?.let { text ->
            val parsedMinBuy = parseReportValue(text, "VALOR_COMPRA_MIN:")
            val parsedMaxBuy = parseReportValue(text, "VALOR_COMPRA_MAX:")
            val parsedSL = parseReportValue(text, "VALOR_STOP_LOSS:")
            val parsedTP1 = parseReportValue(text, "VALOR_TAKE_PROFIT_1:")
            val parsedTP2 = parseReportValue(text, "VALOR_TAKE_PROFIT_2:")

            if (parsedMinBuy != null) minBuyPriceInput = String.format(java.util.Locale.US, "%.2f", parsedMinBuy)
            if (parsedMaxBuy != null) maxBuyPriceInput = String.format(java.util.Locale.US, "%.2f", parsedMaxBuy)
            if (parsedSL != null) slInput = String.format(java.util.Locale.US, "%.2f", parsedSL)
            if (parsedTP1 != null) tpInput = String.format(java.util.Locale.US, "%.2f", parsedTP1)
            if (parsedTP2 != null) tp2Input = String.format(java.util.Locale.US, "%.2f", parsedTP2)
        }
    }

    val tradersList = listOf(
        Triple("ITURRALDE", "Alberto Iturralde", "Operativa DAX: Manipulación, tramas ocultas de mercado y psicología de masas."),
        Triple("SAEZ", "Antonio Sáez del Castillo", "Gesmovasa: Ondas de Elliott, pautas puras del precio y HCH de giro."),
        Triple("CAVA", "José Luis Cava", "Setup Técnico: Confluencia estricta de ADX, MACD, Estocástico y volumen clímax."),
        Triple("ORTEGA", "Alexis Ortega", "Timing & Macro global, flujos y media móvil exponencial semanal."),
        Triple("GIL", "Pablo Gil", "Sexto Magnífico: Fase del ciclo, excesos de múltiplo, Fibonacci y ratio R/R 1:2."),
        Triple("LASVIGNES", "Carlos Lasvignes", "Compra a Cero: Disciplina inviolable, stop loss obligatorio, T1 parcial y lección técnica."),
        Triple("MASTER_PROMPT", "Master Prompt de trading", "Filtro macro sistémico, checklist pre-trade y abogado del diablo.")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Consultar Mesa Redonda Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderBlue)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Consultoria",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mesa Redonda AI de Bolsa",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Selecciona tus asesores de la lista y el master prompt para auditar en conjunto la cotización y volumen de la acción seleccionada en tiempo real.",
                    fontSize = 11.5.sp,
                    color = GrayText,
                    lineHeight = 16.sp
                )
            }
        }

        // Active Stock Selector Preview
        if (searchedQuote == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSlate.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderBlue.copy(alpha = 0.5f))
            ) {
                Box(modifier = Modifier.padding(20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "⚠️ Realiza una búsqueda de ticker en 'Watchlist' para activar la Mesa de Consultores",
                        color = AmberGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val quote = searchedQuote!!
            
            // Stock details & setup card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "ACTIVO EN TIEMPO REAL", fontSize = 10.sp, color = GrayText, fontWeight = FontWeight.Bold)
                            Text(text = quote.ticker, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LightText)
                            Text(text = "${quote.ticker} • ${quote.volume} Acciones hoy", fontSize = 12.sp, color = GrayText)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "${String.format("%.2f", quote.price)} €", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                            Text(
                                text = "${if (quote.changePercent >= 0) "+" else ""}${String.format("%.2f", quote.changePercent)}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (quote.changePercent >= 0) EmeraldGreen else CoralRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = BorderBlue.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(text = "🎯 Configurar Centinela para esta Acción", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Vigila el activo de forma constante en segundo plano y dispara una notificación push con análisis de los traders configurados.",
                        fontSize = 11.sp,
                        color = GrayText,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = slInput,
                            onValueChange = { slInput = it },
                            label = { Text("Stop Loss (Opcional)", fontSize = 10.sp, color = GrayText) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = BorderBlue,
                                focusedContainerColor = MidnightNavy,
                                unfocusedContainerColor = MidnightNavy
                            )
                        )
                        OutlinedTextField(
                            value = tpInput,
                            onValueChange = { tpInput = it },
                            label = { Text("Take Profit (Opcional)", fontSize = 10.sp, color = GrayText) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = BorderBlue,
                                focusedContainerColor = MidnightNavy,
                                unfocusedContainerColor = MidnightNavy
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val sl = slInput.toDoubleOrNull()
                            val tp = tpInput.toDoubleOrNull()
                            viewModel.activateTradersSentinel(
                                ticker = quote.ticker,
                                name = "Consultoría " + quote.ticker,
                                traders = selectedTraders,
                                sl = sl,
                                tp = tp
                            )
                            Toast.makeText(context, "🛡️ ¡Centinela activado para ${quote.ticker} con ${selectedTraders.size} traders!", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = "Active Sentinel", tint = MidnightNavy, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Activar Centinela en este valor", color = MidnightNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Checklist of selected traders
            Text(
                text = "CONSEJO COMPAÑEROS DE LA MESA (${selectedTraders.size} Activos)",
                color = GrayText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tradersList.forEach { (id, name, desc) ->
                    val isChecked = selectedTraders.contains(id)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleTraderSelection(id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isChecked) CardSlate else CardSlate.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isChecked) EmeraldGreen else BorderBlue.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { viewModel.toggleTraderSelection(id) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = EmeraldGreen,
                                    uncheckedColor = GrayText
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    color = if (isChecked) Color.White else LightText.copy(alpha = 0.7f),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    color = if (isChecked) GrayText else GrayText.copy(alpha = 0.7f),
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Run Button
            Button(
                onClick = { viewModel.requestTradersConsultancy(quote.ticker, quote.price, quote.volume, quote.changePercent) },
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("analyze_ticker_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(color = MidnightNavy, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Reuniendo Mesa de Asesores...", fontWeight = FontWeight.Bold, color = MidnightNavy, fontSize = 13.sp)
                } else {
                    Icon(imageVector = Icons.Default.Chat, contentDescription = "Run analysis")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Emitir Informe de Asesoría AI", fontWeight = FontWeight.Bold, color = MidnightNavy, fontSize = 13.sp)
                }
            }

            // Report Advice Display
            if (advice != null) {
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                val currentText = advice!!
                
                // Smart consensus detection from the advisory text
                val upperAdvice = currentText.uppercase()
                
                // 1. Check for standard machine token from repository
                var detectedBuy = false
                var detectedSell = false
                var detectedWait = false

                if (upperAdvice.contains("VEREDICTO_MESA:")) {
                    val verdictSection = upperAdvice.substringAfter("VEREDICTO_MESA:")
                    if (verdictSection.contains("COMPRAR") || verdictSection.contains("COMPRA")) {
                        detectedBuy = true
                    } else if (verdictSection.contains("VENDER") || verdictSection.contains("VENTA")) {
                        detectedSell = true
                    } else if (verdictSection.contains("ESPERAR") || verdictSection.contains("MANTENER")) {
                        detectedWait = true
                    }
                }

                // 2. Fallback if the token is not present
                if (!detectedBuy && !detectedSell && !detectedWait) {
                    val consensusText = when {
                        upperAdvice.contains("CONCLUSIÓN") -> {
                            val idx = upperAdvice.lastIndexOf("CONCLUSIÓN")
                            upperAdvice.substring(idx)
                        }
                        upperAdvice.contains("CONSENSO") -> {
                            val idx = upperAdvice.lastIndexOf("CONSENSO")
                            upperAdvice.substring(idx)
                        }
                        upperAdvice.contains("MESA") -> {
                            val idx = upperAdvice.lastIndexOf("MESA")
                            upperAdvice.substring(idx)
                        }
                        else -> upperAdvice.takeLast(1000)
                    }

                    val linesList = consensusText.split("\n")
                    val sentenciaLine = linesList.find { 
                        it.contains("SENTENCIA UNIFICADA") || 
                        it.contains("SENTENCIA") || 
                        it.contains("RECOMENDACIÓN") || 
                        it.contains("VEREDICTO") ||
                        it.contains("CONSENSO")
                    }

                    if (sentenciaLine != null) {
                        val sUpper = sentenciaLine.uppercase()
                        detectedBuy = sUpper.contains("COMPRA") || sUpper.contains("COMPRAR") || sUpper.contains("ENTRAR") || sUpper.contains("ADQUISICIÓN") || sUpper.contains("LARGOS")
                        detectedSell = sUpper.contains("VENTA") || sUpper.contains("VENDER") || sUpper.contains("NO ENTRAR") || sUpper.contains("EVITAR") || sUpper.contains("NO OPERAR") || sUpper.contains("SHORT")
                        detectedWait = sUpper.contains("ESPERAR") || sUpper.contains("MANTENER") || sUpper.contains("OBSERVAR") || sUpper.contains("NEUTRO")

                        if (detectedBuy && detectedSell) {
                            val buyIdx = listOf("COMPRA", "COMPRAR", "ENTRAR", "ADQUISICIÓN", "LARGOS").map { sUpper.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
                            val sellIdx = listOf("VENTA", "VENDER", "NO ENTRAR", "EVITAR", "NO OPERAR", "SHORT").map { sUpper.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
                            if (buyIdx >= 0 && sellIdx >= 0) {
                                if (buyIdx < sellIdx) detectedSell = false else detectedBuy = false
                            } else if (buyIdx >= 0) {
                                detectedSell = false
                            } else {
                                detectedBuy = false
                            }
                        }
                    }

                    if (!detectedBuy && !detectedSell && !detectedWait) {
                        val buyKeywords = listOf("COMPRA", "COMPRAR", "ENTRAR", "ADQUISICIÓN", "SETUP LISTO", "LARGOS", "BULLISH")
                        val sellKeywords = listOf("VENTA", "VENDER", "NO ENTRAR", "EVITAR", "NO OPERAR", "SHORT", "BEARISH")
                        val waitKeywords = listOf("ESPERAR", "MANTENER", "OBSERVAR", "NEUTRO")

                        val buyCount = buyKeywords.sumOf { k -> consensusText.split(k).size - 1 }
                        val sellCount = sellKeywords.sumOf { k -> consensusText.split(k).size - 1 }
                        val waitCount = waitKeywords.sumOf { k -> consensusText.split(k).size - 1 }

                        if (buyCount > sellCount && buyCount > waitCount) {
                            detectedBuy = true
                        } else if (sellCount > buyCount && sellCount > waitCount) {
                            detectedSell = true
                        } else {
                            detectedWait = true
                        }
                    }
                }

                val currentVerdict: String
                val currentVerdictColor: Color
                val currentVerdictIcon: ImageVector

                if (detectedBuy && !detectedSell) {
                    currentVerdict = "RECOMENDACIÓN: COMPRAR / ENTRAR"
                    currentVerdictColor = EmeraldGreen
                    currentVerdictIcon = Icons.Default.TrendingUp
                } else if (detectedSell) {
                    currentVerdict = "RECOMENDACIÓN: NO ENTRAR / VENDER"
                    currentVerdictColor = CoralRed
                    currentVerdictIcon = Icons.Default.TrendingDown
                } else {
                    currentVerdict = "RECOMENDACIÓN: ESPERAR / MANTENER"
                    currentVerdictColor = AmberGold
                    currentVerdictIcon = Icons.Default.TrendingFlat
                }

                val isBuy = currentVerdictColor == EmeraldGreen
                val isSell = currentVerdictColor == CoralRed

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSlate),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Title row & Copy Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Article,
                                    contentDescription = "Report Advice",
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Informe Global Colectivo de la Mesa",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LightText
                                )
                            }
                            
                            // Copy Button
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(currentText))
                                    Toast.makeText(context, "📋 ¡Informe de asesoría copiado al portapapeles!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(BorderBlue.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderBlue, RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copiar Informe",
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Glowing Verdict Status Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(currentVerdictColor.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .border(1.5.dp, currentVerdictColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(currentVerdictColor, RoundedCornerShape(6.dp))
                                )
                                Icon(
                                    imageVector = currentVerdictIcon,
                                    contentDescription = "Estado",
                                    tint = currentVerdictColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = currentVerdict,
                                    color = currentVerdictColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Semaphoric state dashboard: Buy/Hold/Sell
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Buy indicator card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isBuy && !isSell) EmeraldGreen.copy(alpha = 0.12f) else MidnightNavy,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isBuy && !isSell) EmeraldGreen else BorderBlue.copy(alpha = 0.6f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (isBuy && !isSell) EmeraldGreen else EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    )
                                    Text(
                                        text = "COMPRAR",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBuy && !isSell) Color.White else GrayText.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            // Wait/Hold indicator card
                            val isWait = !isBuy && !isSell || (isBuy && isSell)
                            Box(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .background(
                                        if (isWait) AmberGold.copy(alpha = 0.12f) else MidnightNavy,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isWait) AmberGold else BorderBlue.copy(alpha = 0.6f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (isWait) AmberGold else AmberGold.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    )
                                    Text(
                                        text = "ESPERAR / MANTENER",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isWait) Color.White else GrayText.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            // Sell indicator card
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSell) CoralRed.copy(alpha = 0.12f) else MidnightNavy,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSell) CoralRed else BorderBlue.copy(alpha = 0.6f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (isSell) CoralRed else CoralRed.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    )
                                    Text(
                                        text = "VENDER / EVITAR",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSell) Color.White else GrayText.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Divider(color = BorderBlue.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Visual formatted report lines
                        val lines = currentText.split("\n")
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("VEREDICTO_MESA:")) {
                                // Skip showing the parser metadata tag in the visible report list
                            } else if (trimmed.startsWith("===") && trimmed.endsWith("===")) {
                                val headerText = trimmed.replace("===", "").trim()
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "⚡ $headerText",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricBlue,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else if (trimmed.startsWith("•") || trimmed.startsWith("-")) {
                                val bulletText = trimmed.substring(1).trim()
                                Row(
                                    modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(text = "🔹", fontSize = 11.sp, color = ElectricBlue)
                                    Text(
                                        text = bulletText,
                                        fontSize = 12.5.sp,
                                        color = LightText,
                                        lineHeight = 18.sp
                                    )
                                }
                            } else if (trimmed.isNotEmpty()) {
                                Text(
                                    text = trimmed,
                                    fontSize = 12.5.sp,
                                    color = LightText,
                                    lineHeight = 19.sp,
                                    fontFamily = FontFamily.Default,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StockChart(
    prices: List<Double>,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (prices.size < 2) return@Canvas

        val minPrice = prices.minOrNull() ?: 1.0
        val maxPrice = prices.maxOrNull() ?: 100.0
        val range = if (maxPrice - minPrice == 0.0) 1.0 else maxPrice - minPrice

        val width = size.width
        val height = size.height

        val stepX = width / (prices.size - 1)
        val path = Path()

        // Normalize coordinates and construct path
        prices.forEachIndexed { i, p ->
            val x = i * stepX
            val y = height - (((p - minPrice) / range) * (height - 20f)).toFloat() - 10f

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Color setup
        val strokeColor = if (isPositive) EmeraldGreen else CoralRed
        val fillGradient = Brush.verticalGradient(
            colors = listOf(
                strokeColor.copy(alpha = 0.35f),
                strokeColor.copy(alpha = 0.0f)
            ),
            startY = 0f,
            endY = height
        )

        // Fill background path underneath the quote lines
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = fillGradient
        )

        // Draw line graph
        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        // Draw circular node on current price (last index)
        val lastX = width
        val lastY = height - (((prices.last() - minPrice) / range) * (height - 20f)).toFloat() - 10f
        drawCircle(
            color = strokeColor,
            radius = 5.dp.toPx(),
            center = Offset(lastX, lastY)
        )
        drawCircle(
            color = MidnightNavy,
            radius = 2.dp.toPx(),
            center = Offset(lastX, lastY)
        )
    }
}

// Utility formatting converters
fun formatVolume(vol: Long): String {
    return when {
        vol >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", vol / 1_000_000.0)
        vol >= 1_000 -> String.format(Locale.ROOT, "%.1fK", vol / 1_000.0)
        else -> "$vol"
    }
}

fun sendEmailIntent(context: Context, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Enviar correo de alerta..."))
    } catch (e: Exception) {
        Toast.makeText(context, "No se encontró cliente de correo electrónico.", Toast.LENGTH_SHORT).show()
    }
}
