package com.panel.balance.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * 极简 JSON 路径取值：支持点号与数组下标。
 * 例：data.balance_infos[0].total_balance
 */
object JsonPath {

    fun get(root: JsonElement, path: String): JsonElement? {
        val tokens = path
            .replace("[", ".")
            .replace("]", "")
            .split('.')
            .filter { it.isNotBlank() }
        var cur: JsonElement = root
        for (t in tokens) {
            cur = when (cur) {
                is JsonObject -> cur[t] ?: return null
                is JsonArray -> t.toIntOrNull()?.let { idx -> cur.getOrNull(idx) } ?: return null
                else -> return null
            }
        }
        return cur
    }

    fun str(el: JsonElement, path: String): String? {
        val v = get(el, path) as? JsonPrimitive ?: return null
        return v.content.takeIf { it != "null" }
    }

    fun num(el: JsonElement, path: String): Double? {
        val v = get(el, path) as? JsonPrimitive ?: return null
        return v.doubleOrNull ?: v.content.toDoubleOrNull()
    }
}
