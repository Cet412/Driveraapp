package com.cera.driveraapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cera.driveraapp.camera.CameraService
import com.cera.driveraapp.ui.theme.DriveraAppTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning

class MainActivity : ComponentActivity() {
    private var isServiceRunning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DriveraAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DriverMonitoringScreen(
                        isServiceRunning = isServiceRunning,
                        onStartStopClick = { toggleService() }
                    )
                }
            }
        }
    }

    private fun toggleService() {
        val serviceIntent = Intent(this, CameraService::class.java)
        if (isServiceRunning) {
            stopService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        isServiceRunning = !isServiceRunning
    }
}

@Composable
fun DriverMonitoringScreen(
    isServiceRunning: Boolean,
    onStartStopClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Status Indicator
        Icon(
            imageVector = if (isServiceRunning) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = "Service Status",
            tint = if (isServiceRunning) Color.Green else Color.Red,
            modifier = Modifier.size(48.dp)

                    Spacer(modifier = Modifier.height(24.dp))

                    // Control Button
                    Button(
                    onClick = onStartStopClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (isServiceRunning) "Stop Monitoring" else "Start Monitoring")
        }
    }
}