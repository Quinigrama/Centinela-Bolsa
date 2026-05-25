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
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.AlertHistory
import com.example.data.database.StockAlert
import com.example.data.database.IaAnalysisHistory
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
    val allIaHistory by viewModel.allIaHistory.collectAsStateWithLifecycle()
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
            if (!viewModel.isCheckingAlerts.value) {
                viewModel.runMonitoringAgent()
                Toast.makeText(context, "Iniciando agente autónomo de bolsa y actualizando cotizaciones...", Toast.LENGTH_SHORT).show()
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
                2 -> HistoryScreen(viewModel = viewModel, historyLogs = allHistory, iaLogs = allIaHistory)
                3 -> TechnicalAssistantScreen(viewModel = viewModel)
                4 -> BacktestingScreen(viewModel = viewModel)
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
                enabled = !isChecking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isChecking) EmeraldGreen.copy(alpha = 0.5f) else EmeraldGreen,
                    contentColor = MidnightNavy
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("run_agent_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        color = MidnightNavy,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Vigilando...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run Surveillance",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Vigilar Bolsa",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
            TabItem("AI Coach", Icons.Default.Psychology),
            TabItem("Backtesting", Icons.Default.Timeline)
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
                                val isBullish = if (quote.pricesHistory.size >= 2) {
                                    quote.price >= quote.pricesHistory.first()
                                } else {
                                    quote.changePercent >= 0.0
                                }
                                Text(text = "Tendencia (5d)", fontSize = 10.sp, color = GrayText)
                                Text(
                                    text = if (isBullish) "📈 Alcista" else "📉 Bajista",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBullish) EmeraldGreen else CoralRed,
                                    modifier = Modifier.clickable {
                                        viewModel.prefillConfigureScreen(quote.ticker, quote.ticker, if (isBullish) "ALCISTA" else "BAJISTA")
                                    }
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

                        if (!isPreset) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.addPreset(quote.ticker, when (quote.ticker) {
                                            "^IBEX" -> "IBEX 35"
                                            "SAN.MC" -> "Santander"
                                            "TEF.MC" -> "Telefónica"
                                            "BBVA.MC" -> "BBVA"
                                            "AAPL" -> "Apple"
                                            else -> quote.ticker
                                        })
                                    },
                                    border = BorderStroke(1.dp, EmeraldGreen),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = EmeraldGreen
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.StarBorder,
                                        contentDescription = "Presets",
                                        modifier = Modifier.size(16.dp),
                                        tint = EmeraldGreen
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Añadir Preset",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        searchedQuote?.let { quote ->
            item {
                Spacer(modifier = Modifier.height(10.dp))
                TradersDeskCard(quote = quote)
            }
        }

        // Tus Acciones Favoritas section
        item {
            Text(
                text = "Tus Acciones Favoritas (${presetsList.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = LightText,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (presetsList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSlate),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderBlue)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No tienes acciones favoritas todavía. Busca un ticker y añádelo como Preset en la lupa superior.",
                            fontSize = 12.sp,
                            color = GrayText,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(presetsList) { preset ->
                PresetWatchlistItem(preset = preset, viewModel = viewModel)
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
                alert.unusualVolumeMultiplier?.let {
                    LimitChip(
                        label = "Vol Inusual",
                        value = "${it}x Media",
                        icon = Icons.Default.TrendingUp,
                        tint = AmberGold
                    )
                }
                if (alert.condLogicalOperator != "NONE" && alert.condLogicalOperator != null) {
                    LimitChip(
                        label = "Lógica",
                        value = alert.condLogicalOperator,
                        icon = Icons.Default.Settings,
                        tint = ElectricBlue
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
fun PresetWatchlistItem(
    preset: Pair<String, String>,
    viewModel: StockAgentViewModel
) {
    var quoteState by remember { mutableStateOf<com.example.data.repository.StockRepository.QuoteDataPoint?>(null) }
    val refreshTrigger by viewModel.refreshTrigger.collectAsStateWithLifecycle()

    LaunchedEffect(preset.first, refreshTrigger) {
        try {
            val q = viewModel.repository.getQuote(preset.first)
            quoteState = q
        } catch (e: Exception) {
            // keep empty or simulated fallback
        }
    }

    val isPositive = if (quoteState != null) quoteState!!.changePercent >= 0 else true
    val isBullish = if (quoteState != null) {
        if (quoteState!!.pricesHistory.size >= 2) {
            quoteState!!.price >= quoteState!!.pricesHistory.first()
        } else {
            quoteState!!.changePercent >= 0.0
        }
    } else false

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                viewModel.updateSearchQuery(preset.first)
                viewModel.performSearch()
            },
        colors = CardDefaults.cardColors(containerColor = MidnightNavy),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderBlue.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.first,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )
                Text(
                    text = preset.second,
                    fontSize = 11.sp,
                    color = GrayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (quoteState == null) {
                CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(16.dp))
            } else {
                val q = quoteState!!
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (q.ticker == "^IBEX") {
                            String.format(Locale.ROOT, "%,.1f pto", q.price)
                        } else {
                            String.format(Locale.ROOT, "%.2f €/$$", q.price)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPositive) EmeraldGreen else CoralRed
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.ROOT, "%.2f%%", q.changePercent),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPositive) EmeraldGreen else CoralRed
                        )
                        Text(
                            text = if (isBullish) "📈 ALCISTA" else "📉 BAJISTA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBullish) EmeraldGreen else CoralRed,
                            modifier = Modifier
                                .background((if (isBullish) EmeraldGreen else CoralRed).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                .clickable {
                                    viewModel.prefillConfigureScreen(preset.first, preset.second, if (isBullish) "ALCISTA" else "BAJISTA")
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
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
    val prefillTrend by viewModel.configTrendPrefill.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()

    var ticker by remember { mutableStateOf("SAN.MC") }
    var name by remember { mutableStateOf("Banco Santander") }

    val trends = listOf("Ninguna", "ALCISTA", "BAJISTA")
    var selectedTrendIndex by remember { mutableStateOf(0) }

    LaunchedEffect(prefillTicker, prefillName, prefillTrend) {
        if (prefillTicker.isNotEmpty()) {
            ticker = prefillTicker
            name = prefillName
            selectedTrendIndex = when (prefillTrend) {
                "ALCISTA", "BULLISH" -> 1
                "BAJISTA", "BEARISH" -> 2
                else -> 0
            }
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

    var unusualVolumeMultiplier by remember { mutableStateOf("") }

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

                Spacer(modifier = Modifier.height(16.dp))

                // Unusual Volume Multiplier OutlinedTextField
                OutlinedTextField(
                    value = unusualVolumeMultiplier,
                    onValueChange = { unusualVolumeMultiplier = it },
                    label = { Text("Multiplicador de Volumen Inusual (ej: 2.5)", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("ej. 2.0 = Activa si volumen >= 2x la media", fontSize = 11.sp, color = GrayText) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = BorderBlue,
                        focusedLabelColor = ElectricBlue,
                        focusedTextColor = LightText,
                        unfocusedTextColor = LightText
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                        val volMult = unusualVolumeMultiplier.toDoubleOrNull()
                        val condOperator = "NONE"

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
                            email = "Dispositivo Móvil",
                            condOperator = condOperator,
                            volMult = volMult
                        )

                        // Clear fields and switch to tab 0
                        minPrice = ""
                        maxPrice = ""
                        minVolume = ""
                        pctChange = ""
                        stopLoss = ""
                        takeProfit = ""
                        unusualVolumeMultiplier = ""
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
fun HistoryScreen(
    viewModel: StockAgentViewModel,
    historyLogs: List<AlertHistory>,
    iaLogs: List<IaAnalysisHistory>
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var subTab by remember { mutableStateOf(0) } // 0 = Alertas, 1 = Informes IA

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
                    text = "Alertas disparadas y consultas realizadas.",
                    fontSize = 11.sp,
                    color = GrayText
                )
            }

            val hasLogs = if (subTab == 0) historyLogs.isNotEmpty() else iaLogs.isNotEmpty()
            if (hasLogs) {
                TextButton(
                    onClick = {
                        if (subTab == 0) {
                            viewModel.clearLog()
                        } else {
                            viewModel.clearIaAnalysisHistory()
                        }
                    },
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

        // Custom segmented outline control for subTab toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MidnightNavy, RoundedCornerShape(8.dp))
                .border(1.dp, BorderBlue, RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val subTabs = listOf(
                "Alertas Técnicas (${historyLogs.size})" to Icons.Default.NotificationsActive,
                "Consultas de IA (${iaLogs.size})" to Icons.Default.Psychology
            )
            subTabs.forEachIndexed { idx, pair ->
                val isSelected = subTab == idx
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) ElectricBlue else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { subTab = idx }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = pair.second,
                        contentDescription = pair.first,
                        tint = if (isSelected) MidnightNavy else GrayText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pair.first,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MidnightNavy else GrayText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (subTab == 0) {
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
                        var isExpanded by remember { mutableStateOf(false) }
                        val fullText = if (h.emailContent.isNotBlank()) "${h.message}\n\n${h.emailContent}" else h.message

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isExpanded = !isExpanded },
                            colors = CardDefaults.cardColors(containerColor = CardSlate),
                            border = BorderStroke(1.dp, if (isExpanded) EmeraldGreen.copy(alpha = 0.5f) else BorderBlue.copy(alpha = 0.3f)),
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
                                            fontSize = 15.sp,
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

                                Spacer(modifier = Modifier.height(10.dp))

                                // Code styling from Consultas de IA
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(MidnightNavy, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                when (h.alertType) {
                                                    "MIN_PRICE", "STOP_LOSS" -> CoralRed
                                                    "MAX_PRICE", "TAKE_PROFIT" -> EmeraldGreen
                                                    else -> AmberGold
                                                },
                                                RoundedCornerShape(3.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TIPO: ${h.alertType}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = LightText
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = if (isExpanded) fullText else if (fullText.length > 160) fullText.take(160) + "..." else fullText,
                                    fontSize = 12.sp,
                                    color = LightText,
                                    lineHeight = 17.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isExpanded) "ocultar detalles 🔼" else "toca para expandir informe completo 🔽",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ElectricBlue
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Copy Icon Button
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(fullText))
                                                Toast.makeText(context, "📋 ¡Informe de alerta copiado al portapapeles!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(BorderBlue.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copiar",
                                                tint = ElectricBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        // Share Icon Button
                                        IconButton(
                                            onClick = {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "Reporte Móvil Bolsa: ${h.ticker}")
                                                    putExtra(Intent.EXTRA_TEXT, fullText)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Compartir Reporte Bolsa..."))
                                            },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(BorderBlue.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Compartir",
                                                tint = ElectricBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (iaLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "No IA reports yet",
                            tint = GrayText,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Historial Consultoría IA Vacío",
                            color = LightText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ejecuta un análisis con la 'Mesa de Consultores IA' en la pestaña AI Coach para guardar tus informes aquí de forma permanente.",
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
                    items(iaLogs) { log ->
                        var isExpanded by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                            colors = CardDefaults.cardColors(containerColor = CardSlate),
                            border = BorderStroke(1.dp, if (isExpanded) EmeraldGreen.copy(alpha = 0.5f) else BorderBlue.copy(alpha = 0.3f)),
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
                                            imageVector = Icons.Default.Article,
                                            contentDescription = "IA Report",
                                            tint = EmeraldGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = log.ticker,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = LightText
                                            )
                                            Text(
                                                text = "Precio: ${String.format("%.2f", log.price)}",
                                                fontSize = 11.sp,
                                                color = GrayText
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = log.date,
                                            fontSize = 10.5.sp,
                                            color = GrayText
                                        )
                                        IconButton(
                                            onClick = { viewModel.deleteIaAnalysisHistoryById(log.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Eliminar",
                                                tint = CoralRed.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                val lineList = log.adviceText.split("\n")
                                val verdictLine = lineList.find { it.contains("RECOMENDACIÓN:") || it.contains("VEREDICTO_MESA:") || it.contains("VEREDICTO:") || it.contains("CONSENSO:") } ?: ""
                                val cleanestVerdict = when {
                                    verdictLine.isNotBlank() -> {
                                        verdictLine.replace("VEREDICTO_MESA:", "")
                                                  .replace("VEREDICTO:", "")
                                                  .replace("RECOMENDACIÓN:", "")
                                                  .replace("CONSENSO:", "")
                                                  .trim()
                                    }
                                    log.adviceText.uppercase().contains("VEREDICTO_MESA: COMPRAR") -> "COMPRAR"
                                    log.adviceText.uppercase().contains("VEREDICTO_MESA: VENDER") -> "VENDER"
                                    log.adviceText.uppercase().contains("VEREDICTO_MESA: ESPERAR") -> "ESPERAR"
                                    else -> "ESPERAR"
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(MidnightNavy, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                when {
                                                    cleanestVerdict.uppercase().contains("COMPRA") || cleanestVerdict.uppercase().contains("COMPRAR") -> EmeraldGreen
                                                    cleanestVerdict.uppercase().contains("VENTA") || cleanestVerdict.uppercase().contains("VENDER") -> CoralRed
                                                    else -> AmberGold
                                                },
                                                RoundedCornerShape(3.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = cleanestVerdict,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = LightText
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (isExpanded) {
                                    val logVerdict = when {
                                        cleanestVerdict.uppercase().contains("COMPRA") || cleanestVerdict.uppercase().contains("COMPRAR") || cleanestVerdict.uppercase().contains("ENTRAR") -> "COMPRAR"
                                        cleanestVerdict.uppercase().contains("VENTA") || cleanestVerdict.uppercase().contains("VENDER") || cleanestVerdict.uppercase().contains("NO ENTRAR") || cleanestVerdict.uppercase().contains("EVITAR") -> "VENDER"
                                        else -> "ESPERAR"
                                    }
                                    ConsensusDialGauge(verdict = logVerdict)
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                Text(
                                    text = if (isExpanded) log.adviceText else if (log.adviceText.length > 160) log.adviceText.take(160) + "..." else log.adviceText,
                                    fontSize = 12.sp,
                                    color = LightText,
                                    lineHeight = 17.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isExpanded) "ocultar detalles 🔼" else "toca para expandir informe completo 🔽",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ElectricBlue
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Copy Icon Button
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(log.adviceText))
                                                Toast.makeText(context, "📋 ¡Informe copiado al portapapeles!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(BorderBlue.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copiar",
                                                tint = ElectricBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        // Share Icon Button
                                        IconButton(
                                            onClick = {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "Mesa de Asesores de Bolsa AI: ${log.ticker}")
                                                    putExtra(Intent.EXTRA_TEXT, log.adviceText)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Compartir Consulta Bolsa..."))
                                            },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(BorderBlue.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Compartir",
                                                tint = ElectricBlue,
                                                modifier = Modifier.size(14.dp)
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
    }
}

@Composable
fun TechnicalAssistantScreen(viewModel: StockAgentViewModel) {
    val searchedQuote by viewModel.searchedQuote.collectAsStateWithLifecycle()
    val isRunning by viewModel.isAiRunning.collectAsStateWithLifecycle()
    val advice by viewModel.aiRecommendation.collectAsStateWithLifecycle()
    val selectedTraders by viewModel.selectedTraders.collectAsStateWithLifecycle()
    val selectedAiProvider by viewModel.selectedAiProvider.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

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

        // Dropdown AI Provider Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, BorderBlue)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { menuExpanded = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "AI Model",
                        tint = ElectricBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "PROVEEDOR DE INTELIGENCIA ARTIFICIAL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = GrayText,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val providerLabel = when (selectedAiProvider) {
                            "DEEPSEEK" -> "DeepSeek-R1 (Pensamiento Analítico)"
                            else -> "Gemini 2.5 Flash (Google - Por Defecto)"
                        }
                        Text(
                            text = providerLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightText
                        )
                    }
                }
                Box {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Cambiar IA",
                        tint = GrayText,
                        modifier = Modifier.size(24.dp)
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MidnightNavy).border(1.dp, BorderBlue, RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Gemini 2.5 Flash (Google)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                            onClick = {
                                viewModel.setSelectedAiProvider("GEMINI")
                                menuExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Default.Psychology, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("DeepSeek-R1 (Fidelidad)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                            onClick = {
                                viewModel.setSelectedAiProvider("DEEPSEEK")
                                menuExpanded = false
                            },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp)) }
                        )
                        Divider(color = BorderBlue.copy(alpha = 0.4f))
                        DropdownMenuItem(
                            text = { Text("Próximamente más opciones...", color = GrayText, fontSize = 12.sp) },
                            onClick = {},
                            enabled = false
                        )
                    }
                }
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
                
                // 1. Check for standard machine token from repository (extremely robust against markdown asterisks e.g. **VEREDICTO_MESA:**)
                var detectedBuy = false
                var detectedSell = false
                var detectedWait = false

                val rawCleanVal = currentText.uppercase().replace("*", "").replace("_", "")
                if (rawCleanVal.contains("VEREDICTO_MESA")) {
                    val idx = rawCleanVal.indexOf("VEREDICTO_MESA")
                    val sub = rawCleanVal.substring(idx, (idx + 60).coerceAtMost(rawCleanVal.length))
                    if (sub.contains("COMPRAR") || sub.contains("COMPRA")) {
                        detectedBuy = true
                    } else if (sub.contains("VENDER") || sub.contains("VENTA")) {
                        detectedSell = true
                    } else if (sub.contains("ESPERAR") || sub.contains("MANTENER") || sub.contains("OBSERVAR")) {
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

                        val cleanedConsensusText = consensusText
                            .replace("VALOR_COMPRA_MIN", "")
                            .replace("VALOR_COMPRA_MAX", "")
                            .replace("VALOR_STOP_LOSS", "")
                            .replace("VALOR_TAKE_PROFIT", "")

                        val buyCount = buyKeywords.sumOf { k -> cleanedConsensusText.split(k).size - 1 }
                        val sellCount = sellKeywords.sumOf { k -> cleanedConsensusText.split(k).size - 1 }
                        val waitCount = waitKeywords.sumOf { k -> cleanedConsensusText.split(k).size - 1 }

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

                        val verdictString = if (isBuy && !isSell) "COMPRAR" else if (isSell) "VENDER" else "ESPERAR"

                        // Beautiful Custom Dial/Needle Gauge Indicator!!
                        ConsensusDialGauge(verdict = verdictString)

                        Spacer(modifier = Modifier.height(14.dp))

                        // Smart Multi Horizon Suitability Grid
                        MultiHorizonSuitabilityGrid(quote = quote)

                        Spacer(modifier = Modifier.height(16.dp))

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

@Composable
fun TradersDeskCard(quote: StockRepository.QuoteDataPoint) {
    var subTabSelected by remember { mutableStateOf(0) } // 0 = Métricas, 1 = Calculadora

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSlate),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderBlue)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row with Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ShowChart,
                    contentDescription = "Mesa de Operaciones de los Traders",
                    tint = ElectricBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mesa de Operaciones del Trader",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightText
                )
            }

            // Sub-tabs segment switcher (styled nicely like a material 3 custom button group)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MidnightNavy)
                    .border(1.dp, BorderBlue, RoundedCornerShape(8.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Pair("⚡ Métricas de los Expertos", 0),
                    Pair("🎯 Calculadora de Riesgo R/R", 1)
                ).forEach { (title, index) ->
                    val isSelected = subTabSelected == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) ElectricBlue else Color.Transparent)
                            .clickable { subTabSelected = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MidnightNavy else LightText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (subTabSelected == 0) {
                // Métricas: Cava & Gil & Ortega
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 1. Cava's Volume Climax
                    val avgVolume = if (quote.volumesHistory.isNotEmpty()) quote.volumesHistory.average() else quote.volume.toDouble()
                    val volMultiplier = if (avgVolume != 0.0) quote.volume / avgVolume else 1.0
                    val isClimax = volMultiplier >= 1.5

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Volumen Clímax (José Luis Cava)", fontSize = 11.5.sp, color = GrayText, fontWeight = FontWeight.SemiBold)
                            Text(
                                    text = String.format(Locale.ROOT, "x%.2f del promedio", volMultiplier),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isClimax) AmberGold else EmeraldGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MidnightNavy, RoundedCornerShape(6.dp))
                                .border(1.dp, if (isClimax) AmberGold.copy(alpha = 0.5f) else BorderBlue, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = if (isClimax) "⚠️" else "✅", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isClimax) "¡VOLUMEN CLÍMAX DETECTADO! Gran volumen de negociación repentino. Cava advierte posible clímax de acumulación o distribución institucional."
                                           else "Volumen de negociación dentro del rango promedio normal. Sin signos de manipulación masiva ni acumulación clímax.",
                                    fontSize = 11.sp,
                                    color = if (isClimax) AmberGold else LightText,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }

                    // 2. Cava's 5d RSI
                    val rsi = calculateRsi5(quote.pricesHistory)
                    val rsiColor = when {
                        rsi >= 70.0 -> CoralRed
                        rsi <= 30.0 -> EmeraldGreen
                        else -> ElectricBlue
                    }
                    val rsiLabel = when {
                        rsi >= 70.0 -> "Sobrecompra Crítica (Zona de Distribución)"
                        rsi <= 30.0 -> "Sobrevendido (Zona de Acumulación)"
                        else -> "Zona Neutral de Consolidación"
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "RSI de Fuerza Relativa (RSI-5d)", fontSize = 11.5.sp, color = GrayText, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = String.format(Locale.ROOT, "%.1f", rsi),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = rsiColor
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Linear progress slider for RSI
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MidnightNavy)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth((rsi / 100.0).toFloat().coerceIn(0f, 1f))
                                    .background(rsiColor)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "RSI actual: $rsiLabel. ${if (rsi <= 30.0) "Cava y Gil buscarían validación de soporte para cortos o entrada en largos." else if (rsi >= 70) "Cava buscaría protección y stop ceñido para evitar correcciones." else "Timing de consolidación de tendencia."}",
                            fontSize = 11.sp,
                            color = GrayText,
                            lineHeight = 15.sp
                        )
                    }

                    // 3. Timing / EMA-5d timing de Alexis Ortega
                    val avgPrice = if (quote.pricesHistory.isNotEmpty()) quote.pricesHistory.average() else quote.price
                    val isAboveAverage = quote.price >= avgPrice

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Timing & Promedio (Alexis Ortega)", fontSize = 11.5.sp, color = GrayText, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (isAboveAverage) "Luz Verde (Alcista)" else "Luz Roja (Agotamiento)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAboveAverage) EmeraldGreen else CoralRed
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MidnightNavy, RoundedCornerShape(6.dp))
                                .border(1.dp, BorderBlue, RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = if (isAboveAverage) "📈 El precio de la acción está por encima del promedio de 5 días (${String.format(Locale.ROOT, "%.2f", avgPrice)}). Timing compatible con flujos macro alcistas y fortaleza semanal."
                                       else "📉 El precio está actualmente por debajo del promedio de 5 días (${String.format(Locale.ROOT, "%.2f", avgPrice)}). Alexis Ortega aconseja máxima cautela: los flujos semanales están corrigiendo.",
                                fontSize = 11.sp,
                                color = LightText,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // 4. Fibonacci Retracement Levels de Pablo Gil
                    val minPrice = quote.pricesHistory.minOrNull() ?: quote.price
                    val maxPrice = quote.pricesHistory.maxOrNull() ?: quote.price
                    val fibRange = maxPrice - minPrice
                    val fibLevels = if (fibRange > 0.0) {
                        listOf(
                            Pair("Fib 23.6%", maxPrice - (0.236 * fibRange)),
                            Pair("Fib 38.2%", maxPrice - (0.382 * fibRange)),
                            Pair("Fib 50.0% (Pivote)", maxPrice - (0.500 * fibRange)),
                            Pair("Fib 61.8% (Soporte Algorítmico)", maxPrice - (0.618 * fibRange))
                        )
                    } else {
                        emptyList()
                    }

                    if (fibLevels.isNotEmpty()) {
                        Column {
                            Text(text = "Niveles de Retroceso Fibonacci (Pablo Gil)", fontSize = 11.5.sp, color = GrayText, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MidnightNavy, RoundedCornerShape(8.dp))
                                    .border(1.dp, BorderBlue, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                fibLevels.forEach { (label, value) ->
                                    val isPriceNear = Math.abs(quote.price - value) / value <= 0.015
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(if (isPriceNear) ElectricBlue else GrayText.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isPriceNear) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isPriceNear) ElectricBlue else LightText
                                            )
                                        }
                                        Text(
                                            text = String.format(Locale.ROOT, "%.2f", value),
                                            fontSize = 11.sp,
                                            fontWeight = if (isPriceNear) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isPriceNear) ElectricBlue else LightText
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "💡 Tip de Pablo Gil: Los retrocesos Fibonacci sirven para identificar con precisión dónde están esperando las grandes manos institucionales para entrar en soporte.",
                                fontSize = 10.sp,
                                color = GrayText,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            } else {
                // Calculadora de Riesgo R/R de Gil & Lasvignes
                var entryText by remember(quote.ticker) { mutableStateOf(String.format(Locale.ROOT, "%.2f", quote.price)) }
                var slText by remember(quote.ticker) { mutableStateOf(String.format(Locale.ROOT, "%.2f", quote.price * 0.95)) }
                var tpText by remember(quote.ticker) { mutableStateOf(String.format(Locale.ROOT, "%.2f", quote.price * 1.10)) }

                val entryVal = entryText.toDoubleOrNull() ?: 1.0
                val slVal = slText.toDoubleOrNull() ?: 0.95
                val tpVal = tpText.toDoubleOrNull() ?: 1.10

                val riskAmount = entryVal - slVal
                val rewardAmount = tpVal - entryVal
                val riskPct = if (entryVal != 0.0) (riskAmount / entryVal) * 100.0 else 0.0
                val rewardPct = if (entryVal != 0.0) (rewardAmount / entryVal) * 100.0 else 0.0
                val rrRatio = if (riskAmount != 0.0) rewardAmount / riskAmount else 0.0
                val isRrValid = rrRatio >= 2.0

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Calcula el ratio de tu operativa antes de entrar al mercado para verificar la disciplina matemática de los traders de élite.",
                        fontSize = 11.sp,
                        color = GrayText,
                        lineHeight = 15.sp
                    )

                    // 1. Inputs row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = entryText,
                            onValueChange = { entryText = it },
                            label = { Text("Entrada", fontSize = 9.sp, color = GrayText) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = BorderBlue,
                                focusedContainerColor = MidnightNavy,
                                unfocusedContainerColor = MidnightNavy
                            )
                        )
                        OutlinedTextField(
                            value = slText,
                            onValueChange = { slText = it },
                            label = { Text("Stop Loss", fontSize = 9.sp, color = GrayText) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = BorderBlue,
                                focusedContainerColor = MidnightNavy,
                                unfocusedContainerColor = MidnightNavy
                            )
                        )
                        OutlinedTextField(
                            value = tpText,
                            onValueChange = { tpText = it },
                            label = { Text("Take Profit", fontSize = 9.sp, color = GrayText) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = BorderBlue,
                                focusedContainerColor = MidnightNavy,
                                unfocusedContainerColor = MidnightNavy
                            )
                        )
                    }

                    // 2. Calculated analytics cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MidnightNavy),
                            border = BorderStroke(1.dp, BorderBlue)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Riesgo Asumido", fontSize = 10.sp, color = GrayText)
                                Text(
                                    text = String.format(Locale.ROOT, "%.2f%%", riskPct),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CoralRed
                                )
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MidnightNavy),
                            border = BorderStroke(1.dp, BorderBlue)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Retorno Estimado", fontSize = 10.sp, color = GrayText)
                                Text(
                                    text = String.format(Locale.ROOT, "%.2f%%", rewardPct),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen
                                )
                            }
                        }
                    }

                    // 3. Pablo Gil's evaluation card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MidnightNavy),
                        border = BorderStroke(1.2.dp, if (isRrValid) EmeraldGreen else AmberGold)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isRrValid) Icons.Default.DoneAll else Icons.Default.Warning,
                                    contentDescription = "Evaluacion Gil",
                                    tint = if (isRrValid) EmeraldGreen else AmberGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = String.format(Locale.ROOT, "Ratio Beneficio/Riesgo = 1 : %.2f", rrRatio),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRrValid) EmeraldGreen else AmberGold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isRrValid) "✅ Cumple el estándar de Pablo Gil: El ratio de la operación es óptimo (mayor de 1:2). Tienes las matemáticas de tu parte."
                                       else "⚠️ Alerta de Disciplina Gil: El ratio R/R es inferior a 1:2. Estás asumiendo demasiada pérdida en comparación con la recompensa.",
                                fontSize = 11.sp,
                                color = LightText,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // 4. Lasvignes' trade plan
                    val tp1Partial = entryVal + riskAmount
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MidnightNavy),
                        border = BorderStroke(1.dp, BorderBlue)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "🛡️ Plan 'Compra a Cero' de Carlos Lasvignes",
                                fontSize = 11.5.sp,
                                color = ElectricBlue,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1. Objetivo Parcial (TP1): Al llegar el precio a ${String.format(Locale.ROOT, "%.2f", tp1Partial)}, liquida automáticamente el 50% de tu posición.\n" +
                                       "2. Ajuste de Protección: En ese preciso instante, sube el Stop Loss de las acciones restantes a tu precio de entrada inicial (${String.format(Locale.ROOT, "%.2f", entryVal)}).\n" +
                                       "3. Resultado: Tu riesgo financiero se reduce automáticamente a cero. ¡Operas con tranquilidad institucional!",
                                fontSize = 11.sp,
                                color = LightText,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- GAUGE NEEDLE DIAL COMPOSABLE ---
