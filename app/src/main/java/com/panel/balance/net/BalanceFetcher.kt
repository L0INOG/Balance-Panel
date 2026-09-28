package com.panel.balance.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** 余额查询方式。 */
enum class FetchKind {
    DEEPSEEK,       // GET /user/balance
    MOONSHOT,       // GET /v1/users/me/balance
    SILICONFLOW,    // GET /v1/user/balance
    OPENROUTER,     // GET /api/v1/credits
    ZHIPU,          // GET bigmodel.cn/api/biz/account/query-customer-account-report（现金余额）
    ZHIPU_PACKAGES, // GET bigmodel.cn/api/biz/tokenAccounts/list/my（资源包 Token 额度）
    OPENAI_BILLING, // GET /v1/dashboard/billing/*（OneAPI / NewAPI / 中转站兼容）
    CUSTOM_JSON,    // 任意接口 + JSON 路径
}

sealed interface FetchResult {
    data class Ok(
        val balance: Double,
        val used: Double,
        val currency: String,
        val raw: String,
    ) : FetchResult

    data class Err(val message: String) : FetchResult
}

data class FetchSpec(
    val kind: FetchKind,
    val baseUrl: String,
    val apiKey: String,
    val jsonPath: String = "",
    val authMode: String = "Bearer", // CUSTOM_JSON 用：Bearer / X-API-Key / 无 / URL
    val currency: String = "CNY",
)

