package com.example.hw6_3

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.hw6_3.ui.theme.HW6_3Theme
import kotlinx.coroutines.*
import kotlin.math.log10
import kotlin.math.sqrt

private const val TAG = "SoundMeter"

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, recording will start
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request microphone permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            HW6_3Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SoundMeterScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun SoundMeterScreen(modifier: Modifier = Modifier) {
    var decibelLevel by remember { mutableStateOf(0f) }
    var showAlert by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStatus by remember { mutableStateOf("Initializing...") }

    val threshold = 70f // Noise threshold in dB
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            startRecording(
                onStatusUpdate = { status ->
                    recordingStatus = status
                    isRecording = status == "Recording"
                    Log.d(TAG, "Status: $status")
                },
                onAmplitudeUpdate = { db ->
                    decibelLevel = db
                    showAlert = db > threshold
                    Log.d(TAG, "dB: $db")
                }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sound Meter",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Recording status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .padding(end = 8.dp)
            ) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Green, shape = MaterialTheme.shapes.small)
                    )
                }
            }
            Text(
                text = recordingStatus,
                fontSize = 14.sp,
                color = if (isRecording) Color.Green else Color.Gray
            )
        }

        // Decibel value display
        Text(
            text = "${decibelLevel.toInt()} dB",
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            color = if (showAlert) Color.Red else MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Visual indicator - Progress bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Sound Level",
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LinearProgressIndicator(
                progress = { (decibelLevel / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                color = when {
                    decibelLevel > threshold -> Color.Red
                    decibelLevel > 50f -> Color(0xFFFFA500) // Orange
                    else -> Color.Green
                },
                trackColor = Color.LightGray
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Threshold indicator
        Text(
            text = "Threshold: $threshold dB",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Alert message
        if (showAlert) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFEBEE)
                )
            ) {
                Text(
                    text = "⚠️ ALERT: Noise level exceeds threshold!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

private suspend fun startRecording(
    onStatusUpdate: (String) -> Unit,
    onAmplitudeUpdate: (Float) -> Unit
) {
    withContext(Dispatchers.IO) {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        Log.d(TAG, "Buffer size: $bufferSize")

        try {
            withContext(Dispatchers.Main) {
                onStatusUpdate("Initializing AudioRecord...")
            }

            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized!")
                withContext(Dispatchers.Main) {
                    onStatusUpdate("Error: AudioRecord not initialized")
                }
                return@withContext
            }

            Log.d(TAG, "AudioRecord initialized successfully")
            withContext(Dispatchers.Main) {
                onStatusUpdate("Starting recording...")
            }

            audioRecord.startRecording()
            Log.d(TAG, "Recording started")

            withContext(Dispatchers.Main) {
                onStatusUpdate("Recording")
            }

            val buffer = ShortArray(bufferSize)
            var readCount = 0

            while (isActive) {
                val readSize = audioRecord.read(buffer, 0, bufferSize)
                readCount++

                if (readSize > 0) {
                    // Calculate RMS (Root Mean Square) amplitude
                    var sum = 0.0
                    for (i in 0 until readSize) {
                        sum += (buffer[i] * buffer[i]).toDouble()
                    }
                    val rms = sqrt(sum / readSize)

                    // Convert to decibels
                    val db = if (rms > 0) {
                        // Reference value for normalization
                        val reference = 32767.0 // Max value for 16-bit audio
                        20 * log10(rms / reference) + 90 // Add offset to get reasonable dB range
                    } else {
                        0.0
                    }

                    if (readCount % 10 == 0) { // Log every 10th read
                        Log.d(TAG, "Read #$readCount: RMS=$rms, dB=$db")
                    }

                    withContext(Dispatchers.Main) {
                        onAmplitudeUpdate(db.toFloat().coerceIn(0f, 100f))
                    }
                } else {
                    Log.w(TAG, "Read size <= 0: $readSize")
                }

                delay(100) // Update every 100ms
            }

            audioRecord.stop()
            audioRecord.release()
            Log.d(TAG, "Recording stopped and released")

            withContext(Dispatchers.Main) {
                onStatusUpdate("Stopped")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception - permission not granted", e)
            withContext(Dispatchers.Main) {
                onStatusUpdate("Error: Permission denied")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during recording", e)
            withContext(Dispatchers.Main) {
                onStatusUpdate("Error: ${e.message}")
            }
            e.printStackTrace()
        }
    }
}