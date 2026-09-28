package com.panel.balance.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as FixedColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.panel.balance.MainActivity
import com.panel.balance.R
import com.panel.balance.AppScope
import com.panel.balance.data.AccountEntity
import com.panel.balance.data.AccountStateEntity
import com.panel.balance.data.AppDatabase
import com.panel.balance.data.SettingsStore
import com.panel.balance.data.ServiceLocator
import com.panel.balance.util.formatMoney
import kotlinx.coroutines.launch

/**
 * 单账号小组件：固定 2×1，添加组件时选择要展示的账号。
 */
class SingleAccountWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = (id as? AppWidgetId)?.appWidgetId ?: -1
        val accountId =
            if (appWidgetId > 0) SettingsStore.widgetAccount(context, appWidgetId) else null
        val dao = AppDatabase.get(context).dao()
        // 首选用户配置的账号；无配置（如覆盖安装后组件 ID 被系统重排）回退到第一个账号，
        // 避免显示"未绑定账号"，点击组件仍可进入应用重新选择。
        val acc = accountId?.let { dao.account(it) } ?: dao.allAccounts().firstOrNull()
        val st = acc?.let { dao.state(it.id) }
        android.util.Log.d(
            "PanelWidget",
            "single provideGlance: widgetId=$appWidgetId configAccountId=$accountId showing=${acc?.name}",
        )
        val openIntent = Intent(context, MainActivity::class.java)
        provideContent {
            SingleContent(acc, st, openIntent)
        }
    }
}

class SingleAccountWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleAccountWidget()

    /** 小组件被删除时清理其账号绑定配置。 */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        AppScope.scope.launch {
            appWidgetIds.forEach { SettingsStore.clearWidgetAccount(context, it) }
        }
    }
}

@Composable
private fun SingleContent(acc: AccountEntity?, st: AccountStateEntity?, openIntent: Intent) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_bg))
            .clickable(actionStartActivity(openIntent))
            .padding(10.dp),
    ) {
        if (acc == null) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text("未绑定账号", style = TextStyle(color = widgetPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Spacer(modifier = GlanceModifier.height(3.dp))
                    Text("点击配置", style = TextStyle(color = widgetSecondary, fontSize = 10.sp), maxLines = 1)
                }
            }
        } else {
            val stale = st != null && !st.ok
            val currency = st?.currency?.takeIf { it.isNotBlank() } ?: acc.currency
            val balanceText = if (st != null && st.refreshAt > 0) {
                formatMoney(st.balance, currency)
            } else {
                "—"
            }
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        compactWidgetName(acc.name),
                        style = TextStyle(
                            color = FixedColorProvider(widgetAccent(acc.color)),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                    Spacer(modifier = GlanceModifier.height(1.dp))
                    Text(
                        balanceText,
                        style = TextStyle(
                            color = if (stale) widgetStale else widgetPrimary,
                            fontSize = widgetBalanceSize(balanceText),
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                }
                // 刷新键悬浮在右侧，不参与中心内容排版，也不添加按钮底色。
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(28.dp)
                            .clickable(actionRunCallback<SingleWidgetRefreshAction>()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "↻",
                            style = TextStyle(
                                color = widgetSecondary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private val widgetPrimary = ColorProvider(day = Color(0xFF111111), night = Color.White)
private val widgetSecondary = ColorProvider(day = Color(0xFF6B6B6B), night = Color(0xFFAAAAAA))
private val widgetStale = ColorProvider(day = Color(0xFF9A6500), night = Color(0xFFFFC46B))

private fun widgetAccent(color: Long): Color = lerp(Color(color), Color.White, 0.35f)

/** 2×1 小组件空间有限，截断前先去掉多余空白，避免系统显示省略号。 */
private fun compactWidgetName(name: String): String =
    name.replace(Regex("\\s+"), " ").trim().take(7)

/** 金额越长字号越小，优先保持完整显示，不让系统用省略号截断。 */
private fun widgetBalanceSize(text: String) = when {
    text.length <= 7 -> 28.sp
    text.length <= 9 -> 25.sp
    text.length <= 11 -> 22.sp
    else -> 19.sp
}

/** 单账号小组件上的刷新按钮：刷新账号余额后重绘小组件。 */
class SingleWidgetRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: androidx.glance.GlanceId,
        parameters: ActionParameters,
    ) {
        runCatching {
            val appWidgetId = (glanceId as? AppWidgetId)?.appWidgetId ?: -1
            val accountId = if (appWidgetId > 0) {
                SettingsStore.widgetAccount(context, appWidgetId)
            } else {
                null
            }
            val repo = ServiceLocator.repo(context)
            if (accountId != null) {
                repo.refreshOne(accountId, updateWidgets = false)
            } else {
                repo.refreshAll(updateWidgets = false)
            }
            WidgetUpdater.updateAll(context)
        }
    }
}
