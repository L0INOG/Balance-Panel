package com.panel.balance.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.panel.balance.data.ServiceLocator
import com.panel.balance.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/** 周期性刷新所有账号余额并同步小组件。 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching {
            ServiceLocator.repo(applicationContext).refreshAll(updateWidgets = false)
            WidgetUpdater.updateAll(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "panel_periodic_refresh"

        /** intervalMinutes <= 0 表示关闭自动刷新。 */
        fun apply(context: Context, intervalMinutes: Int) {
            val wm = WorkManager.getInstance(context)
            if (intervalMinutes <= 0) {
                wm.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
