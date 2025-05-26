package com.example.heartratesender.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Text
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var currentHeartRate: Float = 0f
    private val client = OkHttpClient()

    private val handler = Handler(Looper.getMainLooper())
    private val sendInterval = 5000L // 5 segundos

    private val sendRunnable = object : Runnable {
        override fun run() {
            enviarHeartrate(currentHeartRate.toInt())
            handler.postDelayed(this, sendInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Verificar permiso
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                1
            )
        } else {
            initSensor()
        }

        setContent {
            var hrState by remember { mutableStateOf(currentHeartRate.toInt()) }

            // UI principal
            WearApp(
                greetingName = "Android",
                heartrate = hrState,
                onSendHeartrate = {
                    enviarHeartrate(currentHeartRate.toInt())
                    hrState = currentHeartRate.toInt()
                }
            )
        }
    }

    private fun initSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (heartRateSensor != null) {
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
            handler.post(sendRunnable) // Inicia envío automático
        } else {
            Log.w("Sensor", "Sensor de ritmo cardíaco no disponible.")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            currentHeartRate = event.values[0]
            Log.d("Sensor", "Ritmo cardíaco detectado: $currentHeartRate")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(sendRunnable)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initSensor()
        } else {
            Log.w("Permission", "Permiso BODY_SENSORS denegado")
        }
    }

    private fun enviarHeartrate(heartrate: Int) {
        val url = "http://10.0.2.2:8000/heartrate"
        val json = """{"heartrate": $heartrate}"""
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DEBUG", "Error al enviar heartrate: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d("Debug", "Respuesta del servidor: ${response.code}")
                response.close()
            }
        })
    }
}


@Composable
fun WearApp(greetingName: String, heartrate: Int, onSendHeartrate: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Yellow),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Cyan)
        ) {
            Text("HR actual: $heartrate", color = Color.Red)

            Greeting(greetingName = greetingName)

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onSendHeartrate) {
                Text("Enviar Heartrate", color = Color.Black)
            }
        }
    }
}

@Composable
fun Greeting(greetingName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = Color.Black,
        text = "Hola $greetingName"
    )
}

