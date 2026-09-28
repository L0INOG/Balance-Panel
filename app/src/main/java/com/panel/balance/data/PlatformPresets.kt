package com.panel.balance.data

import com.panel.balance.net.FetchKind

/** 平台预设：可通过接口自动查询余额的平台目录。 */
data class PlatformPreset(
    val id: String,
    val name: String,
    val color: Long,
    val kind: FetchKind,
    val baseUrl: String = "",
    val currency: String = "CNY",
)

object PlatformPresets {

    val all: List<PlatformPreset> = listOf(
        PlatformPreset(
            "deepseek", "DeepSeek", 0xFF4D6BFE, FetchKind.DEEPSEEK,
            "https://api.deepseek.com",
        ),
        PlatformPreset(
            "moonshot", "Kimi · 月之暗面", 0xFF1E293B, FetchKind.MOONSHOT,
            "https://api.moonshot.cn",
        ),
        PlatformPreset(
            "siliconflow", "硅基流动 SiliconFlow", 0xFF6C47FF, FetchKind.SILICONFLOW,
            "https://api.siliconflow.cn",
        ),
        PlatformPreset(
            "openrouter", "OpenRouter", 0xFF5E6AD2, FetchKind.OPENROUTER,
            "https://openrouter.ai", "USD",
        ),
        PlatformPreset(
            "zhipu", "智谱 GLM", 0xFF3859FF, FetchKind.ZHIPU,
            "https://bigmodel.cn",
        ),
        PlatformPreset(
            "zhipu_tokens", "智谱资源包", 0xFF3B82F6, FetchKind.ZHIPU_PACKAGES,
            "https://bigmodel.cn", "TOKEN",
        ),
        PlatformPreset(
            "oneapi", "中转站 / NewAPI", 0xFF0EA5E9, FetchKind.OPENAI_BILLING,
        ),
        PlatformPreset(
            "aihubmix", "AiHubMix", 0xFF2563EB, FetchKind.OPENAI_BILLING,
            "https://api.aihubmix.com", "USD",
        ),
        PlatformPreset(
            "custom", "自定义接口", 0xFF7C3AED, FetchKind.CUSTOM_JSON,
        ),
    )

    private val byId = all.associateBy { it.id }

    /** 按平台 id 查找预设，未命中（如旧数据）返回 null。 */
    fun byId(id: String): PlatformPreset? = byId[id]
}
