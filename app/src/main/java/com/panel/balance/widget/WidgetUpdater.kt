package com.panel.balance.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** 数据变化后统一刷新所有小组件。 */
object WidgetUpdater {

    suspend fun updateAll(context: Context) {
        runCatching { SingleAccountWidget().updateAll(context) }
    }
}
