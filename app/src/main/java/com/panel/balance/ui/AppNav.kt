package com.panel.balance.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.panel.balance.ui.add.AddAccountScreen
import com.panel.balance.ui.detail.DetailScreen
import com.panel.balance.ui.overview.OverviewScreen
import com.panel.balance.ui.settings.SettingsScreen

/** 页面路由常量。 */
object Routes {
    const val OVERVIEW = "overview"
    const val ADD = "add"
    const val DETAIL = "detail"
    const val SETTINGS = "settings"
}

/** 应用导航图：总览 → 添加平台 / 详情 / 设置。 */
@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Routes.OVERVIEW) {
        composable(Routes.OVERVIEW) {
            OverviewScreen(
                onAdd = { nav.navigate(Routes.ADD) },
                onOpenDetail = { id -> nav.navigate("${Routes.DETAIL}/$id") },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.ADD) {
            AddAccountScreen(onDone = { nav.popBackStack() })
        }
        composable("${Routes.DETAIL}/{id}") { entry ->
            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
            DetailScreen(id, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
