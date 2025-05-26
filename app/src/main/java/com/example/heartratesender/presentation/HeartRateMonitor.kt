/*package com.example.heartratesender.presentation

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow


import androidx.health.services.client.data.SampleDataPoint



class HeartRateMonitor(context: Context) {

    private val healthClient = HealthServices.getClient(context).measureClient

    private val _heartRateFlow = MutableStateFlow(0)
    val heartRateFlow: Flow<Int> = _heartRateFlow

    private val callback: (List<DataPoint<Double>>) -> Unit = { dataPoints ->
        for (dataPoint in dataPoints) {
            if (dataPoint.dataType == DataType.HEART_RATE_BPM) {
                // Acceder con getValueAsDouble()
                val bpmDouble = d
                val bpm = bpmDouble.toInt()
                Log.d("HeartRateMonitor", "Received heart rate: $bpm")
                _heartRateFlow.value = bpm
            }
        }
    }



    suspend fun startMonitoring() {
        healthClient.registerDataCallback(setOf(DataType.HEART_RATE_BPM), callback)
    }

    suspend fun stopMonitoring() {
        healthClient.unregisterDataCallback(callback)
    }
}
*/