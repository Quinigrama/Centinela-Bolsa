package com.example.data.backtest

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Backtrade(
    val ticker: String,
    val indexEntry: Int,
    val indexExit: Int?,
    val dateEntry: String,
    val dateExit: String?,
    val priceEntry: Double,
    val priceExit: Double?,
    val isProfit: Boolean,
    val progressPct: Double,
    val durationDays: Int
)

data class BacktestResult(
    val ticker: String,
    val strategyName: String,
    val timeframe: String,
    val initialCapital: Double,
    val finalCapital: Double,
    val totalReturnPct: Double,
    val buyAndHoldReturnPct: Double,
    val profitFactor: Double,
    val maxDrawdownPct: Double,
    val totalTrades: Int,
    val winRatePct: Double,
    val trades: List<Backtrade>,
    val finalPrice: Double,
    val isUsingRealData: Boolean,
    val pointsHistory: List<Long>,
    val pricesHistory: List<Double>,
    val errorMsg: String? = null
)

object BacktestEngine {

    fun runSimulation(
        ticker: String,
        strategyId: String,
        timeframe: String,
        initialCapital: Double,
        prices: List<Double>,
        timestamps: List<Long>,
        volumes: List<Long>,
        isRealData: Boolean
    ): BacktestResult {
        if (prices.isEmpty()) {
            return BacktestResult(
                ticker = ticker,
                strategyName = getStrategyName(strategyId),
                timeframe = timeframe,
                initialCapital = initialCapital,
                finalCapital = initialCapital,
                totalReturnPct = 0.0,
                buyAndHoldReturnPct = 0.0,
                profitFactor = 0.0,
                maxDrawdownPct = 0.0,
                totalTrades = 0,
                winRatePct = 0.0,
                trades = emptyList(),
                finalPrice = 0.0,
                isUsingRealData = isRealData,
                pointsHistory = emptyList(),
                pricesHistory = emptyList(),
                errorMsg = "Historial de precios vacío. No se puede simular."
            )
        }

        var cash = initialCapital
        var positionUnits = 0.0
        var entryPrice = 0.0
        var entryIndex = 0
        val tradesList = mutableListOf<Backtrade>()

        var peakCapital = initialCapital
        var maxDrawdownVal = 0.0

        for (i in 0 until prices.size) {
            val currentPrice = prices[i]
            val currentDate = formatDate(timestamps.getOrNull(i) ?: (System.currentTimeMillis() / 1000))

            val holdsPosition = positionUnits > 0.0
            
            val isBuySignal = checkBuySignal(strategyId, prices, volumes, i, holdsPosition)
            val isSellSignal = checkSellSignal(strategyId, prices, volumes, i, holdsPosition, entryPrice)

            if (!holdsPosition && isBuySignal) {
                // Buy All-In
                positionUnits = cash / currentPrice
                entryPrice = currentPrice
                entryIndex = i
                cash = 0.0
            } else if (holdsPosition && isSellSignal) {
                // Sell All-In
                val exitPrice = currentPrice
                val profitPct = ((exitPrice - entryPrice) / entryPrice) * 100.0
                val isProfit = exitPrice > entryPrice
                val duration = i - entryIndex

                tradesList.add(
                    Backtrade(
                        ticker = ticker,
                        indexEntry = entryIndex,
                        indexExit = i,
                        dateEntry = formatDate(timestamps.getOrNull(entryIndex) ?: (System.currentTimeMillis() / 1000)),
                        dateExit = currentDate,
                        priceEntry = entryPrice,
                        priceExit = exitPrice,
                        isProfit = isProfit,
                        progressPct = profitPct,
                        durationDays = duration
                    )
                )
                cash = positionUnits * exitPrice
                positionUnits = 0.0
            }

            // Calculate Portfolio Value
            val curPortfolioValue = if (positionUnits > 0.0) positionUnits * currentPrice else cash
            if (curPortfolioValue > peakCapital) {
                peakCapital = curPortfolioValue
            }
            val curDrawdown = if (peakCapital > 0.0) ((peakCapital - curPortfolioValue) / peakCapital) * 100.0 else 0.0
            if (curDrawdown > maxDrawdownVal) {
                maxDrawdownVal = curDrawdown
            }
        }

        // Liquidate at the end if we still hold shares
        if (positionUnits > 0.0) {
            val finalPrice = prices.last()
            val profitPct = ((finalPrice - entryPrice) / entryPrice) * 100.0
            val isProfit = finalPrice > entryPrice
            val duration = (prices.size - 1) - entryIndex

            tradesList.add(
                Backtrade(
                    ticker = ticker,
                    indexEntry = entryIndex,
                    indexExit = prices.size - 1,
                    dateEntry = formatDate(timestamps.getOrNull(entryIndex) ?: (System.currentTimeMillis() / 1000)),
                    dateExit = formatDate(timestamps.lastOrNull() ?: (System.currentTimeMillis() / 1000)),
                    priceEntry = entryPrice,
                    priceExit = finalPrice,
                    isProfit = isProfit,
                    progressPct = profitPct,
                    durationDays = duration
                )
            )
            cash = positionUnits * finalPrice
            positionUnits = 0.0
        }

        // Statistics
        val finalCapital = cash
        val totalReturnPct = ((finalCapital - initialCapital) / initialCapital) * 100.0
        
        val firstPrice = prices.first()
        val lastPrice = prices.last()
        val buyAndHoldReturnPct = if (firstPrice > 0) ((lastPrice - firstPrice) / firstPrice) * 100.0 else 0.0

        val totalTrades = tradesList.size
        val profitableTrades = tradesList.filter { it.isProfit }
        val winRatePct = if (totalTrades > 0) (profitableTrades.size.toDouble() / totalTrades.toDouble()) * 100.0 else 0.0

        val grossProfit = tradesList.filter { it.progressPct > 0 }.sumOf { (it.priceExit!! - it.priceEntry) * (initialCapital / it.priceEntry) }
        val grossLoss = tradesList.filter { it.progressPct <= 0 }.sumOf { Math.abs(it.priceExit!! - it.priceEntry) * (initialCapital / it.priceEntry) }
        val profitFactor = if (grossLoss > 0.0) grossProfit / grossLoss else if (grossProfit > 0.0) 99.9 else 0.0

        return BacktestResult(
            ticker = ticker,
            strategyName = getStrategyName(strategyId),
            timeframe = timeframe,
            initialCapital = initialCapital,
            finalCapital = finalCapital,
            totalReturnPct = totalReturnPct,
            buyAndHoldReturnPct = buyAndHoldReturnPct,
            profitFactor = profitFactor,
            maxDrawdownPct = maxDrawdownVal,
            totalTrades = totalTrades,
            winRatePct = winRatePct,
            trades = tradesList,
            finalPrice = lastPrice,
            isUsingRealData = isRealData,
            pointsHistory = timestamps,
            pricesHistory = prices
        )
    }

