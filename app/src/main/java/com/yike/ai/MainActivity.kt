package com.yike.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yike.ai.core.designsystem.YikeTheme
import com.yike.ai.feature.AppViewModel
import com.yike.ai.navigation.YikeApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel()
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            val dark =
                when (prefs.theme) {
                    "LIGHT" -> false
                    "SYSTEM" -> isSystemInDarkTheme()
                    else -> true
                }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            YikeTheme(prefs) { YikeApp(vm) }
        }
    }
}
