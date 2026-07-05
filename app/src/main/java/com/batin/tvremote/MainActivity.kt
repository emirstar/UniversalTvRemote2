package com.batin.tvremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.batin.tvremote.ui.navigation.TvRemoteNavHost
import com.batin.tvremote.ui.theme.TvRemoteTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TvRemoteApp()
        }
    }
}

@Composable
private fun TvRemoteApp() {
    TvRemoteTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            TvRemoteNavHost()
        }
    }
}
