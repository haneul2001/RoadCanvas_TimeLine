package com.carnect.roadcanvas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.carnect.roadcanvas.ui.StatusScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as RoadCanvasApp
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    StatusScreen(app.vehicleRepo, app.naviRepo, app.tts)
                }
            }
        }
    }
}
