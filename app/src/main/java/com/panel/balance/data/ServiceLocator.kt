package com.panel.balance.data

import android.content.Context

/** 轻量服务定位器，避免引入 DI 框架。 */
object ServiceLocator {

    @Volatile
    private var repo: PanelRepo? = null

    fun repo(context: Context): PanelRepo =
        repo ?: synchronized(this) {
            repo ?: PanelRepo(context.applicationContext).also { repo = it }
        }
}
