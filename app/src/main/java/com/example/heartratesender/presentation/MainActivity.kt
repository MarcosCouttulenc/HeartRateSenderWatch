package com.example.heartratesender.presentation

import com.example.heartratesender.BuildConfig
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
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object HeartRateBridge {
    var hrUpdater: ((Int) -> Unit)? = null
}

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var currentHeartRate: Float = 0f
    private val client = OkHttpClient()

    private val handler = Handler(Looper.getMainLooper())
    private val sendInterval = 500L // medio segundo

    private val sendRunnable = object : Runnable {
        override fun run() {
            enviarHeartrate(currentHeartRate.toInt())
            handler.postDelayed(this, sendInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Solicitar permiso de sensor corporal
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
            // Este estado estará vinculado a la UI y se actualizará cuando cambie currentHeartRate
            var hrState by remember { mutableStateOf(0) }
            var estadoTransmision by remember { mutableStateOf(false) }
            var sessionId by remember { mutableStateOf("") }

            // Guardamos una referencia para actualizar desde el listener
            LaunchedEffect(Unit) {
                HeartRateBridge.hrUpdater = { newValue ->
                    hrState = newValue
                }
            }

            // Bucle que actualiza el estado desde la gateway cada 2 segundos
            LaunchedEffect(Unit) {
                while (true) {
                    try {
                        val url = "http://${BuildConfig.SERVER_IP}:8000/estado"
                        Log.d("DEBUG", "Solicitando estado a: $url")

                        val request = Request.Builder().url(url).build()
                        val response = withContext(Dispatchers.IO) {
                            client.newCall(request).execute()
                        }

                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = org.json.JSONObject(body)
                            val listo = json.getBoolean("listo_para_transmitir")
                            estadoTransmision = listo
                            Log.d("DEBUG", "JSON parseado correctamente -> listo=$listo")
                        }
                        response.close()
                    } catch (e: Exception) {
                        Log.e("DEBUG", "Error solicitando estado: ${e.message}")
                        estadoTransmision = false
                    }

                    kotlinx.coroutines.delay(2000)
                }
            }

            FiusenseApp(
                heartrate = hrState,
                estadoTransmision = estadoTransmision
            )
        }


    }

    private fun initSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (heartRateSensor != null) {
            sensorManager.unregisterListener(this)
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL)
            handler.post(sendRunnable)
        } else {
            Log.w("Sensor", "Sensor de ritmo cardíaco no disponible.")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HEART_RATE) {
            currentHeartRate = event.values[0]
            Log.d("Sensor", "Ritmo cardíaco detectado: $currentHeartRate")
            HeartRateBridge.hrUpdater?.invoke(currentHeartRate.toInt())
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
        val url = "http://${BuildConfig.SERVER_IP}:8000/heartrate"
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val username = "marcos" // reemplazalo por tu usuario real

        val json = """{
            "username": "$username",
            "heartrate": $heartrate,
            "timestamp": "$timestamp"
        }""".trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toRequestBody(mediaType)

        Log.d("DEBUG", "Enviando heartrate: $json a la URL: $url")

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
fun FiusenseApp(heartrate: Int, estadoTransmision: Boolean) {
    var beating by remember { mutableStateOf(true) }
    val scale by animateFloatAsState(
        targetValue = if (beating) 1.2f else 1.0f,
        animationSpec = tween(durationMillis = 400, easing = LinearEasing)
    )

    LaunchedEffect(Unit) {
        while (true) {
            beating = !beating
            kotlinx.coroutines.delay(500)
        }
    }

    val estadoTexto = if (estadoTransmision) "Transmitiendo..." else "Desconectado"
    val estadoColor = if (estadoTransmision) Color(0xFF00BFA6) else Color.Red

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101820)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Fiusense",
                color = Color(0xFF00BFA6),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Heart icon",
                tint = Color.Red,
                modifier = Modifier
                    .size(40.dp)
                    .scale(scale)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${heartrate} bpm",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = estadoTexto,
                color = estadoColor,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

