package com.panel.balance.net

import com.panel.balance.util.formatTokens
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 智谱接口真机联调（可选）：需要环境变量 ZHIPU_KEY 提供真实 API Key，
 * 未设置时自动跳过。验证 ZHIPU（现金）与 ZHIPU_PACKAGES（资源包）两条完整路径。
 *
 * 运行：ZHIPU_KEY=<你的key> ./gradlew testDebugUnitTest --tests "com.panel.balance.net.ZhipuLiveTest"
 */
class ZhipuLiveTest {

    @Test
    fun fetchRealZhipuCash() {
        val apiKey = keyOrNull() ?: return
        val result = runBlocking {
            BalanceFetcher.fetch(FetchSpec(FetchKind.ZHIPU, "https://bigmodel.cn", apiKey))
        }
        assertTrue("期望查询成功，实际：$result", result is FetchResult.Ok)
        val ok = result as FetchResult.Ok
        assertTrue("现金余额应大于 0，实际：${ok.balance}", ok.balance > 0)
        assertTrue("币种应为 CNY，实际：${ok.currency}", ok.currency == "CNY")
        println("智谱现金余额：¥${ok.balance}（累计已用 ¥${ok.used}）")
    }

    @Test
    fun fetchRealZhipuPackages() {
        val apiKey = keyOrNull() ?: return
        val result = runBlocking {
            BalanceFetcher.fetch(FetchSpec(FetchKind.ZHIPU_PACKAGES, "https://bigmodel.cn", apiKey))
        }
        assertTrue("期望查询成功，实际：$result", result is FetchResult.Ok)
        val ok = result as FetchResult.Ok
        assertTrue("Token 额度应大于 0，实际：${ok.balance}", ok.balance > 0)
        println("智谱资源包剩余 Token：${formatTokens(ok.balance)}（原始值 ${ok.balance}）")
    }

    private fun keyOrNull(): String? {
        val key: String? = System.getenv("ZHIPU_KEY")
        assumeTrue("未设置 ZHIPU_KEY，跳过联调", !key.isNullOrBlank())
        return key
    }
}
