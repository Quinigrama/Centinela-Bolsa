package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_alerts")
data class StockAlert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ticker: String,
    val name: String,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val minVolume: Long? = null,
    val pctChange: Double? = null, // e.g., alert if absolute change > 2%
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val takeProfit2: Double? = null,
    val minBuyPrice: Double? = null,
    val maxBuyPrice: Double? = null,
    val alertTrend: String? = "NONE", // "BULLISH", "BEARISH", "NONE"
    val email: String,
    val isActive: Boolean = true,
    val condLogicalOperator: String? = "NONE", // "AND", "OR", "NONE"
    val unusualVolumeMultiplier: Double? = null, // e.g. 2.0 means 2x average historical volume
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "alert_history")
data class AlertHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ticker: String,
    val alertType: String, // "MIN_PRICE", "MAX_PRICE", "VOLUME", "PCT_CHANGE", "STOP_LOSS", "TAKE_PROFIT", "TREND"
    val triggerValue: Double,
    val message: String,
    val emailSent: Boolean,
    val emailContent: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ia_analysis_history")
data class IaAnalysisHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ticker: String,
    val date: String,
    val price: Double,
    val adviceText: String,
    val timestamp: Long = System.currentTimeMillis()
)
