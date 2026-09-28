package com.panel.balance.net

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * OneAPI/NewAPI/AiHubMix 系中转站联调（可选）：环境变量提供 RELAY_BASE 与 RELAY_KEY，
 * 未设置时跳过。验证修复后的 OPENAI_BILLING 路径（优先 /api/user/self 真实 quota，
 * 不再出现 hard_limit_usd=1e8 导致的「1 亿元」余额）。
 */
class RelayLiveTest {

    @Test
    fun fetchRealRelayBalance() {
        val base: String? = System.getenv("RELAY_BASE")
        val key: String? = System.getenv("RELAY_KEY")
        assumeTrue("未设置 RELAY_BASE/RELAY_KEY，跳过联调", !base.isNullOrBlank() && !key.isNullOrBlank())
        val baseUrl = base ?: return
        val apiKey = key ?: return

        val result = runBlocking {
            BalanceFetcher.fetch(
                FetchSpec(kind = FetchKind.OPENAI_BILLING, baseUrl = baseUrl, apiKey = apiKey)
            )
        }
        assertTrue("期望查询成功，实际：$result", result is FetchResult.Ok)
        val ok = result as FetchResult.Ok
        assertTrue("余额应小于 10000（不出现 1e8 天文数字），实际：${ok.balance}", ok.balance < 10_000.0)
        println("中转站余额：$${ok.balance}（已用 $${ok.used}）")
    }
}