    private fun calculateEma(prices: List<Double>, index: Int, period: Int): Double {
        if (prices.isEmpty() || index < 0) return 0.0
        val idx = minOf(index, prices.size - 1)
        if (idx < period - 1) return prices[idx]
        var ema = prices[0]
        val multiplier = 2.0 / (period + 1)
        for (i in 1..idx) {
            ema = (prices[i] - ema) * multiplier + ema
        }
        return ema
    }

    private fun calculateMacd(prices: List<Double>, index: Int): Pair<Double, Double> {
        if (index < 26) return Pair(0.0, 0.0)
        val ema12 = calculateEma(prices, index, 12)
        val ema26 = calculateEma(prices, index, 26)
        val macdLine = ema12 - ema26
        
        var sumMacd = 0.0
        val signalPeriod = 9
        val startIdx = index - signalPeriod + 1
        if (startIdx >= 0) {
            for (j in startIdx..index) {
                val e12 = calculateEma(prices, j, 12)
                val e26 = calculateEma(prices, j, 26)
                sumMacd += (e12 - e26)
            }
            val signalLine = sumMacd / signalPeriod
            return Pair(macdLine, signalLine)
        }
        return Pair(macdLine, 0.0)
    }

    private fun calculateAdxProxy(prices: List<Double>, index: Int, period: Int = 14): Double {
        if (index < period) return 15.0
        var upCount = 0
        for (i in (index - period + 1)..index) {
            if (prices[i] > prices[i-1]) upCount++
        }
        val ratio = upCount.toDouble() / period
        val trendStrength = Math.abs(ratio - 0.5) * 2.0 * 50.0
        return trendStrength + 10.0
    }