@Composable
fun ConsensusDialGauge(verdict: String, modifier: Modifier = Modifier) {
    val targetAngle = when (verdict) {
        "VENDER" -> 30f // Left slice
        "ESPERAR" -> 90f // Center slice
        "COMPRAR" -> 150f // Right slice
        else -> 90f
    }
    
    val animatedAngle by animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = spring(
            dampingRatio = 0.55f, 
            stiffness = Spring.StiffnessLow
        ),
        label = "NeedleAngle"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 240.dp, height = 130.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val center = Offset(width / 2f, height - 10f)
                val radius = width / 2.3f
                
                val strokeWidth = 16.dp.toPx()
                
                // Red segment (Vender): 180 to 240
                drawArc(
                    color = CoralRed.copy(alpha = 0.2f),
                    startAngle = 180f,
                    sweepAngle = 60f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(radius * 2f, radius * 2f),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )
                // Yellow segment (Esperar): 240 to 300
                drawArc(
                    color = AmberGold.copy(alpha = 0.2f),
                    startAngle = 240f,
                    sweepAngle = 60f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth),
                    size = Size(radius * 2f, radius * 2f),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )
                // Green segment (Comprar): 300 to 360
                drawArc(
                    color = EmeraldGreen.copy(alpha = 0.2f),
                    startAngle = 300f,
                    sweepAngle = 60f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(radius * 2f, radius * 2f),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )
                
                // Active highlight zone
                when (verdict) {
                    "VENDER" -> {
                        drawArc(
                            color = CoralRed,
                            startAngle = 180f,
                            sweepAngle = 60f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth + 4.dp.toPx(), cap = StrokeCap.Round),
                            size = Size(radius * 2f, radius * 2f),
                            topLeft = Offset(center.x - radius, center.y - radius)
                        )
                    }
                    "ESPERAR" -> {
                        drawArc(
                            color = AmberGold,
                            startAngle = 240f,
                            sweepAngle = 60f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth + 4.dp.toPx()),
                            size = Size(radius * 2f, radius * 2f),
                            topLeft = Offset(center.x - radius, center.y - radius)
                        )
                    }
                    "COMPRAR" -> {
                        drawArc(
                            color = EmeraldGreen,
                            startAngle = 300f,
                            sweepAngle = 60f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth + 4.dp.toPx(), cap = StrokeCap.Round),
                            size = Size(radius * 2f, radius * 2f),
                            topLeft = Offset(center.x - radius, center.y - radius)
                        )
                    }
                }

                val absoluteAngle = 180f + animatedAngle
                val rad = Math.toRadians(absoluteAngle.toDouble())
                val needleLength = radius * 0.88f
                
                val needleEnd = Offset(
                    (center.x + needleLength * Math.cos(rad)).toFloat(),
                    (center.y + needleLength * Math.sin(rad)).toFloat()
                )
                
                // Shadow for premium look
                drawLine(
                    color = Color.Black.copy(alpha = 0.6f),
                    start = center + Offset(3f, 3f),
                    end = needleEnd + Offset(3f, 3f),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // High-contrast, glowing ElectricBlue needle
                drawLine(
                    color = ElectricBlue,
                    start = center,
                    end = needleEnd,
                    strokeWidth = 3.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                
                // Needle center cap
                drawCircle(
                    color = MidnightNavy,
                    radius = 10.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = ElectricBlue,
                    radius = 6.dp.toPx(),
                    center = center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Horizontal descriptive tags below gauge
        Row(
            modifier = Modifier.width(240.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("VENDER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CoralRed)
            Text("ESPERAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AmberGold)
            Text("COMPRAR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
        }
    }
}

// --- MULTI HORIZON SUITABILITY COMPOSABLE ---
@Composable
fun MultiHorizonSuitabilityGrid(quote: StockRepository.QuoteDataPoint) {
    val absChange = Math.abs(quote.changePercent)
    val rsi = calculateRsi5(quote.pricesHistory)
    val avgPrice = if (quote.pricesHistory.isNotEmpty()) quote.pricesHistory.average() else quote.price
    val isAboveAverage = quote.price >= avgPrice

    // 1. Scalping (Minutes to hours)
    val scalpLevel = when {
        absChange >= 1.4 -> "ÓPTIMO (Alta Volatilidad)"
        absChange >= 0.6 -> "APTO (Fluctuación Media)"
        else -> "BAJO (Poca Actividad)"
    }
    val scalpColor = when {
        absChange >= 1.4 -> EmeraldGreen
        absChange >= 0.6 -> ElectricBlue
        else -> GrayText
    }

    // 2. Corto Plazo (Days/weeks)
    val shortTermLevel = when {
        rsi in 32.0..68.0 -> "FAVORABLE (Rango Dinámico)"
        rsi < 32.0 -> "ACUMULACIÓN (Soporte Técnico)"
        else -> "SOBRECOMPRA (Alto Riesgo)"
    }
    val shortTermColor = when {
        rsi in 32.0..68.0 -> EmeraldGreen
        rsi < 32.0 -> AmberGold
        else -> CoralRed
    }

    // 3. Medio Plazo (Months)
    val mediumTermLevel = if (isAboveAverage) "TENDENCIA SANAL (Alcista)" else "TENDENCIA BAJISTA (Precaución)"
    val mediumTermColor = if (isAboveAverage) EmeraldGreen else CoralRed

    // 4. Largo Plazo (Years)
    val longTermLevel = when {
        absChange < 1.8 && isAboveAverage -> "SÓLIDO (Crecimiento Sano)"
        isAboveAverage -> "ALTO CRECIMIENTO"
        else -> "CONSOLIDANDO SOPORTE"
    }
    val longTermColor = if (isAboveAverage) EmeraldGreen else AmberGold

    val horizons = listOf(
        HorizonData("⚡ Scalping (1-15 min)", scalpLevel, scalpColor, Icons.Default.FlashOn),
        HorizonData("🎯 Corto Plazo (Días/Semanas)", shortTermLevel, shortTermColor, Icons.Default.SwapVert),
        HorizonData("📈 Medio Plazo (Meses)", mediumTermLevel, mediumTermColor, Icons.Default.TrendingUp),
        HorizonData("💎 Largo Plazo (Años)", longTermLevel, longTermColor, Icons.Default.Star)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MidnightNavy, RoundedCornerShape(10.dp))
            .border(1.dp, BorderBlue, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "Idoneidad por Horizontes Temporales",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = LightText,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            horizons.forEach { horizon ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = horizon.icon,
                            contentDescription = horizon.title,
                            tint = ElectricBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = horizon.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = LightText
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(horizon.color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(1.dp, horizon.color, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = horizon.status,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = horizon.color
                        )
                    }
                }
            }
        }
    }
}

data class HorizonData(
    val title: String,
    val status: String,
    val color: Color,
    val icon: ImageVector
)

// Simple RSI estimation for 5-day prices
fun calculateRsi5(prices: List<Double>): Double {
    if (prices.size < 2) return 50.0
    var gains = 0.0
    var losses = 0.0
    for (i in 1 until prices.size) {
        val diff = prices[i] - prices[i - 1]
        if (diff > 0) gains += diff else losses -= diff
    }
    if (losses == 0.0) return 100.0
    val rs = gains / losses
    return 100.0 - (100.0 / (1.0 + rs))
}

// --- NEW BACKTESTING SCREEN COMPOSABLE ---
@Composable
fun BacktestingScreen(viewModel: StockAgentViewModel) {
    val result by viewModel.backtestResult.collectAsStateWithLifecycle()
    val isRunning by viewModel.isBacktestingRunning.collectAsStateWithLifecycle()
    val error by viewModel.backtestError.collectAsStateWithLifecycle()
    val aiReview by viewModel.backtestAiReview.collectAsStateWithLifecycle()
    val isAiRunning by viewModel.isBacktestAiRunning.collectAsStateWithLifecycle()
    val activeQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var tickerInput by remember { mutableStateOf(activeQuery) }
    var strategySelected by remember { mutableStateOf("MASTER_PROMPT") }
    var timeframeSelected by remember { mutableStateOf("3mo") }
    var initialCapitalInput by remember { mutableStateOf("10000") }

    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val strategies = listOf(
        Triple("MASTER_PROMPT", "Consenso Mesa AI (Comité Integrado 🤖)", "Señales inteligentes unificadas bajo filtros macroestructurales, momentum, pullback y gestión dinámica del capital."),
        Triple("CAVA", "José Luis Cava (Súper Confluencia 📈)", "Requiere alineación estricta de filtros: cruce alcista de MACD, tendencia potente (ADX) y volumen clímax."),
        Triple("ITURRALDE", "Alberto Iturralde (DAX Falsas Rupturas 🕵️)", "Busca trampas de mercado bajistas en soportes clave y barrido de stops para incorporarse con velocidad."),
        Triple("SAEZ", "Antonio Sáez del Castillo (Ondas de Elliott 🌊)", "Detecta la culminación de ondas correctivas bajistas de Gesmovasa para subirse al impulso ascendente."),
        Triple("ORTEGA", "Alexis Ortega (Timing y Media Semanal ⏳)", "Seguimiento institucional de tendencia a medio plazo analizando el cruce y soporte de una Media Móvil Exponencial (EMA)."),
        Triple("GIL", "Pablo Gil (Retroceso Fibonacci y Rígido 1:2 🎯)", "Compra rebotes en zonas Fibonacci áureas de retroceso (38.2%/61.8%) y gestiona con un ratio riesgo/recompensa fijo de 1:2."),
        Triple("LASVIGNES", "Carlos Lasvignes (Compra a Cero & TP/SL Inviolable 🛡️)", "Acceso en ruptura de rango, stop loss inmediato ultracercano al -1.5% y objetivo parcial de beneficio rápido del +3.0%.")
    )

    val timeframes = listOf(
        Pair("1mo", "1 Mes"),
        Pair("3mo", "3 Meses"),
        Pair("6mo", "6 Meses"),
        Pair("1y", "1 Año")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            border = BorderStroke(1.dp, BorderBlue)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Configuración del Backtesting Histórico",
                    color = LightText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                // Ticker Input
                OutlinedTextField(
                    value = tickerInput,
                    onValueChange = { tickerInput = it.uppercase() },
                    label = { Text("Ticker del Activo (ej: SAN.MC, AAPL)", color = GrayText) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = LightText,
                        unfocusedTextColor = LightText,
                        focusedContainerColor = MidnightNavy,
                        unfocusedContainerColor = MidnightNavy,
                        focusedLabelColor = ElectricBlue,
                        unfocusedLabelColor = GrayText,
                        focusedIndicatorColor = ElectricBlue,
                        unfocusedIndicatorColor = BorderBlue
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Strategy selector
                Text(
                    text = "Estrategia Técnica de Simulación",
                    color = LightText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    strategies.forEach { strategy ->
                        val isSelected = strategySelected == strategy.first
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) BorderBlue.copy(alpha = 0.25f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) ElectricBlue else BorderBlue,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { strategySelected = strategy.first }
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { strategySelected = strategy.first },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = ElectricBlue,
                                            unselectedColor = GrayText
                                        )
                                    )
                                    Text(
                                        text = strategy.second,
                                        color = if (isSelected) LightText else GrayText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = strategy.third,
                                    color = GrayText,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 36.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Timeframe Selector Row
                Text(
                    text = "Rango de Datos Históricos (Yahoo Finance Real)",
                    color = LightText,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timeframes.forEach { tf ->
                        val isSelected = timeframeSelected == tf.first
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) ElectricBlue.copy(alpha = 0.15f) else MidnightNavy,
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) ElectricBlue else BorderBlue,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { timeframeSelected = tf.first }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tf.second,
                                color = if (isSelected) ElectricBlue else LightText,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Initial Capital
                OutlinedTextField(
                    value = initialCapitalInput,
                    onValueChange = { initialCapitalInput = it.filter { char -> char.isDigit() } },
                    label = { Text("Capital de Entrada Virtual (EUR)", color = GrayText) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = LightText,
                        unfocusedTextColor = LightText,
                        focusedContainerColor = MidnightNavy,
                        unfocusedContainerColor = MidnightNavy,
                        focusedLabelColor = ElectricBlue,
                        unfocusedLabelColor = GrayText,
                        focusedIndicatorColor = ElectricBlue,
                        unfocusedIndicatorColor = BorderBlue
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Action button
                Button(
                    onClick = {
                        val cap = initialCapitalInput.toDoubleOrNull() ?: 10000.0
                        viewModel.executeBacktest(
                            ticker = tickerInput,
                            strategyId = strategySelected,
                            timeframe = timeframeSelected,
                            initialCapital = cap
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("run_backtest_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricBlue,
                        contentColor = MidnightNavy
                    ),
                    enabled = !isRunning && tickerInput.isNotBlank(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            color = MidnightNavy,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Simular",
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Ejecutar Simulación Avanzada",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Active Loaders
        if (isRunning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = ElectricBlue)
                    Text(
                        text = "Conectando con Yahoo Finance, recuperando datos históricos reales de cierre y ejecutando simulación...",
                        color = LightText,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Error panel
        error?.let { err ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CoralRed.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, CoralRed)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = CoralRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = err,
                        color = CoralRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // RESULTS PANEL!!
        result?.let { r ->
            // Quality marker info
            val isReal = r.isUsingRealData
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                border = BorderStroke(1.dp, if (isReal) EmeraldGreen else AmberGold)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isReal) EmeraldGreen.copy(alpha = 0.08f) else AmberGold.copy(alpha = 0.08f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isReal) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = "Estado de datos",
                            tint = if (isReal) EmeraldGreen else AmberGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isReal) "DATOS HISTÓRICOS REALES (Yahoo Finance)" else "DATOS SIMULADOS DE CONTINGENCIA",
                            color = if (isReal) EmeraldGreen else AmberGold,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Dial Gauge for Strategy Efficiency (Win Rate!)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Eficiencia de Estrategia (Win Rate %)",
                        color = LightText,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    BacktestWinRateGauge(winRate = r.winRatePct)
                }
            }

            // Stats grid cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val statList = listOf(
                    QuadStat("Rendimiento Simulado", String.format(java.util.Locale.US, "%.2f", r.totalReturnPct) + "%", if (r.totalReturnPct >= 0) EmeraldGreen else CoralRed, "Enfoque de la estrategia"),
                    QuadStat("Benchmark Buy & Hold", String.format(java.util.Locale.US, "%.2f", r.buyAndHoldReturnPct) + "%", if (r.buyAndHoldReturnPct >= 0) EmeraldGreen else CoralRed, "Comprar y Mantener"),
                    QuadStat("Capital de Cierre", String.format(java.util.Locale.US, "%.2f", r.finalCapital) + " €", LightText, "Capital inicial: ${String.format(java.util.Locale.US, "%.2f", r.initialCapital)} €"),
                    QuadStat("Factor de Ganancia", String.format(java.util.Locale.US, "%.2f", r.profitFactor), if (r.profitFactor >= 1.0) EmeraldGreen else CoralRed, "Rentabilidad vs pérdidas"),
                    QuadStat("Max Drawdown de Cuenta", String.format(java.util.Locale.US, "%.2f", r.maxDrawdownPct) + "%", if (r.maxDrawdownPct <= 15.0) EmeraldGreen else if (r.maxDrawdownPct <= 25.0) AmberGold else CoralRed, "Mayor caída desde pico"),
                    QuadStat("Operaciones Ejecutadas", "${r.totalTrades}", ElectricBlue, "Tasa acierto: ${String.format(java.util.Locale.US, "%.1f", r.winRatePct)}%")
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox(statList[0], modifier = Modifier.weight(1f))
                    StatBox(statList[1], modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox(statList[2], modifier = Modifier.weight(1f))
                    StatBox(statList[3], modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox(statList[4], modifier = Modifier.weight(1f))
                    StatBox(statList[5], modifier = Modifier.weight(1f))
                }
            }

            // AI Auditor Report Box!
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MidnightNavy),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "AI Review",
                                tint = ElectricBlue,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Auditoría Cuantitativa de la IA",
                                color = LightText,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isAiRunning) {
                            CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                        } else if (aiReview != null) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(aiReview ?: ""))
                                        Toast.makeText(context, "📋 Auditoría de backtesting copiada", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copiar", tint = ElectricBlue, modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "Auditoría de Backtesting: ${r.ticker}")
                                            putExtra(Intent.EXTRA_TEXT, aiReview)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Compartir Auditoría..."))
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = "Compartir", tint = ElectricBlue, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    Divider(color = BorderBlue, modifier = Modifier.padding(vertical = 12.dp))

                    if (isAiRunning) {
                        Text(
                            text = "La Mesa de Asesores de Bolsa AI está redactando un informe estratégico completo del backtesting...",
                            color = GrayText,
                            fontSize = 11.5.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    } else {
                        Text(
                            text = aiReview ?: "Revisión no disponible.",
                            color = LightText,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Expandable Trades list
            var tradesVisible by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                border = BorderStroke(1.dp, BorderBlue)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tradesVisible = !tradesVisible },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Historial",
                                tint = ElectricBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Registro de Transacciones Históricas (${r.trades.size})",
                                color = LightText,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Icon(
                            imageVector = if (tradesVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expandir",
                            tint = ElectricBlue
                        )
                    }

                    if (tradesVisible) {
                        Divider(color = BorderBlue, modifier = Modifier.padding(vertical = 10.dp))
                        
                        if (r.trades.isEmpty()) {
                            Text(
                                "No se realizaron operaciones bajo las condiciones especificadas de esta estrategia y temporalidad.",
                                fontSize = 11.sp,
                                color = GrayText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                r.trades.forEachIndexed { index, trade ->
                                    val isProfit = trade.isProfit
                                    val badgeBg = if (isProfit) EmeraldGreen.copy(alpha = 0.1f) else CoralRed.copy(alpha = 0.1f)
                                    val badgeBorder = if (isProfit) EmeraldGreen else CoralRed
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MidnightNavy, RoundedCornerShape(6.dp))
                                            .border(1.dp, BorderBlue, RoundedCornerShape(6.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Operación #${index + 1}",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = LightText
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .background(badgeBg, RoundedCornerShape(4.dp))
                                                        .border(1.dp, badgeBorder, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (trade.progressPct >= 0) "+${String.format(java.util.Locale.US, "%.2f", trade.progressPct)}%" else "${String.format(java.util.Locale.US, "%.2f", trade.progressPct)}%",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = badgeBorder
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text("COMPRA: ${trade.dateEntry}", fontSize = 10.sp, color = GrayText)
                                                    Text("Precio: ${String.format(java.util.Locale.US, "%.2f", trade.priceEntry)} €", fontSize = 10.5.sp, color = LightText, fontWeight = FontWeight.SemiBold)
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("VENTA: ${trade.dateExit ?: "Fin Periodo"}", fontSize = 10.sp, color = GrayText)
                                                    Text("Precio: ${String.format(java.util.Locale.US, "%.2f", trade.priceExit ?: 0.0)} €", fontSize = 10.5.sp, color = LightText, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                            Text(
                                                text = "Duración aproximada: ${trade.durationDays} sesiones",
                                                fontSize = 9.sp,
                                                color = GrayText
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
    }
}

// --- WIN RATE GAUGE WITH ROTATING NEEDLE ---
@Composable
fun BacktestWinRateGauge(winRate: Double, modifier: Modifier = Modifier) {
    // Leftmost is 0f (0%), center is 90f (50%), rightmost is 180f (100%)
    val targetAngle = (winRate / 100f) * 180f
    
    val animatedAngle by animateFloatAsState(
        targetValue = targetAngle.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessLow
        ),
        label = "WinRateNeedle"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(height = 120.dp, width = 220.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val center = Offset(width / 2f, height - 10f)
                val radius = width / 2.3f
                val strokeWidth = 16.dp.toPx()

                // Draw standard gauge arc zones
                // Draw 0-40% zone: Poor efficiency (Red)
                drawArc(
                    color = CoralRed.copy(alpha = 0.2f),
                    startAngle = 180f,
                    sweepAngle = 72f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(radius * 2f, radius * 2f),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )

                // Draw 40-65% zone: Normal efficiency (Amber)
                drawArc(
                    color = AmberGold.copy(alpha = 0.2f),
                    startAngle = 252f,
                    sweepAngle = 45f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth),
                    size = Size(radius * 2f, radius * 2f),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )

                // Draw 65-100% zone: Strong efficiency (Green)
                drawArc(
                    color = EmeraldGreen.copy(alpha = 0.2f),
                    startAngle = 297f,
                    sweepAngle = 63f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(radius * 2f, radius * 2f),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )

                // Highlight active sector based on winRate
                when {
                    winRate < 40.0 -> {
                        drawArc(
                            color = CoralRed,
                            startAngle = 180f,
                            sweepAngle = 72f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth + 3.dp.toPx(), cap = StrokeCap.Round),
                            size = Size(radius * 2f, radius * 2f),
                            topLeft = Offset(center.x - radius, center.y - radius)
                        )
                    }
                    winRate < 65.0 -> {
                        drawArc(
                            color = AmberGold,
                            startAngle = 252f,
                            sweepAngle = 45f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth + 3.dp.toPx()),
                            size = Size(radius * 2f, radius * 2f),
                            topLeft = Offset(center.x - radius, center.y - radius)
                        )
                    }
                    else -> {
                        drawArc(
                            color = EmeraldGreen,
                            startAngle = 297f,
                            sweepAngle = 63f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth + 3.dp.toPx(), cap = StrokeCap.Round),
                            size = Size(radius * 2f, radius * 2f),
                            topLeft = Offset(center.x - radius, center.y - radius)
                        )
                    }
                }

                // Make rotating needle
                val absoluteAngle = 180f + animatedAngle
                val rad = Math.toRadians(absoluteAngle.toDouble())
                val needleLen = radius * 0.9f
                
                val needleEnd = Offset(
                    (center.x + needleLen * Math.cos(rad)).toFloat(),
                    (center.y + needleLen * Math.sin(rad)).toFloat()
                )

                // Shadow
                drawLine(
                    color = Color.Black.copy(alpha = 0.5f),
                    start = center + Offset(2f, 2f),
                    end = needleEnd + Offset(2f, 2f),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Electric glow needle
                drawLine(
                    color = ElectricBlue,
                    start = center,
                    end = needleEnd,
                    strokeWidth = 2.8.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Center cap
                drawCircle(color = MidnightNavy, radius = 9.dp.toPx(), center = center)
                drawCircle(color = ElectricBlue, radius = 5.dp.toPx(), center = center)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Win Rate central label
        Text(
            text = "${String.format(java.util.Locale.US, "%.1f", winRate)}%",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = ElectricBlue
        )
        Text(
            text = if (winRate < 40f) "Acierto Bajo (Estrategia Débil)" else if (winRate < 60f) "Acierto Medio (Consolidación)" else "Acierto Fuerte (Estrategia de Precisión)",
            fontSize = 10.sp,
            color = GrayText,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun StatBox(data: QuadStat, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MidnightNavy, RoundedCornerShape(8.dp))
            .border(1.dp, BorderBlue, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(text = data.title, fontSize = 10.5.sp, color = GrayText, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = data.value, fontSize = 15.sp, color = data.color, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = data.hint, fontSize = 9.sp, color = GrayText)
        }
    }
}

data class QuadStat(
    val title: String,
    val value: String,
    val color: Color,
    val hint: String
)

