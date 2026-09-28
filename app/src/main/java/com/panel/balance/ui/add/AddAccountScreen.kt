package com.panel.balance.ui.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.balance.data.AccountEntity
import com.panel.balance.data.CryptoStore
import com.panel.balance.data.PlatformPreset
import com.panel.balance.data.PlatformPresets
import com.panel.balance.data.ServiceLocator
import com.panel.balance.net.FetchKind
import com.panel.balance.ui.common.PlatformAvatar
import kotlinx.coroutines.launch

/** 添加平台：先选预设，再填账号信息。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ServiceLocator.repo(context.applicationContext) }

    var preset by remember { mutableStateOf<PlatformPreset?>(null) }
    val current = preset

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (current == null) "添加平台" else current.name) },
                navigationIcon = {
                    IconButton(onClick = { if (current == null) onDone() else preset = null }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (current == null) {
            PresetPicker(Modifier.padding(padding), onPick = { preset = it })
        } else {
            AccountForm(
                preset = current,
                modifier = Modifier.padding(padding),
                onCancel = { preset = null },
                onSave = { account ->
                    scope.launch {
                        repo.addAccount(account)
                        onDone()
                    }
                },
            )
        }
    }
}

// ---------- 第一步：选择平台预设 ----------

/** 平台预设网格，每行两个卡片。 */
@Composable
private fun PresetPicker(modifier: Modifier, onPick: (PlatformPreset) -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        PlatformPresets.all.chunked(2).forEach { rowItems ->
            Row(Modifier.padding(horizontal = 16.dp)) {
                rowItems.forEach { p ->
                    Box(Modifier.weight(1f).padding(horizontal = 5.dp, vertical = 5.dp)) {
                        PresetTile(p, onClick = { onPick(p) })
                    }
                }
                // 奇数个平台时补齐右半格，保持卡片宽度一致
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 单个平台卡片：头像 + 平台名。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetTile(p: PlatformPreset, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            PlatformAvatar(p.name, p.color, 36.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                p.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------- 第二步：填写账号信息 ----------

/** 账号表单：名称 / API Key / 接口地址 /（自定义接口的）JSON 路径与认证方式 / 币种。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountForm(
    preset: PlatformPreset,
    modifier: Modifier,
    onCancel: () -> Unit,
    onSave: (AccountEntity) -> Unit,
) {
    val isCustom = preset.kind == FetchKind.CUSTOM_JSON

    var name by remember { mutableStateOf(preset.name) }
    var apiKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf(preset.baseUrl) }
    var jsonPath by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf("Bearer") }
    var currency by remember { mutableStateOf(preset.currency) }

    // 智谱现金/资源包的币种由接口决定并锁定；其余可选 CNY/USD
    val currencyOptions = when (preset.kind) {
        FetchKind.ZHIPU -> listOf("CNY")
        FetchKind.ZHIPU_PACKAGES -> listOf("TOKEN")
        else -> listOf("CNY", "USD")
    }

    // 接口地址里带 {{key}} 占位符时密钥已在 URL 中，API Key 输入框可留空
    val keyOptional = isCustom && baseUrl.contains("{{key}}")
    val baseOk = baseUrl.isNotBlank() || preset.baseUrl.isNotBlank()
    val canSave = name.isNotBlank() &&
        baseOk && (keyOptional || apiKey.isNotBlank()) && (!isCustom || jsonPath.isNotBlank())

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("平台名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(if (keyOptional) "API Key（已通过 URL 占位符提供则可留空）" else "API Key") },
            singleLine = true,
            visualTransformation =
                if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        if (keyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = {
                Text(
                    when {
                        isCustom -> "接口地址（支持 {{key}} 占位符）"
                        preset.kind == FetchKind.OPENAI_BILLING -> "中转站地址，如 https://api.example.com"
                        else -> "接口地址"
                    }
                )
            },
            supportingText = {
                if (!isCustom && preset.kind != FetchKind.OPENAI_BILLING) {
                    Text("默认 ${preset.baseUrl}，如无需要可留空")
                } else if (isCustom) {
                    Text("完整余额查询接口 URL，密钥认证方式见下方")
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (isCustom) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = jsonPath,
                onValueChange = { jsonPath = it },
                label = { Text("余额字段 JSON 路径") },
                supportingText = { Text("示例：data.balance_infos[0].total_balance") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text("认证方式", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                listOf("Bearer", "X-API-Key", "无", "URL").forEach { m ->
                    FilterChip(
                        selected = authMode == m,
                        onClick = { authMode = m },
                        label = { Text(if (m == "URL") "URL 占位符" else m) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("币种", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            currencyOptions.forEach { c ->
                FilterChip(
                    selected = currency == c,
                    onClick = { currency = c },
                    label = { Text(when (c) {
                        "CNY" -> "CNY (¥)"
                        "USD" -> "USD ($)"
                        else -> "TOKEN（资源包额度）"
                    }) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row {
            Button(
                onClick = {
                    onSave(
                        AccountEntity(
                            name = name.trim().ifBlank { preset.name },
                            platformId = preset.id,
                            kind = preset.kind.name,
                            color = preset.color,
                            apiKeyEnc = CryptoStore.protect(apiKey.trim()),
                            baseUrl = baseUrl.trim(),
                            currency = currency,
                            customJsonPath = jsonPath.trim(),
                            customAuthHeader = authMode,
                        )
                    )
                },
                enabled = canSave,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text("保存", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onCancel,
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.weight(0.5f).height(52.dp),
            ) {
                Text("取消")
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}
