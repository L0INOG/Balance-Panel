package com.panel.balance

import android.app.Application
import com.panel.balance.data.SettingsStore
import com.panel.balance.work.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 全局协程作用域：应用级后台任务（如新增账号后的首次刷新）。 */
object AppScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class PanelApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 按用户设置恢复周期刷新任务（WorkManager 初始化异常时静默跳过）
        AppScope.scope.launch {
            runCatching {
                val settings = SettingsStore.settings(this@PanelApp).first()
                RefreshWorker.apply(this@PanelApp, settings.intervalMin)
            }
            // 覆盖安装后系统可能给小组件重新分配 ID，启动时全量重渲染一次
            runCatching {
                com.panel.balance.widget.WidgetUpdater.updateAll(this@PanelApp)
            }
        }
    }
}
