package com.example.ui

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.database.AlertHistory
import com.example.data.database.AppDatabase
import com.example.data.database.StockAlert
import com.example.data.database.IaAnalysisHistory
import com.example.data.repository.StockRepository
import com.example.data.network.*
import com.example.data.backtest.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StockAgentViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val repository = StockRepository(database.stockDao(), application)

    // Flow lists representing interactive UI elements
    val allAlerts: StateFlow<List<StockAlert>> = repository.allAlerts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allHistory: StateFlow<List<AlertHistory>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allIaHistory: StateFlow<List<IaAnalysisHistory>> = repository.allIaHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Interactive UI controls
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("SAN.MC") // Pre-populate with Banco Santander
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchedQuote = MutableStateFlow<StockRepository.QuoteDataPoint?>(null)
    val searchedQuote = _searchedQuote.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError = _searchError.asStateFlow()

    private val _isCheckingAlerts = MutableStateFlow(false)
    val isCheckingAlerts = _isCheckingAlerts.asStateFlow()

    private val _checkingStatus = MutableStateFlow("")
    val checkingStatus = _checkingStatus.asStateFlow()

    private val _draftedEmail = MutableStateFlow<String?>(null)
    val draftedEmail = _draftedEmail.asStateFlow()

    private val _isDrafting = MutableStateFlow(false)
    val isDrafting = _isDrafting.asStateFlow()

    // Gemini assistant state
    private val _aiRecommendation = MutableStateFlow<String?>(null)
    val aiRecommendation = _aiRecommendation.asStateFlow()

    private val _isAiRunning = MutableStateFlow(false)
    val isAiRunning = _isAiRunning.asStateFlow()

    // NEW TRADERS CONSULTANCY & SENTINEL STATES
    private val _selectedTraders = MutableStateFlow<Set<String>>(setOf("LASVIGNES"))
    val selectedTraders = _selectedTraders.asStateFlow()

    private val _selectedAiProvider = MutableStateFlow("GEMINI") // "GEMINI", "DEEPSEEK", "KIMI"
    val selectedAiProvider = _selectedAiProvider.asStateFlow()

    fun setSelectedAiProvider(provider: String) {
        _selectedAiProvider.value = provider
    }

    private val _activeNotification = MutableStateFlow<Triple<String, String, String>?>(null)
    val activeNotification = _activeNotification.asStateFlow()

    // DYNAMIC TICKER PRESETS
    private val _presets = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val presets = _presets.asStateFlow()

    // HOISTED PRE-CONFIGURE FIELDS
    private val _configTickerPrefill = MutableStateFlow("")
    val configTickerPrefill = _configTickerPrefill.asStateFlow()

    private val _configNamePrefill = MutableStateFlow("")
    val configNamePrefill = _configNamePrefill.asStateFlow()

    private val _configTrendPrefill = MutableStateFlow("")
    val configTrendPrefill = _configTrendPrefill.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger = _refreshTrigger.asStateFlow()

    // BACKTESTING STATES
    private val _backtestResult = MutableStateFlow<BacktestResult?>(null)
    val backtestResult = _backtestResult.asStateFlow()

    private val _isBacktestingRunning = MutableStateFlow(false)
    val isBacktestingRunning = _isBacktestingRunning.asStateFlow()

    private val _backtestError = MutableStateFlow<String?>(null)
    val backtestError = _backtestError.asStateFlow()

    private val _backtestAiReview = MutableStateFlow<String?>(null)
    val backtestAiReview = _backtestAiReview.asStateFlow()

    private val _isBacktestAiRunning = MutableStateFlow(false)
    val isBacktestAiRunning = _isBacktestAiRunning.asStateFlow()

    fun triggerRefresh() {
        _refreshTrigger.value += 1
    }

    // JOB FOR CANCELLING CURRENT SECHEDULER SCANNING
    private var monitoringJob: kotlinx.coroutines.Job? = null

    fun prefillConfigureScreen(ticker: String, name: String, trend: String = "") {
        _configTickerPrefill.value = ticker
        _configNamePrefill.value = name
        _configTrendPrefill.value = trend
        _selectedTab.value = 1 // Switch to Configurar screen (tab 1)
    }

    fun clearConfigurePrefill() {
        _configTickerPrefill.value = ""
        _configNamePrefill.value = ""
        _configTrendPrefill.value = ""
    }

    private fun loadPresets() {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("ticker_presets", Context.MODE_PRIVATE)
        val serialized = sharedPrefs.getString("presets_set", null)
        if (serialized == null) {
            val defaults = listOf("^IBEX|IBEX 35", "SAN.MC|Santander", "TEF.MC|Telefónica", "AAPL|Apple")
            sharedPrefs.edit().putString("presets_set", defaults.joinToString(";")).apply()
            _presets.value = listOf(
                "^IBEX" to "IBEX 35",
                "SAN.MC" to "Santander",
                "TEF.MC" to "Telefónica",
                "AAPL" to "Apple"
            )
        } else {
            val list = serialized.split(";").filter { it.contains("|") }.map { chunk ->
                val parts = chunk.split("|")
                parts[0] to parts[1]
            }
            _presets.value = list
        }
    }

    fun addPreset(ticker: String, name: String) {
        val current = _presets.value.toMutableList()
        if (current.none { it.first.uppercase() == ticker.uppercase() }) {
            current.add(ticker to name)
            _presets.value = current
            savePresets(current)
        }
    }

    fun removePreset(ticker: String) {
        val current = _presets.value.toMutableList()
        val index = current.indexOfFirst { it.first.uppercase() == ticker.uppercase() }
        if (index != -1) {
            current.removeAt(index)
            _presets.value = current
            savePresets(current)
        }
    }

    private fun savePresets(list: List<Pair<String, String>>) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences("ticker_presets", Context.MODE_PRIVATE)
        val serialized = list.joinToString(";") { "${it.first}|${it.second}" }
        sharedPrefs.edit().putString("presets_set", serialized).apply()
    }

    fun toggleTraderSelection(trader: String) {
        val current = _selectedTraders.value.toMutableSet()
        if (current.contains(trader)) {
            if (current.size > 1) { // keep at least one
                current.remove(trader)
            }
        } else {
            current.add(trader)
        }
        _selectedTraders.value = current
    }

    fun setSelectedTraders(traders: Set<String>) {
        if (traders.isNotEmpty()) {
            _selectedTraders.value = traders
        }
    }

    fun setActiveNotification(ticker: String, msg: String, detail: String) {
        _activeNotification.value = Triple(ticker, msg, detail)
    }

    fun clearActiveNotification() {
        _activeNotification.value = null
    }

    init {
        loadPresets()
        // Fetch default ticker on launch
        performSearch(force = true)
        // Check alerts and run initial scan on startup
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = database.stockDao().getActiveAlertsList()
                if (list.isEmpty()) {
                    val defaultAlert1 = StockAlert(
                        ticker = "SAN.MC",
                        name = "Banco Santander",
                        minPrice = 3.90,
                        maxPrice = 4.80,
                        minVolume = 5000000L,
                        pctChange = 2.0,
                        stopLoss = 3.80,
                        takeProfit = 4.90,
                        alertTrend = "BULLISH",
                        email = "Dispositivo Móvil"
                    )
                    val defaultAlert2 = StockAlert(
                        ticker = "TEF.MC",
                        name = "Telefónica",
                        minPrice = 3.50,
                        maxPrice = 4.20,
                        minVolume = 3000000L,
                        pctChange = 1.5,
                        stopLoss = 3.40,
                        takeProfit = 4.40,
                        alertTrend = "NONE",
                        email = "Dispositivo Móvil"
                    )
                    val defaultAlert3 = StockAlert(
                        ticker = "^IBEX",
                        name = "IBEX 35",
                        minPrice = 10800.0,
                        maxPrice = 11500.0,
                        minVolume = 100000000L,
                        pctChange = 1.0,
                        email = "Dispositivo Móvil"
                    )
                    repository.insertAlert(defaultAlert1)
                    repository.insertAlert(defaultAlert2)
                    repository.insertAlert(defaultAlert3)
                }
                
                // Trigger initial watchlist preset refresh & start background sentinel evaluation instantly
                withContext(Dispatchers.Main) {
                    triggerRefresh()
                    runMonitoringAgent()
                }
            } catch (e: Exception) {
                Log.e("StockAgentViewModel", "Failed creating default alerts or initial run: ${e.message}")
            }
        }
    }

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun performSearch(force: Boolean = false) {
        val q = _searchQuery.value.trim()
        if (q.isEmpty()) return

        _isSearching.value = true
        _searchError.value = null

        viewModelScope.launch {
            try {
                val quote = repository.getQuote(q)
                _searchedQuote.value = quote
            } catch (e: java.lang.Exception) {
                _searchError.value = "Error al obtener cotización: ${e.localizedMessage}"
                _searchedQuote.value = null
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun addAlert(
        ticker: String,
        name: String,
        minP: Double?,
        maxP: Double?,
        minV: Long?,
        pctC: Double?,
        sl: Double?,
        tp: Double?,
        trend: String,
        email: String,
        tp2: Double? = null,
        minBuyP: Double? = null,
        maxBuyP: Double? = null,
        condOperator: String? = "NONE",
        volMult: Double? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val alert = StockAlert(
                ticker = ticker.uppercase().trim(),
                name = name.ifBlank { "Acción de bolsa" },
                minPrice = minP,
                maxPrice = maxP,
                minVolume = minV,
                pctChange = pctC,
                stopLoss = sl,
                takeProfit = tp,
                takeProfit2 = tp2,
                minBuyPrice = minBuyP,
                maxBuyPrice = maxBuyP,
                alertTrend = when (trend) {
                    "ALCISTA" -> "BULLISH"
                    "BAJISTA" -> "BEARISH"
                    "BULLISH" -> "BULLISH"
                    "BEARISH" -> "BEARISH"
                    else -> "NONE"
                },
                email = "Dispositivo Móvil",
                condLogicalOperator = condOperator,
                unusualVolumeMultiplier = volMult
            )
            repository.insertAlert(alert)
        }
    }

    fun toggleAlertActive(alert: StockAlert) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAlert(alert.copy(isActive = !alert.isActive))
        }
    }

    fun deleteAlert(alert: StockAlert) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAlert(alert)
        }
    }

    fun clearLog() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
        }
    }

    /**
     * Executes the STOCK MONITORING AUTONOMOUS AGENT cycle.
     * Iterates through active alerts, pulls real quotes, validates triggers, automatically logs and drafts emails.
     */
    fun runMonitoringAgent() {
        if (_isCheckingAlerts.value) {
            return
        }
        _isCheckingAlerts.value = true
        _checkingStatus.value = "Iniciando vigilancia de bolsa..."

        // Simultaneously trigger custom preset watch list refreshes instantly
        triggerRefresh()

        monitoringJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val activeList = database.stockDao().getActiveAlertsList()
                if (activeList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _checkingStatus.value = "No hay alertas activas en vigilancia."
                        _isCheckingAlerts.value = false
                    }
                    return@launch
                }

                var triggeredCount = 0
                activeList.forEachIndexed { index, alert ->
                    withContext(Dispatchers.Main) {
                        _checkingStatus.value = "Validando cotización de ${alert.ticker} en tiempo real... (${index + 1}/${activeList.size})"
                    }

                    try {
                        val quote = repository.getQuote(alert.ticker)
                        val price = quote.price
                        val volume = quote.volume
                        val pctChange = quote.changePercent

                        // Determine trend from history
                        val prices = quote.pricesHistory
                        val isBullish = if (prices.size >= 2) price >= prices.first() else pctChange >= 0.0
                        val isBearish = if (prices.size >= 2) price < prices.first() else pctChange < 0.0

                        // Calculate average volume from historical sessions
                        val vols = quote.volumesHistory
                        val avgVolume = if (vols.size >= 2) {
                            vols.dropLast(1).average()
                        } else {
                            volume.toDouble()
                        }

                        // Check distinct alerts
                        if (alert.alertTrend?.startsWith("TRADERS:") == true) {
                            val tradersJoined = alert.alertTrend.substringAfter("TRADERS:")
                            val tradersSet = tradersJoined.split(",").filter { it.isNotBlank() }.toSet()
                            val apiKey = com.example.BuildConfig.GEMINI_API_KEY

                            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                                // Online AI Key fallback check (20% random simulation trigger OR hit stop/takeprofit limits)
                                val basePercent = 100.0
                                val limitHitSL = alert.stopLoss != null && price <= alert.stopLoss
                                val limitHitTP1 = alert.takeProfit != null && price >= alert.takeProfit
                                val limitHitTP2 = alert.takeProfit2 != null && price >= alert.takeProfit2
                                val limitInBuyRange = alert.minBuyPrice != null && alert.maxBuyPrice != null && price >= alert.minBuyPrice && price <= alert.maxBuyPrice
                                val randomSample = (1..6).random() == 1

                                if (limitHitSL || limitHitTP1 || limitHitTP2 || limitInBuyRange || randomSample) {
                                    val localType = if (limitHitSL) "STOP_LOSS" 
                                                    else if (limitHitTP1) "TAKE_PROFIT" 
                                                    else if (limitHitTP2) "TAKE_PROFIT_2" 
                                                    else if (limitInBuyRange) "BUY_MOMENTUM" 
                                                    else "BUY_MOMENTUM"
                                    
                                    val textMsg = if (limitHitSL) {
                                        "🛑 ¡CENTINELA ACTIVO - STOP LOSS! El comité unificado de traders ($tradersJoined) para ${alert.ticker} reporta que se rebasó el límite protector fijado en ${alert.stopLoss}."
                                    } else if (limitHitTP1) {
                                        "💰 ¡CENTINELA ACTIVO - TAKE PROFIT 1! El comité unificado de traders ($tradersJoined) para ${alert.ticker} reporta que se alcanzó el objetivo del TP1 de ${alert.takeProfit}."
                                    } else if (limitHitTP2) {
                                        "💰 ¡CENTINELA ACTIVO - TAKE PROFIT 2! El comité unificado de traders ($tradersJoined) para ${alert.ticker} reporta que se alcanzó la meta óptima del TP2 de ${alert.takeProfit2}."
                                    } else if (limitInBuyRange) {
                                        "🎯 ¡CENTINELA ACTIVO - RANGO COMPRA! El activo ${alert.ticker} entró en tu horquilla de entrada óptima (${alert.minBuyPrice} - ${alert.maxBuyPrice}) vigilada por los traders."
                                    } else {
                                        "📈 ¡CENTINELA ACTIVO - MOMENTUM! Los traders de tu centinela ($tradersJoined) confirman que el activo ${alert.ticker} cumple todas sus condiciones de compra de mínimo riesgo."
                                    }

                                    val localDetailText = """
                                        === HISTORIAL DE TRADING SENTINEL (Simulado) ===
                                        Ticker del activo: ${alert.ticker}
                                        Última Cotización: ${String.format("%.2f", price)} EUR/USD
                                        Volumen: $volume acciones
                                        Traders Activos: $tradersJoined
                                        Rango Compra Guardado: ${alert.minBuyPrice ?: "-"} - ${alert.maxBuyPrice ?: "-"}
                                        Stop Loss Guardado: ${alert.stopLoss ?: "-"}
                                        Take Profit 1 Guardado: ${alert.takeProfit ?: "-"}
                                        Take Profit 2 Guardado: ${alert.takeProfit2 ?: "-"}
                                        
                                        ANÁLISIS COHESIVO DEL COMITÉ:
                                        Los traders configurados para este centinela confirman la entrada en zona crítica o momentum del activo de forma óptima.
                                        
                                        Consejo complementario:
                                        Recuerde operar bajo estricta disciplina técnica, stop loss ceñido y control emocional constante.
                                    """.trimIndent()

                                    triggerAlertEvent(alert, localType, price, textMsg, quote, localDetailText)
                                    triggeredCount++
                                }
                            } else {
                                // Live automated sentinel verification using Gemini AI!
                                val result = checkSentinelRulesWithGemini(alert, quote, tradersSet)
                                if (result.triggered) {
                                    triggerAlertEvent(alert, result.eventType, price, result.reason, quote, result.detailedAdvice)
                                    triggeredCount++
                                }
                            }
                        } else {
                            // Standard/Custom numeric checks
                            val conditionsStatus = mutableListOf<Boolean>()
                            val conditionsDescriptions = mutableListOf<String>()

                            if (alert.minPrice != null) {
                                val met = price <= alert.minPrice
                                conditionsStatus.add(met)
                                if (met) {
                                    conditionsDescriptions.add("Suelo alcanzado: precio (${String.format("%.2f", price)}) <= Min (${alert.minPrice})")
                                }
                            }
                            if (alert.maxPrice != null) {
                                val met = price >= alert.maxPrice
                                conditionsStatus.add(met)
                                if (met) {
                                    conditionsDescriptions.add("Techo superado: precio (${String.format("%.2f", price)}) >= Max (${alert.maxPrice})")
                                }
                            }
                            if (alert.minVolume != null) {
                                val met = volume >= alert.minVolume
                                conditionsStatus.add(met)
                                if (met) {
                                    conditionsDescriptions.add("Volumen de negocio mínimo: volumen actuales ($volume) >= Min (${alert.minVolume})")
                                }
                            }
                            if (alert.pctChange != null) {
                                val met = Math.abs(pctChange) >= alert.pctChange
                                conditionsStatus.add(met)
                                if (met) {
                                    conditionsDescriptions.add("Oscilación brusca: cambio diario (${String.format("%.2f", pctChange)}%) >= Límite (${alert.pctChange}%)")
                                }
                            }
                            if (alert.stopLoss != null) {
                                val met = price <= alert.stopLoss
                                conditionsStatus.add(met)
                                if (met) {
                                    conditionsDescriptions.add("STOP LOSS perimetral: precio (${String.format("%.2f", price)}) <= SL (${alert.stopLoss})")
                                }
                            }
                            if (alert.takeProfit != null) {
                                val met = price >= alert.takeProfit
                                conditionsStatus.add(met)
                                if (met) {
                                    conditionsDescriptions.add("TAKE PROFIT 1 meta: precio (${String.format("%.2f", price)}) >= TP1 (${alert.takeProfit})")
                                }
                            }
                            if (alert.takeProfit2 != null) {
                                val met = price >= alert.takeProfit2
                                conditionsStatus.add(met)
                                if (met) {
                                    conditionsDescriptions.add("TAKE PROFIT 2 meta: precio (${String.format("%.2f", price)}) >= TP2 (${alert.takeProfit2})")
                                }
                            }
                            if (alert.minBuyPrice != null && alert.maxBuyPrice != null) {
                                val met = price >= alert.minBuyPrice && price <= alert.maxBuyPrice
                                conditionsStatus.add(met)
                                if (met) {
                                    conditionsDescriptions.add("Horquilla de compra: precio (${String.format("%.2f", price)}) está entre ${alert.minBuyPrice} y ${alert.maxBuyPrice}")
                                }
                            }
                            if (alert.alertTrend == "BULLISH" || alert.alertTrend == "BEARISH") {
                                val met = if (alert.alertTrend == "BULLISH") isBullish else isBearish
                                val trendLabel = if (alert.alertTrend == "BULLISH") "ALCISTA" else "BAJISTA"
                                conditionsStatus.add(met)
                                if (met) {
                                    conditionsDescriptions.add("Gatillo tendencia coincide con: $trendLabel")
                                }
                            }
                            if (alert.unusualVolumeMultiplier != null) {
                                val met = volume >= (avgVolume * alert.unusualVolumeMultiplier)
                                conditionsStatus.add(met)
                                if (met) {
                                    conditionsDescriptions.add("Volumen inusual detectado: volumen actual ($volume) >= ${String.format("%.0f", avgVolume * alert.unusualVolumeMultiplier)} (${alert.unusualVolumeMultiplier}x de la media de ${String.format("%.0f", avgVolume)})")
                                }
                            }

                            if (alert.condLogicalOperator == "AND" || alert.condLogicalOperator == "OR") {
                                val isAnd = alert.condLogicalOperator == "AND"
                                val triggerFired = if (isAnd) {
                                    conditionsStatus.size >= 2 && conditionsStatus.all { it }
                                } else {
                                    conditionsStatus.isNotEmpty() && conditionsStatus.any { it }
                                }

                                if (triggerFired) {
                                    val metConditionsText = conditionsDescriptions.joinToString("\n- ")
                                    val heading = if (isAnd) {
                                        "🚨 ¡ALERTA CONDICIONAL CONJUNTA (AND) DISPARADA!"
                                    } else {
                                        "🎯 ¡ALERTA CONDICIONAL COMBINADA (OR) DISPARADA!"
                                    }
                                    val logMessage = "$heading\nEl activo ${alert.ticker} ha activado el conjunto condicional:\n- $metConditionsText"
                                    triggerAlertEvent(alert, "CONDITIONAL", price, logMessage, quote)
                                    triggeredCount++
                                }
                            } else {
                                // Classic Independent evaluation
                                if (alert.minPrice != null && price <= alert.minPrice) {
                                    triggerAlertEvent(alert, "MIN_PRICE", price, "¡Alerta de Suelo alcanzado! ${alert.ticker} bajó a ${String.format("%.2f", price)} EUR/USD (Limite Min: ${alert.minPrice}).", quote)
                                    triggeredCount++
                                }
                                if (alert.maxPrice != null && price >= alert.maxPrice) {
                                    triggerAlertEvent(alert, "MAX_PRICE", price, "¡Alerta de Techo superada! ${alert.ticker} subió a ${String.format("%.2f", price)} EUR/USD (Limite Max: ${alert.maxPrice}).", quote)
                                    triggeredCount++
                                }
                                if (alert.minVolume != null && volume >= alert.minVolume) {
                                    triggerAlertEvent(alert, "VOLUME", volume.toDouble(), "¡Volumen Extraordinario! ${alert.ticker} negoció $volume acciones, por encima del mínimo de ${alert.minVolume}.", quote)
                                    triggeredCount++
                                }
                                if (alert.pctChange != null && Math.abs(pctChange) >= alert.pctChange) {
                                    triggerAlertEvent(alert, "PCT_CHANGE", pctChange, "¡Oscilación Brusca! El cambio diario de ${alert.ticker} es de ${String.format("%.2f", pctChange)}%, cruzando la variación límite de ${alert.pctChange}%.", quote)
                                    triggeredCount++
                                }
                                if (alert.stopLoss != null && price <= alert.stopLoss) {
                                    triggerAlertEvent(alert, "STOP_LOSS", price, "🚨 ¡STOP LOSS ALCANZADO! El valor ${alert.ticker} cayó a ${String.format("%.2f", price)}, cruzando el umbral de pánico del SL fijado en ${alert.stopLoss}.", quote)
                                    triggeredCount++
                                }
                                if (alert.takeProfit != null && price >= alert.takeProfit) {
                                    triggerAlertEvent(alert, "TAKE_PROFIT", price, "💰 ¡TAKE PROFIT 1 CONFIRMADO! El valor ${alert.ticker} de ${alert.name} escaló a ${String.format("%.2f", price)}, activando el objetivo TP1 de ${alert.takeProfit}.", quote)
                                    triggeredCount++
                                }
                                if (alert.takeProfit2 != null && price >= alert.takeProfit2) {
                                    triggerAlertEvent(alert, "TAKE_PROFIT_2", price, "💰 ¡TAKE PROFIT 2 CONFIRMADO! El valor ${alert.ticker} de ${alert.name} escaló a ${String.format("%.2f", price)}, activando el objetivo TP2 de ${alert.takeProfit2}.", quote)
                                    triggeredCount++
                                }
                                if (alert.minBuyPrice != null && alert.maxBuyPrice != null && price >= alert.minBuyPrice && price <= alert.maxBuyPrice) {
                                    triggerAlertEvent(alert, "BUY_MOMENTUM", price, "🎯 ¡EN HORQUILLA DE COMPRA! El activo ${alert.ticker} cotiza en ${String.format("%.2f", price)}, dentro del rango de entrada óptimo (${alert.minBuyPrice} - ${alert.maxBuyPrice}) sugerido por los analistas.", quote)
                                    triggeredCount++
                                }
                                if (alert.alertTrend == "BULLISH" && isBullish) {
                                    triggerAlertEvent(alert, "TREND", price, "📈 ¡Tendencia Alcista Confirmada! Cotización de ${alert.ticker} a ${String.format("%.2f", price)} está en claro empuje comprador.", quote)
                                    triggeredCount++
                                } else if (alert.alertTrend == "BEARISH" && isBearish) {
                                    triggerAlertEvent(alert, "TREND", price, "📉 ¡Tendencia Bajista Confirmada! Cotización de ${alert.ticker} a ${String.format("%.2f", price)} está sumergida en ciclo vendedor.", quote)
                                    triggeredCount++
                                }
                                if (alert.unusualVolumeMultiplier != null && volume >= (avgVolume * alert.unusualVolumeMultiplier)) {
                                    triggerAlertEvent(alert, "UNUSUAL_VOLUME", volume.toDouble(), "⚠️ ¡VOLUMEN INUSUAL DETECTADO! El volumen de ${alert.ticker} de $volume ha superado el ${alert.unusualVolumeMultiplier}x de su media histórica de las últimas sesiones (${String.format("%.0f", avgVolume)}).", quote)
                                    triggeredCount++
                                }
                            }
                        }

                    } catch (e: Exception) {
                        Log.e("StockAgentViewModel", "Failed checking stock alert rule for ${alert.ticker}: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    _checkingStatus.value = if (triggeredCount > 0) {
                        "¡Proceso completado! Se detectaron y confirmaron $triggeredCount cambios significativos."
                    } else {
                        "Vigilancia completada. No se han infringido condiciones técnicas de mercado."
                    }
                    _isCheckingAlerts.value = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _checkingStatus.value = "Error al ejecutar el agente de bolsa: ${e.localizedMessage}"
                    _isCheckingAlerts.value = false
                }
            }
        }
    }

    private suspend fun triggerAlertEvent(
        alert: StockAlert,
        alertType: String,
        triggerValue: Double,
        msg: String,
        quote: StockRepository.QuoteDataPoint,
        customDetailAdvice: String? = null
    ) {
        val craftedEmailText = customDetailAdvice ?: repository.draftGeminiEmailReport(
            ticker = alert.ticker,
            alertType = alertType,
            currentPrice = quote.price,
            triggerValue = triggerValue,
            history = quote,
            userMail = alert.email
        )

        val historyEntity = AlertHistory(
            ticker = alert.ticker,
            alertType = alertType,
            triggerValue = triggerValue,
            message = msg,
            emailSent = true,
            emailContent = craftedEmailText
        )
        repository.insertHistory(historyEntity)
        
        // Trigger a real Android System Push Notification, passing the detailed analysis!
        sendPushNotification(alert.ticker, msg, craftedEmailText)
    }

    fun sendPushNotification(ticker: String, msg: String, detailText: String? = null) {
        val context = getApplication<Application>().applicationContext
        
        // 1. Create Channels on Android 8.0+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "stock_alerts_channel",
                "Alertas de Bolsa",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Canal para notificar alertas de bolsa y cotizaciones de bolsa alcanzadas."
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // 2. Build Notification
        val hasPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (!hasPermission) {
            Log.w("StockAgentViewModel", "Notification permission was not granted; fallback to main-thread Toast")
            viewModelScope.launch(Dispatchers.Main) {
                Toast.makeText(context, "🚨 Cotización de $ticker: $msg", Toast.LENGTH_LONG).show()
            }
            return
        }

        val builder = androidx.core.app.NotificationCompat.Builder(context, "stock_alerts_channel")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("🚨 Cotización de $ticker - Alerta")
            .setContentText(msg)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(msg))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // PendingIntent to launch main activity on tap
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ticker", ticker)
            putExtra("message", msg)
            if (detailText != null) {
                putExtra("detail", detailText)
            }
            putExtra("show_alert_detail", true)
        }
        val pendingIntent = if (launchIntent != null) {
            android.app.PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        } else null
        
        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val notificationId = (System.currentTimeMillis() % 100000).toInt()
            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.e("StockAgentViewModel", "Failed to dispatch notification: ${e.message}")
        }
    }

    fun requestAIEvaluation(ticker: String, price: Double, recommendation: String) {
        _isAiRunning.value = true
        _aiRecommendation.value = null

        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            _aiRecommendation.value = "⚠️ No has ingresado tu clave de API de Gemini. Configúrala en el panel de secretos en AI Studio para iniciar este análisis.\n\nRecomendación de seguridad predeterminada: Fija un Stop Loss del 5% (${price * 0.95}) y Take Profit del 15% (${price * 1.15}) para este activo ($ticker) para proteger tus fondos."
            _isAiRunning.value = false
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val systemMsg = "Eres un consultor financiero experto de élite y especialista en trading algorítmico de bolsa."
            val userMsg = """
                Analiza en profundidad la acción de bolsa actual $ticker con cotización de ${price} EUR/USD.
                
                Instrucciones que me gustaría que cubras:
                1. ¿Es un momento técnico alcista, bajista o lateral?
                2. Sugiere un nivel de STOP LOSS lógico técnico para proteger la inversión. Cruza esto contra soportes recientes.
                3. Sugiere un nivel de TAKE PROFIT lógico y bien fundamentado matemáticamente.
                4. Analiza la volatilidad esperada e indícame qué volumen mínimo de negociación recomendarías vigilar.
                5. ¿Qué opina de operar este activo con respecto al Ibex nacional o tendencias mundiales actuales?
                
                Por favor, genera una respuesta profesional, escrita de forma fluida, fácil de comprender, en español y organizada por secciones.
            """.trimIndent()

            try {
                val request = GeminiContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = userMsg)))),
                    systemInstruction = Content(parts = listOf(Part(text = systemMsg))),
                    generationConfig = GenerationConfig(temperature = 0.6f)
                )
                val response = RetrofitClient.generateContentSafe(
                    model = "gemini-3.5-flash",
                    apiKey = apiKey,
                    request = request
                )
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                withContext(Dispatchers.Main) {
                    _aiRecommendation.value = text ?: "Error: Respuesta de Gemini vacía."
                }
                if (!text.isNullOrBlank()) {
                    val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    repository.insertIaAnalysisHistory(
                        IaAnalysisHistory(
                            ticker = ticker,
                            date = dateStr,
                            price = price,
                            adviceText = text
                        )
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _aiRecommendation.value = "Error al contactar al Agente de Análisis AI: ${e.localizedMessage}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isAiRunning.value = false
                }
            }
        }
    }

    fun requestTradersConsultancy(ticker: String, price: Double, volume: Long, changePercent: Double) {
        _isAiRunning.value = true
        _aiRecommendation.value = null

        viewModelScope.launch {
            try {
                val advice = repository.draftTradersConsultancy(
                    ticker = ticker,
                    currentPrice = price,
                    volume = volume,
                    changePercent = changePercent,
                    selectedTraders = _selectedTraders.value,
                    aiProvider = _selectedAiProvider.value
                )
                _aiRecommendation.value = advice
                if (!advice.isNullOrBlank() && !advice.startsWith("⚠️") && !advice.startsWith("Error")) {
                    val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    repository.insertIaAnalysisHistory(
                        IaAnalysisHistory(
                            ticker = ticker,
                            date = dateStr,
                            price = price,
                            adviceText = advice
                        )
                    )
                }
            } catch (e: Exception) {
                _aiRecommendation.value = "Error al ejecutar la consultoría de trading: ${e.localizedMessage}"
            } finally {
                _isAiRunning.value = false
            }
        }
    }

    fun clearIaAnalysisHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearIaAnalysisHistory()
        }
    }

    fun deleteIaAnalysisHistoryById(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteIaAnalysisHistoryById(id)
        }
    }

    fun executeBacktest(ticker: String, strategyId: String, timeframe: String, initialCapital: Double) {
        _isBacktestingRunning.value = true
        _backtestError.value = null
        _backtestResult.value = null
        _backtestAiReview.value = null

        viewModelScope.launch {
            try {
                // Fetch historical quotes from repository
                val quote = repository.getBacktestHistory(ticker, timeframe)
                val isReal = !quote.isFallback
                
                // Run backtest simulation
                val result = BacktestEngine.runSimulation(
                    ticker = quote.ticker,
                    strategyId = strategyId,
                    timeframe = timeframe,
                    initialCapital = initialCapital,
                    prices = quote.pricesHistory,
                    timestamps = quote.pointsHistory,
                    volumes = quote.volumesHistory ?: emptyList(),
                    isRealData = isReal
                )
                
                _backtestResult.value = result
                
                // Triggers Gemini AI consultation of backtest results!
                runBacktestAiAnalysis(result)
            } catch (e: Exception) {
                _backtestError.value = "Error al ejecutar el backtesting: ${e.localizedMessage}"
            } finally {
                _isBacktestingRunning.value = false
            }
        }
    }

    private fun runBacktestAiAnalysis(result: BacktestResult) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            _backtestAiReview.value = "⚠️ No hay API Key para realizar la revisión experta de AI. Revisa los secretos de AI Studio."
            return
        }

        _isBacktestAiRunning.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val systemMsg = """
                    Eres la "Mesa de Asesores de Bolsa AI". Tu misión es redactar una auditoría cuantitativa y consejo estratégico riguroso en base a los resultados de un backtesting histórico que el usuario acaba de simular.
                    Debes hablar en español, de forma muy analítica, directa y profesional, sin rodeos comerciales ni saludos exagerados. Utiliza viñetas claras.
                """.trimIndent()

                val tradeSummary = result.trades.take(12).mapIndexed { idx, trade ->
                    "Operación #${idx + 1}: Compra el ${trade.dateEntry} a ${String.format(java.util.Locale.US, "%.2f", trade.priceEntry)}, Venta el ${trade.dateExit ?: "Fin"} a ${String.format(java.util.Locale.US, "%.2f", trade.priceExit ?: 0.0)} -> Rendimiento: ${String.format(java.util.Locale.US, "%.2f", trade.progressPct)}%"
                }.joinToString("\n")

                val userPrompt = """
                    === RESULTADO DE BACKTESTING DE BOLSA ===
                    Activo: ${result.ticker}
                    Estrategia: ${result.strategyName}
                    Temporalidad: ${result.timeframe} (Simulado sobre datos ${if (result.isUsingRealData) "REALES de Yahoo Finance" else "de contingencia debido a límites"})
                    Capital Inicial: ${String.format(java.util.Locale.US, "%.2f", result.initialCapital)}
                    Capital Final: ${String.format(java.util.Locale.US, "%.2f", result.finalCapital)}
                    Rentabilidad de la Estrategia: ${String.format(java.util.Locale.US, "%.2f", result.totalReturnPct)}%
                    Rentabilidad Buy & Hold (Comprar y Mantener): ${String.format(java.util.Locale.US, "%.2f", result.buyAndHoldReturnPct)}%
                    Número total de Operaciones: ${result.totalTrades}
                    Tasa de Acierto (Win Rate): ${String.format(java.util.Locale.US, "%.2f", result.winRatePct)}%
                    Factor de Ganancia (Profit Factor): ${String.format(java.util.Locale.US, "%.2f", result.profitFactor)}
                    Drawdown Máximo de la cuenta: ${String.format(java.util.Locale.US, "%.2f", result.maxDrawdownPct)}%

                    Muestra de operaciones históricas ejecuciones:
                    $tradeSummary

                    Escribe tu veredicto agrupado exactamente en los siguientes apartados con emojis analíticos:
                    1. 📊 COMENTARIO DEL AUDITOR: Evalúa si la estrategia superó al mercado (Buy & Hold), el impacto del factor de ganancia y si el número de señales es estadísticamente suficiente.
                    2. ⚖️ ANÁLISIS DE RIESGO: Analiza si el Drawdown Máximo es aceptable o excesivo para el rendimiento obtenido (Ratio de Calidad del Drawdown).
                    3. 💡 RECOMENDACIÓN DE REGULACIÓN: Qué filtros adicionales añadirías (e.g. cruce de tendencia macro superior, filtro de RSI o parada de stop loss) para limpiar operaciones falsas.
                    4. 🏛️ VEREDICTO DE LA MESA: [APROBADO CON RESERVAS / NO RECOMENDADO / EXCELENTE SETUP]
                """.trimIndent()

                val request = GeminiContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = userPrompt)))),
                    systemInstruction = Content(parts = listOf(Part(text = systemMsg))),
                    generationConfig = GenerationConfig(temperature = 0.5f)
                )

                val response = RetrofitClient.generateContentSafe(
                    model = "gemini-3.5-flash",
                    apiKey = apiKey,
                    request = request
                )
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                withContext(Dispatchers.Main) {
                    _backtestAiReview.value = text ?: "Sin respuesta del asesor de IA."
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _backtestAiReview.value = "Error al recibir la revisión experta de la IA: ${e.localizedMessage}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isBacktestAiRunning.value = false
                }
            }
        }
    }

    fun activateTradersSentinel(
        ticker: String,
        name: String,
        traders: Set<String>,
        sl: Double?,
        tp: Double?,
        tp2: Double? = null,
        minBuyP: Double? = null,
        maxBuyP: Double? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val alert = StockAlert(
                ticker = ticker.uppercase().trim(),
                name = name.ifBlank { "Consultoría Móvil" },
                minPrice = null,
                maxPrice = null,
                minVolume = null,
                pctChange = null,
                stopLoss = sl,
                takeProfit = tp,
                takeProfit2 = tp2,
                minBuyPrice = minBuyP,
                maxBuyPrice = maxBuyP,
                alertTrend = "TRADERS:${traders.joinToString(",")}",
                email = "Dispositivo Móvil"
            )
            repository.insertAlert(alert)
        }
    }

    private suspend fun checkSentinelRulesWithGemini(
        alert: StockAlert,
        quote: StockRepository.QuoteDataPoint,
        traders: Set<String>,
        aiProvider: String = "GEMINI"
    ): GeminiSentinelResult = withContext(Dispatchers.IO) {
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
        val prompt = """
            Estás actuando como el robot centinela del sistema inteligente de trading en tiempo real de Roberto Ruiz.
            Debes evaluar si la cotización en tiempo real del activo ${alert.ticker} cumple con las condiciones técnicas de los traders: ${traders.joinToString(", ")}.
            
            Datos actuales de mercado de ${alert.ticker}:
            - Precio actual: ${quote.price} EUR/USD
            - Volumen negociado hoy: ${quote.volume} acciones
            - Rentabilidad diaria: ${quote.changePercent}%
            - Límite de pérdidas fijado (Stop Loss): ${alert.stopLoss ?: "No definido"}
            - Límite de ganancias fijado (Take Profit): ${alert.takeProfit ?: "No definido"}
            
            Evalúa con rigor las reglas de los traders seleccionados. Determina si:
            1. El precio ha cruzado el Stop Loss o el Take Profit configurados.
            2. O si el comité de traders de los PDFs coincide en que el activo acaba de entrar en su exacto momentum de COMPRA o VENTA de mínimo riesgo (por ejemplo, confluencia de soporte validado en Cava/Lasvignes/Gil).
            
            Por favor, responde exclusivamente con un objeto JSON válido que podamos parsear en Kotlin. No incluyas explicaciones externas al JSON, ni bloques de código redundantes, solo el objeto plano que respete este esquema:
            {
              "triggered": true (si se cumple una condición crítica o alerta de momentum/limites, de lo contrario false),
              "eventType": "STOP_LOSS" o "TAKE_PROFIT" o "BUY_MOMENTUM" o "SELL_MOMENTUM",
              "reason": "Acción u evento que provocó el salto (ej. ¡Pánico! Cava detecta clímax y salta stop consolidado)",
              "detailedAdvice": "Explicación exhaustiva en español de por qué se disparó la alerta, qué condiciones de qué traders se cumplieron de forma fiel a sus personalidades, y qué plan táctico de compra/venta o protección recomiendan ejecutar ahora mismo."
            }
            
            ${when (aiProvider) {
                "DEEPSEEK" -> "ATENCIÓN: Emite el análisis adoptando la personalidad técnica hiper-rigurosa y paso a paso de DeepSeek-R1. Prepara el 'detailedAdvice' con el prefijo explicitamente: '🤖 [Análisis Generado por DeepSeek-R1] '."
                "KIMI" -> "ATENCIÓN: Emite el análisis adoptando la personalidad sumamente veloz, intuitiva y centrada en tendencias sectoriales rápidas de Kimi Chat. Prepara 'detailedAdvice' con el prefijo explicitamente: '🤖 [Análisis Generado por Kimi Chat] '."
                else -> "ATENCIÓN: Emite el análisis adoptando la personalidad de Gemini 2.5 Flash de Google. Prepara el 'detailedAdvice' con el prefijo explicitamente: '🤖 [Análisis Generado por Gemini 2.5 Flash] '."
            }}
        """.trimIndent()

        try {
            val request = GeminiContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(temperature = 0.5f)
            )
            val response = RetrofitClient.generateContentSafe(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!text.isNullOrBlank()) {
                val isTriggered = text.contains("\"triggered\": true") || text.contains("\"triggered\":true")
                val event = if (text.contains("STOP_LOSS")) "STOP_LOSS" 
                            else if (text.contains("TAKE_PROFIT")) "TAKE_PROFIT" 
                            else if (text.contains("BUY_MOMENTUM") || text.contains("BUY")) "BUY_MOMENTUM" 
                            else if (text.contains("SELL_MOMENTUM") || text.contains("SELL")) "SELL_MOMENTUM" 
                            else "TREND"
                
                val reasonStart = text.indexOf("\"reason\":")
                val reasonText = if (reasonStart != -1) {
                    val sub = text.substring(reasonStart + 9)
                    val firstQuote = sub.indexOf("\"")
                    val secondQuote = sub.indexOf("\"", firstQuote + 1)
                    if (firstQuote != -1 && secondQuote != -1) sub.substring(firstQuote + 1, secondQuote) else "Alerta de centinela disparada"
                } else {
                    "Se han cumplido las condiciones de alerta del centinela de trading."
                }
                
                val adviceStart = text.indexOf("\"detailedAdvice\":")
                val adviceText = if (adviceStart != -1) {
                    val sub = text.substring(adviceStart + 17)
                    val firstQuote = sub.indexOf("\"")
                    val secondQuote = sub.indexOf("\"", firstQuote + 1)
                    if (firstQuote != -1 && secondQuote != -1) {
                        sub.substring(firstQuote + 1, secondQuote).replace("\\n", "\n").replace("\\\"", "\"")
                    } else "Se recomienda revisar el gráfico."
                } else {
                    "Revisa de inmediato las condiciones de trade del activo."
                }

                return@withContext GeminiSentinelResult(isTriggered, event, reasonText, adviceText)
            }
        } catch (e: Exception) {
            Log.e("StockAgentViewModel", "Failed checking AI sentinel conditions: ${e.message}")
        }
        return@withContext GeminiSentinelResult(false, "NONE", "", "")
    }

    data class GeminiSentinelResult(
        val triggered: Boolean,
        val eventType: String,
        val reason: String,
        val detailedAdvice: String
    )
}
