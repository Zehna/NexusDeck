package com.nexusdeck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nexusdeck.app.core.profiler.HardwareProfiler
import com.nexusdeck.app.core.storage.NexusStorageManager
import com.nexusdeck.app.ui.DashboardScreen
import com.nexusdeck.app.ui.theme.NexusFullRoidDarkColorScheme
import com.nexusdeck.app.ui.theme.NexusTypography

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NexusStorageManager.ensureStandardFolders(this)

        setContent {
            val context = LocalContext.current
            val hardwareSpec = remember {
                HardwareProfiler(context).detectHardwareSpec()
            }

            NexusDeckTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(hardwareSpec = hardwareSpec)
                }
            }
        }
    }
}

@Composable
fun NexusDeckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexusFullRoidDarkColorScheme,
        typography = NexusTypography,
        content = content
    )
}
