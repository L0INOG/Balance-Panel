package com.panel.balance.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.panel.balance.data.AccountEntity
import com.panel.balance.data.AccountStateEntity
import com.panel.balance.data.ServiceLocator
import com.panel.balance.data.SettingsStore
import com.panel.balance.ui.common.PlatformAvatar
import com.panel.balance.ui.common.tnum
import com.panel.balance.ui.theme.PanelTheme
import com.panel.balance.util.formatMoney
import kotlinx.coroutines.launch

/** 添加「单账号余额」小组件时的账号选择页。 */
class SingleWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // 用户取消时以 CANCELED 结束
        setResult(RESULT_CANCELED)
        setContent {
            PanelTheme {
                PickerScreen(
                    appWidgetId = appWidgetId,
                    onPick = { accountId -> finishWith(appWidgetId, accountId) },
                )
            }
        }
    }

    private fun finishWith(appWidgetId: Int, accountId: Long) {
        lifecycleScope.launch {
            val ctx = applicationContext
            SettingsStore.setWidgetAccount(ctx, appWidgetId, accountId)
            android.util.Log.w("PanelWidget", "config saved: widgetId=$appWidgetId accountId=$accountId")
            runCatching {
                val manager = GlanceAppWidgetManager(this@SingleWidgetConfigActivity)
                val glanceId = manager.getGlanceIdBy(appWidgetId)
                SingleAccountWidget().update(this@SingleWidgetConfigActivity, glanceId)
            }
            // 双保险：立刻全量刷新一次小组件，避免部分 ROM 延迟派发系统更新
            runCatching { WidgetUpdater.updateAll(ctx) }
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerScreen(appWidgetId: Int, onPick: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repo(context.applicationContext) }
    val accounts = repo.observeAccounts()
        .collectAsState(initial = emptyList<AccountEntity>()).value
    val stateList = repo.observeStates()
        .collectAsState(initial = emptyList<AccountStateEntity>()).value
    val stateMap: Map<Long, AccountStateEntity> = stateList.associateBy { it.accountId }

    Scaffold(
        topBar = { TopAppBar(title = { Text("选择要展示的账号") }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (accounts.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "请先在应用中添加平台账号",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(accounts, key = { it.id }) { acc ->
                    val st = stateMap[acc.id]
                    Card(
                        onClick = { onPick(acc.id) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlatformAvatar(acc.name, acc.color, 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(acc.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    acc.currency,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = if (st?.ok == true) formatMoney(st?.balance ?: 0.0, acc.currency) else "--",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                style = tnum(MaterialTheme.typography.bodyMedium),
                            )
                        }
                    }
                }
            }
        }
    }
}