/** 各平台余额查询实现。 */
object BalanceFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(spec: FetchSpec): FetchResult = withContext(Dispatchers.IO) {
        runCatching {
            when (spec.kind) {
                FetchKind.DEEPSEEK -> fetchDeepseek(spec)
                FetchKind.MOONSHOT -> fetchMoonshot(spec)
                FetchKind.SILICONFLOW -> fetchSiliconflow(spec)
                FetchKind.OPENROUTER -> fetchOpenRouter(spec)
                FetchKind.ZHIPU -> fetchZhipuCash(spec)
                FetchKind.ZHIPU_PACKAGES -> fetchZhipuPackages(spec)
                FetchKind.OPENAI_BILLING -> fetchOpenAiBilling(spec)
                FetchKind.CUSTOM_JSON -> fetchCustom(spec)
            }
        }.getOrElse { e ->
            FetchResult.Err(
                when (e) {
                    is IOException -> "网络错误：${e.message ?: "连接失败"}"
                    is kotlinx.serialization.SerializationException -> "返回内容不是合法 JSON"
                    else -> "请求失败：${e.message ?: e.javaClass.simpleName}"
                }
            )
        }
    }

    // ---------- 各平台实现 ----------

    // {"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"110.00",...}, ...]}
    // 注意：balance_infos 是数组，可能同时包含 CNY/USD 多个钱包（USD 0.00 可能排在首位），
    // 取余额最大的那个钱包（相同金额时优先 CNY），避免把有余额的币种漏掉。
    private fun fetchDeepseek(spec: FetchSpec): FetchResult {
        val (code, body) = get(url(spec.baseUrl, "/user/balance"), spec)
        ensureHttp(code, body)
        val root = json.parseToJsonElement(body)
        val infos = JsonPath.get(root, "balance_infos")
            as? kotlinx.serialization.json.JsonArray
            ?: return FetchResult.Err("未找到 balance_infos 字段")
        var bestBalance = -1.0
        var bestCurrency = "CNY"
        for (info in infos) {
            val v = JsonPath.num(info, "total_balance") ?: continue
            val cur = JsonPath.str(info, "currency") ?: "CNY"
            if (v > bestBalance || (v == bestBalance && cur.equals("CNY", true) && !bestCurrency.equals("CNY", true))) {
                bestBalance = v
                bestCurrency = cur
            }
        }
        if (bestBalance < 0) return FetchResult.Err("balance_infos 中没有 total_balance 字段")
        return FetchResult.Ok(bestBalance, 0.0, bestCurrency, body)
    }

    // {"data":{"available_balance":"9.00","voucher_balance":"10.00","total_balance":"19.00","currency":"CNY"}}
    private fun fetchMoonshot(spec: FetchSpec): FetchResult {
        val (code, body) = get(url(spec.baseUrl, "/v1/users/me/balance"), spec)
        ensureHttp(code, body)
        val root = json.parseToJsonElement(body)
        val data = JsonPath.get(root, "data") ?: root
        val total = JsonPath.num(data, "total_balance")
            ?: return FetchResult.Err("未找到 total_balance 字段")
        val available = JsonPath.num(data, "available_balance")
        val currency = JsonPath.str(data, "currency") ?: spec.currency
        val used = if (available != null) (total - available).coerceAtLeast(0.0) else 0.0
        return FetchResult.Ok(total, used, currency, body)
    }

    // {"code":200,"data":{"balance":"1.23","totalBalance":"100.00"}}
    private fun fetchSiliconflow(spec: FetchSpec): FetchResult {
        var (code, body) = get(url(spec.baseUrl, "/v1/user/balance"), spec)
        if (code != 200) {
            val retry = get(url(spec.baseUrl, "/v1/user/info"), spec)
            code = retry.first
            body = retry.second
        }
        ensureHttp(code, body)
        val root = json.parseToJsonElement(body)
        val balance = JsonPath.num(root, "data.balance")
            ?: return FetchResult.Err("未找到 data.balance 字段")
        return FetchResult.Ok(balance, 0.0, spec.currency, body)
    }

    // {"data":{"total_credits":10.0,"total_usage":2.5}}
    private fun fetchOpenRouter(spec: FetchSpec): FetchResult {
        val (code, body) = get(url(spec.baseUrl, "/api/v1/credits"), spec)
        ensureHttp(code, body)
        val root = json.parseToJsonElement(body)
        val credits = JsonPath.num(root, "data.total_credits")
            ?: return FetchResult.Err("未找到 data.total_credits 字段")
        val usage = JsonPath.num(root, "data.total_usage") ?: 0.0
        return FetchResult.Ok(credits - usage, usage, "USD", body)
    }

    // 智谱现金余额（实测可用）：
    // {"code":200,"data":{"balance":18.319,"rechargeAmount":20.0,"totalSpendAmount":1.68,...}}
    private fun fetchZhipuCash(spec: FetchSpec): FetchResult {
        val base = spec.baseUrl.ifBlank { "https://bigmodel.cn" }
        val (code, body) = get(url(base, "/api/biz/account/query-customer-account-report"), spec)
        ensureHttp(code, body)
        val root = json.parseToJsonElement(body)
        val bizCode = JsonPath.num(root, "code")
        if (bizCode == null || bizCode != 200.0) {
            return FetchResult.Err("智谱接口错误 code=${bizCode?.toInt() ?: "?"}：${JsonPath.str(root, "msg") ?: "未知错误"}")
        }
        val balance = JsonPath.num(root, "data.balance")
            ?: return FetchResult.Err("未找到 data.balance 字段")
        val spend = JsonPath.num(root, "data.totalSpendAmount") ?: 0.0
        return FetchResult.Ok(balance, spend, "CNY", body)
    }

    // 智谱资源包：{"code":200,"rows":[{"availableBalance":5000000,"status":"EFFECTIVE",...}]}
    // 汇总所有生效资源包的剩余 Token（体验包/订阅包赠送额度，非现金）。
    private fun fetchZhipuPackages(spec: FetchSpec): FetchResult {
        val base = spec.baseUrl.ifBlank { "https://bigmodel.cn" }
        val (code, body) = get(url(base, "/api/biz/tokenAccounts/list/my?pageNum=1&pageSize=50"), spec)
        ensureHttp(code, body)
        val root = json.parseToJsonElement(body)
        val bizCode = JsonPath.num(root, "code")
        if (bizCode != null && bizCode != 200.0) {
            return FetchResult.Err("智谱接口错误 code=${bizCode.toInt()}：${JsonPath.str(root, "msg") ?: "未知错误"}")
        }
        val rows = JsonPath.get(root, "rows") as? kotlinx.serialization.json.JsonArray
            ?: kotlinx.serialization.json.JsonArray(emptyList())
        var sum = 0.0
        for (row in rows) {
            if ((JsonPath.str(row, "status") ?: continue) != "EFFECTIVE") continue
            sum += JsonPath.num(row, "availableBalance")
                ?: JsonPath.num(row, "tokenBalance")
                ?: continue
        }
        return FetchResult.Ok(sum, 0.0, "TOKEN", body)
    }

    // OpenAI 兼容计费接口（OneAPI / NewAPI / AiHubMix / 各类中转站）。
    //
    // 注意：OneAPI 系站点对按量付费账户的 subscription 接口返回 hard_limit_usd=1e8 的
    //「无限额度」占位值，直接相减会得到天文数字。因此优先调 /api/user/self
    //（普通令牌即可）取真实 quota，按 /api/status 的 quota_per_unit（默认 500000）换算美元；
    // 仅在 user/self 不可用时回退旧逻辑，且对 1e8 占位值报清晰错误而非显示 1 亿元。
    private fun fetchOpenAiBilling(spec: FetchSpec): FetchResult {
        val base = spec.baseUrl.trimEnd('/').removeSuffix("/v1")

        val self = runCatching { get("$base/api/user/self", spec) }.getOrNull()
        if (self != null && self.first == 200) {
            val parsed = runCatching { json.parseToJsonElement(self.second) }.getOrNull()
            val success = (parsed?.let { JsonPath.get(it, "success") } as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull
            if (parsed != null && success == true) {
                val perUnit = fetchQuotaPerUnit(base, spec)
                val quota = JsonPath.num(parsed, "data.quota")
                if (quota != null) {
                    val usedQuota = JsonPath.num(parsed, "data.used_quota") ?: 0.0
                    return FetchResult.Ok(quota / perUnit, usedQuota / perUnit, spec.currency, self.second)
                }
            }
        }

        val (subCode, subBody) = get("$base/v1/dashboard/billing/subscription", spec)
        ensureHttp(subCode, subBody)
        val subRoot = json.parseToJsonElement(subBody)
        val hard = JsonPath.num(subRoot, "hard_limit_usd")
            ?: JsonPath.num(subRoot, "system_hard_limit_usd")
            ?: return FetchResult.Err("未找到 hard_limit_usd 字段")
        if (hard >= 1e8) {
            return FetchResult.Err("站点返回「无限额度」占位值且不支持 /api/user/self，无法计算余额")
        }

        val start = LocalDate.now().withDayOfMonth(1).toString()
        val end = LocalDate.now().plusDays(1).toString()
        val (useCode, useBody) = get("$base/v1/dashboard/billing/usage?start_date=$start&end_date=$end", spec)
        val used = if (useCode == 200) {
            (JsonPath.num(json.parseToJsonElement(useBody), "total_usage") ?: 0.0) / 100.0
        } else 0.0

        return FetchResult.Ok((hard - used).coerceAtLeast(0.0), used, "USD", subBody)
    }

    /** OneAPI 系站点的 quota→USD 换算比率，默认 500000。 */
    private fun fetchQuotaPerUnit(base: String, spec: FetchSpec): Double =
        runCatching {
            val (code, body) = get("$base/api/status", spec)
            if (code == 200) {
                JsonPath.num(json.parseToJsonElement(body), "data.quota_per_unit") ?: 500000.0
            } else 500000.0
        }.getOrDefault(500000.0)

    // 自定义：完整 URL（支持 {{key}} 占位符）+ JSON 路径
    private fun fetchCustom(spec: FetchSpec): FetchResult {
        if (spec.baseUrl.isBlank()) return FetchResult.Err("请先填写接口地址")
        if (spec.jsonPath.isBlank()) return FetchResult.Err("请先填写 JSON 字段路径")
        val urlIn = spec.baseUrl.replace("{{key}}", spec.apiKey)
        val request = Request.Builder().url(urlIn).get()
        if (!spec.baseUrl.contains("{{key}}")) {
            when (spec.authMode) {
                "Bearer" -> request.header("Authorization", "Bearer ${spec.apiKey}")
                "X-API-Key" -> request.header("X-API-Key", spec.apiKey)
                // "无" -> 不带认证头
            }
        }
        val (code, body) = execute(request.build())
        ensureHttp(code, body)
        val root = json.parseToJsonElement(body)
        val value = JsonPath.num(root, spec.jsonPath)
            ?: return FetchResult.Err("路径「${spec.jsonPath}」未取到数字，请检查字段路径")
        return FetchResult.Ok(value, 0.0, spec.currency, body)
    }

    // ---------- 工具 ----------

    private fun url(base: String, path: String): String = base.trimEnd('/') + path

    private fun get(url: String, spec: FetchSpec): Pair<Int, String> {
        val request = Request.Builder().url(url).get()
            .header("Authorization", "Bearer ${spec.apiKey}")
            .build()
        return execute(request)
    }

    private fun execute(request: Request): Pair<Int, String> =
        client.newCall(request).execute().use { resp ->
            resp.code to (resp.body?.string().orEmpty())
        }

    private fun ensureHttp(code: Int, body: String) {
        when {
            code == 401 || code == 403 -> throw HttpError("API Key 无效或无权限（HTTP $code）")
            code == 404 -> throw HttpError("接口不存在（HTTP 404），请检查接口地址")
            code != 200 -> throw HttpError("HTTP $code：${body.take(120)}")
        }
    }

    private class HttpError(msg: String) : Exception(msg)
}
