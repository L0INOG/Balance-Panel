package com.panel.balance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.panel.balance.ui.AppNav
import com.panel.balance.ui.theme.PanelTheme

/** 单 Activity 入口：边缘到边缘显示，承载 Compose 导航。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PanelTheme {
                AppNav()
            }
        }
    }
}
