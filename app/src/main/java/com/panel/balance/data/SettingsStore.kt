package com.panel.balance.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "panel_settings")

/** 用户设置：周期刷新间隔与启动时刷新开关。 */
data class PanelSettings(
    val intervalMin: Int = 60,       // 0 = 关闭自动刷新
    val refreshOnLaunch: Boolean = true,
)

/** DataStore 封装：刷新设置 + 小组件与账号的绑定关系。 */
object SettingsStore {

    private val KEY_INTERVAL = intPreferencesKey("refresh_interval_minutes")
    private val KEY_ON_LAUNCH = booleanPreferencesKey("refresh_on_launch")
    private fun widgetKey(appWidgetId: Int) = stringPreferencesKey("widget_single_$appWidgetId")

    fun settings(context: Context): Flow<PanelSettings> =
        context.dataStore.data.map { p ->
            PanelSettings(
                intervalMin = p[KEY_INTERVAL] ?: 60,
                refreshOnLaunch = p[KEY_ON_LAUNCH] ?: true,
            )
        }

    suspend fun setInterval(context: Context, value: Int) {
        context.dataStore.edit { it[KEY_INTERVAL] = value }
    }

    suspend fun setRefreshOnLaunch(context: Context, value: Boolean) {
        context.dataStore.edit { it[KEY_ON_LAUNCH] = value }
    }

    /** 绑定小组件 → 账号（单账号小组件配置页保存）。 */
    suspend fun setWidgetAccount(context: Context, appWidgetId: Int, accountId: Long) {
        context.dataStore.edit { it[widgetKey(appWidgetId)] = accountId.toString() }
    }

    /** 读取小组件绑定的账号 id，未绑定返回 null。 */
    suspend fun widgetAccount(context: Context, appWidgetId: Int): Long? =
        context.dataStore.data.map { p -> p[widgetKey(appWidgetId)]?.toLongOrNull() }.firstOrNull()

    /** 小组件被删除后清理绑定。 */
    suspend fun clearWidgetAccount(context: Context, appWidgetId: Int) {
        context.dataStore.edit { it.remove(widgetKey(appWidgetId)) }
    }
}
