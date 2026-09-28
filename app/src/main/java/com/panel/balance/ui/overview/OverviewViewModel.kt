package com.panel.balance.ui.overview

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.panel.balance.data.AccountEntity
import com.panel.balance.data.AccountStateEntity
import com.panel.balance.data.ServiceLocator
import com.panel.balance.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 总览列表行：账号 + 其余额状态。 */
data class AccountRow(
    val account: AccountEntity,
    val state: AccountStateEntity?,
)

/** 总览页 UI 状态。 */
data class OverviewUiState(
    val loaded: Boolean = false,
    val items: List<AccountRow> = emptyList(),
    val totals: List<Pair<String, Double>> = emptyList(),
    val errorCount: Int = 0,
    val lastRefresh: Long = 0,
    val refreshing: Boolean = false,
)

/** 币种展示顺序：CNY 优先，其次 USD，其余（如 TOKEN）排后。 */
private fun currencyOrder(currency: String): Int = when (currency.uppercase()) {
    "CNY" -> 0
    "USD" -> 1
    else -> 2
}

/** 总览页 ViewModel：合并账号流与状态流，触发全量刷新。 */
class OverviewViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.repo(app)
    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<OverviewUiState> = combine(
        repo.observeAccounts(),
        repo.observeStates(),
        SettingsStore.settings(app),
        refreshing,
    ) { accs, states, _, isRefreshing ->
        val sm = states.associateBy { it.accountId }
        val items = accs.map { AccountRow(it, sm[it.id]) }
        // 有过成功快照的账号都计入总额（含最近一次刷新失败但保留了旧值的账号）
        // 币种以最近一次刷新返回的实际币种为准（如智谱的 TOKEN），空则回退账号设置
        val totals = items
            .filter { (it.state?.refreshAt ?: 0) > 0 }
            .groupBy { row -> row.state?.currency?.takeIf { c -> c.isNotBlank() } ?: row.account.currency }
            .map { (cur, rows) -> cur to rows.sumOf { it.state?.balance ?: 0.0 } }
            .sortedWith(compareBy({ currencyOrder(it.first) }, { -it.second }))
        OverviewUiState(
            loaded = true,
            items = items,
            totals = totals,
            errorCount = items.count { it.state?.ok == false && (it.state?.refreshAt ?: 0) > 0 },
            lastRefresh = states.maxOfOrNull { it.refreshAt } ?: 0,
            refreshing = isRefreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    init {
        viewModelScope.launch {
            val cfg = SettingsStore.settings(getApplication()).first()
            if (cfg.refreshOnLaunch) {
                val last = repo.latestRefresh()
                if (System.currentTimeMillis() - last > 5 * 60_000) refresh()
            }
        }
    }

    /** 触发一次全量刷新（下拉刷新 / 启动时自动刷新 / 主平台卡刷新按钮）。 */
    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            try {
                repo.refreshAll()
            } finally {
                refreshing.value = false
            }
        }
    }
}
