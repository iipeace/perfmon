package com.oss.perfmon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.oss.perfmon.ui.screen.HomeScreen
import com.oss.perfmon.ui.screen.MonitorScreen
import com.oss.perfmon.ui.screen.ResmonScreen
import com.oss.perfmon.ui.screen.SystemScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home") { HomeScreen(navController) }
                    composable("monitor") { MonitorScreen(navController) }
                    composable("resmon") { ResmonScreen(navController) }
                    composable("system") { SystemScreen(navController) }
                }
            }
        }
    }
}
