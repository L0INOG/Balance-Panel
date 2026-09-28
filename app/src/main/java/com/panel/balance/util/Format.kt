package com.panel.balance.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val df2 = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
private val df4 = DecimalFormat("#,##0.####", DecimalFormatSymbols(Locale.US))
private val dfTrim = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
private val df0 = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))

/** 币种符号：CNY→¥、USD→$，其余原样返回并补空格（如 "TOKEN "）。 */
fun currencySymbol(currency: String): String = when (currency.uppercase()) {
    "CNY", "RMB" -> "¥"
    "USD" -> "$"
    else -> currency.uppercase() + " "
}

/** Token 额度的中文习惯显示：5,000,000 → 500万 */
fun formatTokens(v: Double): String = when {
    v >= 1e8 -> dfTrim.format(v / 1e8) + " 亿"
    v >= 1e4 -> dfTrim.format(v / 1e4) + " 万"
    else -> df0.format(v)
}

/** 金额格式化：TOKEN 走中文万/亿缩写；小于 0.01 的非零值保留 4 位小数避免显示成 0.00。 */
fun formatMoney(v: Double, currency: String, withSymbol: Boolean = true): String {
    if (currency.equals("TOKEN", ignoreCase = true)) return formatTokens(v)
    val n = if (v != 0.0 && abs(v) < 0.01) df4.format(v) else df2.format(v)
    if (!withSymbol) return n
    val sym = currencySymbol(currency)
    return if (v < 0) "-$sym${df2.format(abs(v))}" else sym + n
}

/** 相对时间：刚刚 / n 分钟前 / n 小时前 / n 天前；ts<=0 表示从未刷新。 */
fun relativeTime(ts: Long): String {
    if (ts <= 0) return "未刷新"
    val d = System.currentTimeMillis() - ts
    return when {
        d < 60_000 -> "刚刚"
        d < 3_600_000 -> "${d / 60_000} 分钟前"
        d < 86_400_000 -> "${d / 3_600_000} 小时前"
        else -> "${d / 86_400_000} 天前"
    }
}

/** 历史记录用的时间格式（MM-dd HH:mm）。 */
fun formatDateTime(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(ts))
