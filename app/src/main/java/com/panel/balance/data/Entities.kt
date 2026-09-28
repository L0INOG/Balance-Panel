package com.panel.balance.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一个受管理的平台账号。
 * API Key 以 [CryptoStore] 加密后存放在 [apiKeyEnc]。
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val platformId: String,          // PlatformPresets 里的 id
    val kind: String,                // FetchKind 名称
    val color: Long,                 // 平台主题色（ARGB）
    val apiKeyEnc: String = "",
    val baseUrl: String = "",
    val currency: String = "CNY",
    val customJsonPath: String = "", // CUSTOM_JSON 用的 JSON 路径
    val customAuthHeader: String = "Bearer",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 最近一次查询到的余额状态。currency 记录本次返回的实际币种（空 = 未刷新过，回退账号设置）。 */
@Entity(tableName = "account_state")
data class AccountStateEntity(
    @PrimaryKey val accountId: Long,
    val balance: Double = 0.0,
    val used: Double = 0.0,
    val ok: Boolean = false,
    val error: String? = null,
    val refreshAt: Long = 0,
    val currency: String = "",
)

/** 余额快照，用于绘制趋势图。 */
@Entity(
    tableName = "balance_records",
    indices = [Index("accountId"), Index("time")],
)
data class BalanceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val time: Long,
    val balance: Double,
    val used: Double = 0.0,
)

/** 账号 + 状态的组合视图。 */
data class AccountWithState(
    @Embedded val account: AccountEntity,
    val state: AccountStateEntity?,
)
