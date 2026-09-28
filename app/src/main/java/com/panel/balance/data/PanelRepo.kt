package com.panel.balance.data

import android.content.Context
import com.panel.balance.AppScope
import com.panel.balance.net.BalanceFetcher
import com.panel.balance.net.FetchKind
import com.panel.balance.net.FetchResult
import com.panel.balance.net.FetchSpec
import com.panel.balance.widget.WidgetUpdater
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 统一的数据操作入口：账号 CRUD、余额刷新、历史快照。 */
class PanelRepo(private val context: Context) {

    private val dao get() = AppDatabase.get(context).dao()
    private val orderMutex = Mutex()

    // ---------- 观察流 ----------

    fun observeAccounts() = dao.observeAccounts()
    fun observeStates() = dao.observeStates()

    suspend fun account(id: Long): AccountEntity? = dao.account(id)
    suspend fun state(id: Long): AccountStateEntity? = dao.state(id)
    suspend fun accountRow(id: Long): Pair<AccountEntity, AccountStateEntity?>? {
        val acc = dao.account(id) ?: return null
        return acc to dao.state(id)
    }

    /** 按时间升序返回的快照（供图表使用）。 */
    suspend fun recordsFor(id: Long, limit: Int = 60): List<BalanceRecordEntity> =
        dao.records(id, limit).asReversed()

    suspend fun latestRefresh(): Long = dao.latestRefresh() ?: 0L

    // ---------- 账号管理 ----------

    suspend fun addAccount(a: AccountEntity): Long {
        // 新建账号默认放在列表末尾。否则 AccountEntity 的默认 sortOrder=0
        // 会让所有通过添加页面创建的账号拥有相同序号，交换排序时不会产生任何变化。
        val lastSortOrder = dao.allAccounts().lastOrNull()?.sortOrder ?: -1
        val id = dao.insertAccount(a.copy(sortOrder = lastSortOrder + 1))
        // 新增后立刻拉一次余额
        AppScope.scope.launch { runCatching { refreshOne(id, updateWidgets = false) } }
        WidgetUpdater.updateAll(context)
        return id
    }

    suspend fun updateAccount(a: AccountEntity) {
        dao.updateAccount(a)
        WidgetUpdater.updateAll(context)
    }

    suspend fun deleteAccount(id: Long) {
        dao.deleteRecords(id)
        dao.deleteState(id)
        dao.deleteAccount(id)
        WidgetUpdater.updateAll(context)
    }

    /** 在排序列表中把账号上移/下移一位。 */
    suspend fun move(id: Long, delta: Int) {
        // 统一串行化所有移动入口（总览拖动和详情页菜单），避免快速操作
        // 时多个协程同时读取同一份旧列表。
        orderMutex.withLock {
            val list = dao.allAccounts()
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) return@withLock
            val target = idx + delta
            if (target !in list.indices) return@withLock

            // 兼容早期数据：当时新增账号的 sortOrder 都可能是 0。先按当前
            // 展示顺序归一化成唯一序号，再交换两个位置，避免“交换相同序号”无效。
            list.forEachIndexed { index, account ->
                if (account.sortOrder != index) dao.setSort(account.id, index)
            }
            dao.setSort(list[idx].id, target)
            dao.setSort(list[target].id, idx)
            WidgetUpdater.updateAll(context)
        }
    }

    // ---------- 余额刷新 ----------

    suspend fun refreshOne(id: Long, updateWidgets: Boolean = true): Boolean {
        val acc = dao.account(id) ?: return false
        // kind 无法识别（如旧版本数据）时无法查询，直接失败返回
        val kind = runCatching { FetchKind.valueOf(acc.kind) }.getOrNull() ?: return false

        val spec = FetchSpec(
            kind = kind,
            baseUrl = acc.baseUrl,
            apiKey = CryptoStore.unprotect(acc.apiKeyEnc),
            jsonPath = acc.customJsonPath,
            authMode = acc.customAuthHeader,
            currency = acc.currency,
        )
        val result = BalanceFetcher.fetch(spec)
        val state = when (result) {
            is FetchResult.Ok -> AccountStateEntity(
                accountId = id,
                balance = result.balance,
                used = result.used,
                ok = true,
                error = null,
                refreshAt = now(),
                currency = result.currency,
            )
            is FetchResult.Err ->
                (dao.state(id) ?: AccountStateEntity(id))
                    .copy(ok = false, error = result.message, refreshAt = now())
        }
        dao.upsertState(state)
        if (result is FetchResult.Ok) {
            // 余额与上次一致时只更新时间/已用量，避免同一数值刷屏历史记录
            val latest = dao.latestRecord(id)
            if (latest != null && latest.balance == result.balance) {
                dao.updateRecordValues(latest.id, now(), result.balance, result.used)
            } else {
                dao.insertRecord(
                    BalanceRecordEntity(accountId = id, time = now(), balance = result.balance, used = result.used)
                )
                dao.pruneRecords(id)
            }
        }
        if (updateWidgets) WidgetUpdater.updateAll(context)
        return result is FetchResult.Ok
    }

    /** 并发刷新所有账号，返回成功数量。 */
    suspend fun refreshAll(updateWidgets: Boolean = true): Int {
        val targets = dao.allAccounts()
        val results: List<Boolean> = coroutineScope {
            targets.map { acc ->
                async { runCatching { refreshOne(acc.id, updateWidgets = false) }.getOrDefault(false) }
            }.awaitAll()
        }
        val ok = results.count { success -> success }
        if (updateWidgets) WidgetUpdater.updateAll(context)
        return ok
    }

    private fun now(): Long = System.currentTimeMillis()
}
