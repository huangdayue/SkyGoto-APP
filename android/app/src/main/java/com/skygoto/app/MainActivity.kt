package com.skygoto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.skygoto.app.ui.navigation.SkyGotoNavHost
import com.skygoto.app.ui.theme.SkyGotoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkyGotoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SkyGotoNavHost()
                }
            }
        }
    }
}