    private fun checkBuySignal(strategyId: String, prices: List<Double>, volumes: List<Long>, index: Int, holds: Boolean): Boolean {
        if (holds) return false // Cannot buy if we already have a position
        
        return when (strategyId) {
            "ITURRALDE" -> {
                if (index < 4) return false
                val currentPrice = prices[index]
                val prevPrices = prices.subList(index - 4, index)
                val lowestOfPrev = prevPrices.minOrNull() ?: currentPrice
                val currentVolume = volumes.getOrNull(index)?.toDouble() ?: 0.0
                val prevVolumeAvg = if (index >= 5) volumes.subList(index - 5, index).average() else 1.0
                
                val wasBelowOrNearLow = prices[index - 1] < lowestOfPrev * 1.01
                val recoveredToday = currentPrice > lowestOfPrev
                val highVolumeShakeout = currentVolume > prevVolumeAvg * 1.15
                wasBelowOrNearLow && recoveredToday && highVolumeShakeout
            }
            "CAVA" -> {
                if (index < 26) return false
                val (macdCurrent, signalCurrent) = calculateMacd(prices, index)
                val (macdPrev, signalPrev) = calculateMacd(prices, index - 1)
                val macdCrossUp = macdPrev <= signalPrev && macdCurrent > signalCurrent
                val adxStrength = calculateAdxProxy(prices, index, 14)
                val currentVolume = volumes.getOrNull(index)?.toDouble() ?: 0.0
                val prevVolumesAvg = if (index >= 5) volumes.subList(index - 5, index).average() else 1.0
                val volumeConfirmation = currentVolume >= prevVolumesAvg * 1.10
                
                macdCrossUp && adxStrength > 22.0 && volumeConfirmation
            }
            "SAEZ" -> {
                if (index < 6) return false
                val isCorrectiveDown = prices[index - 3] < prices[index - 4] && prices[index - 2] < prices[index - 3]
                val turnUp = prices[index] > prices[index - 1] && prices[index - 1] > prices[index - 2]
                isCorrectiveDown && turnUp
            }
            "ORTEGA" -> {
                if (index < 20) return false
                val currentPrice = prices[index]
                val prevPrice = prices[index - 1]
                val ema20Current = calculateEma(prices, index, 20)
                val ema20Prev = calculateEma(prices, index - 1, 20)
                prevPrice <= ema20Prev && currentPrice > ema20Current
            }
            "GIL" -> {
                if (index < 25) return false
                val subset = prices.subList(index - 25, index)
                val highest = subset.maxOrNull() ?: prices[index]
                val lowest = subset.minOrNull() ?: prices[index]
                val range = highest - lowest
                if (range <= 0.0) return false
                
                val fibo382 = highest - 0.382 * range
                val fibo618 = highest - 0.618 * range
                val currentPrice = prices[index]
                
                val inFibZone = currentPrice in fibo618..fibo382
                val bouncing = currentPrice > prices[index - 1]
                inFibZone && bouncing
            }
            "LASVIGNES" -> {
                if (index < 12) return false
                val subset = prices.subList(index - 12, index)
                val maxOfPrev = subset.maxOrNull() ?: prices[index]
                prices[index] > maxOfPrev
            }
            "MASTER_PROMPT" -> {
                if (index < 20) return false
                val rsi = calculateRsi(prices, index, 14)
                val ema = calculateEma(prices, index, 20)
                val smaFiltered = prices[index] > ema
                
                val pullback = index >= 4 && prices[index] > (prices.subList(index - 4, index).minOrNull() ?: prices[index]) && prices[index - 1] < (prices.subList(index - 4, index).minOrNull() ?: prices[index])
                val rsiBounce = rsi in 35.0..60.0 && rsi > calculateRsi(prices, index - 1, 14)
                
                smaFiltered && (pullback || rsiBounce)
            }
            "SMA_CROSS" -> {
                if (index < 15) return false
                val sma5Prev = calculateSma(prices, index - 1, 5)
                val sma15Prev = calculateSma(prices, index - 1, 15)
                val sma5Curr = calculateSma(prices, index, 5)
                val sma15Curr = calculateSma(prices, index, 15)
                sma5Prev <= sma15Prev && sma5Curr > sma15Curr
            }
            "RSI_REV" -> {
                if (index < 14) return false
                val rsiPrev = calculateRsi(prices, index - 1, 14)
                val rsiCurr = calculateRsi(prices, index, 14)
                rsiPrev <= 30.0 && rsiCurr > 30.0
            }
            "MOMENTUM_BREAK" -> {
                if (index < 5) return false
                val currentPrice = prices[index]
                val prevPrices = prices.subList(index - 5, index)
                val prevMax = prevPrices.maxOrNull() ?: currentPrice
                val currentVolume = volumes.getOrNull(index)?.toDouble() ?: 1.0
                
                val prevVolumes = if (volumes.size > index) volumes.subList(index - 5, index) else emptyList()
                val avgVolume = if (prevVolumes.isNotEmpty()) prevVolumes.average() else 1.0
                
                val crossesMax = currentPrice > prevMax
                val volumeClimax = currentVolume >= (avgVolume * 1.3)
                crossesMax && volumeClimax
            }
            else -> false
        }
    }

