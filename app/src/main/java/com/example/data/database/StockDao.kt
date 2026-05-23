package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    // Alert rules queries
    @Query("SELECT * FROM stock_alerts ORDER BY id DESC")
    fun getAllAlerts(): Flow<List<StockAlert>>

    @Query("SELECT * FROM stock_alerts WHERE isActive = 1")
    fun getActiveAlerts(): Flow<List<StockAlert>>

    @Query("SELECT * FROM stock_alerts WHERE isActive = 1")
    suspend fun getActiveAlertsList(): List<StockAlert>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: StockAlert)

    @Update
    suspend fun updateAlert(alert: StockAlert)

    @Delete
    suspend fun deleteAlert(alert: StockAlert)

    @Query("DELETE FROM stock_alerts WHERE id = :id")
    suspend fun deleteAlertById(id: Int)

    // Alert history logs queries
    @Query("SELECT * FROM alert_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<AlertHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: AlertHistory)

    @Query("DELETE FROM alert_history")
    suspend fun clearHistory()

    // IA Analysis history queries
    @Query("SELECT * FROM ia_analysis_history ORDER BY timestamp DESC")
    fun getAllIaAnalysisHistory(): Flow<List<IaAnalysisHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIaAnalysisHistory(iaHistory: IaAnalysisHistory)

    @Query("DELETE FROM ia_analysis_history")
    suspend fun clearIaAnalysisHistory()

    @Query("DELETE FROM ia_analysis_history WHERE id = :id")
    suspend fun deleteIaAnalysisHistoryById(id: Int)
}