    private fun checkSellSignal(strategyId: String, prices: List<Double>, volumes: List<Long>, index: Int, holds: Boolean, entryPrice: Double): Boolean {
        if (!holds) return false // Cannot sell if we have no position
        
        return when (strategyId) {
            "ITURRALDE" -> {
                val rsi = calculateRsi(prices, index, 14)
                rsi >= 68.0 || (index >= 4 && prices[index] < (prices.subList(index - 4, index).maxOrNull() ?: Double.MAX_VALUE))
            }
            "CAVA" -> {
                if (index < 26) return false
                val (macdCurrent, signalCurrent) = calculateMacd(prices, index)
                val (macdPrev, signalPrev) = calculateMacd(prices, index - 1)
                val macdCrossDown = macdPrev >= signalPrev && macdCurrent < signalCurrent
                val rsi = calculateRsi(prices, index, 14)
                macdCrossDown || rsi >= 75.0
            }
            "SAEZ" -> {
                if (index < 10) return false
                val sma10 = calculateSma(prices, index, 10)
                val isOverstretched = prices[index] > sma10 * 1.08
                val lowerClose = prices[index] < prices[index - 1]
                isOverstretched || (isOverstretched && lowerClose)
            }
            "ORTEGA" -> {
                if (index < 20) return false
                val currentPrice = prices[index]
                val prevPrice = prices[index - 1]
                val ema20Current = calculateEma(prices, index, 20)
                val ema20Prev = calculateEma(prices, index - 1, 20)
                prevPrice >= ema20Prev && currentPrice < ema20Current
            }
            "GIL" -> {
                val currentPrice = prices[index]
                val stopLossPrice = entryPrice * 0.975
                val takeProfitPrice = entryPrice * 1.05
                currentPrice <= stopLossPrice || currentPrice >= takeProfitPrice
            }
            "LASVIGNES" -> {
                val currentPrice = prices[index]
                val sl = entryPrice * 0.985
                val tp = entryPrice * 1.03
                currentPrice <= sl || currentPrice >= tp
            }
            "MASTER_PROMPT" -> {
                val ema = calculateEma(prices, index, 20)
                val rsi = calculateRsi(prices, index, 14)
                prices[index] < ema || rsi >= 72.0
            }
            "SMA_CROSS" -> {
                if (index < 15) return false
                val sma5Prev = calculateSma(prices, index - 1, 5)
                val sma15Prev = calculateSma(prices, index - 1, 15)
                val sma5Curr = calculateSma(prices, index, 5)
                val sma15Curr = calculateSma(prices, index, 15)
                sma5Prev >= sma15Prev && sma5Curr < sma15Curr
            }
            "RSI_REV" -> {
                if (index < 14) return false
                val rsiPrev = calculateRsi(prices, index - 1, 14)
                val rsiCurr = calculateRsi(prices, index, 14)
                rsiPrev >= 70.0 && rsiCurr < 70.0
            }
            "MOMENTUM_BREAK" -> {
                if (index < 5) return false
                val currentPrice = prices[index]
                val prevPrices = prices.subList(index - 5, index)
                val prevMin = prevPrices.minOrNull() ?: currentPrice
                currentPrice < prevMin
            }
            else -> false
        }
    }

    private fun calculateSma(prices: List<Double>, index: Int, period: Int): Double {
        if (prices.isEmpty() || index < 0) return 0.0
        val idx = minOf(index, prices.size - 1)
        if (idx < period - 1) return prices[idx]
        var sum = 0.0
        for (i in (idx - period + 1)..idx) {
            sum += prices[i]
        }
        return sum / period
    }

    private fun calculateRsi(prices: List<Double>, index: Int, period: Int): Double {
        if (prices.isEmpty() || index < 1) return 50.0
        val idx = minOf(index, prices.size - 1)
        if (idx < period) return 50.0
        var avgGain = 0.0
        var avgLoss = 0.0

        for (i in 1..period) {
            val change = prices[idx - period + i] - prices[idx - period + i - 1]
            if (change > 0) avgGain += change else avgLoss += -change
        }
        avgGain /= period
        avgLoss /= period

        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun formatDate(sec: Long): String {
        val date = Date(sec * 1000)
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(date)
    }

    fun getStrategyName(id: String): String {
        return when (id) {
            "ITURRALDE" -> "Alberto Iturralde (Operativa DAX Falsas Rupturas)"
            "SAEZ" -> "Antonio Sáez del Castillo (Gesmovasa & Elliott)"
            "CAVA" -> "José Luis Cava (Súper Confluencia MACD+ADX+Volumen)"
            "ORTEGA" -> "Alexis Ortega (Timing Macro & Media Semanal)"
            "GIL" -> "Pablo Gil (Fibonacci & Gestión Rígida R/R 1:2)"
            "LASVIGNES" -> "Carlos Lasvignes (Compra a Cero & TP1)"
            "MASTER_PROMPT" -> "Mesa AI (Consenso del Comité)"
            "SMA_CROSS" -> "Cruce de Medias Móviles Rápida vs Lenta"
            "RSI_REV" -> "Reversión por Sobreventa/Sobrecompra RSI"
            "MOMENTUM_BREAK" -> "Ruptura de Canal con Volumen Clímax"
            else -> "Estrategia Personalizada"
        }
    }
}
