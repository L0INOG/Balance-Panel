package com.panel.balance.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panel.balance.data.AccountEntity
import com.panel.balance.data.AccountStateEntity
import com.panel.balance.data.BalanceRecordEntity
import com.panel.balance.data.CryptoStore
import com.panel.balance.data.PlatformPresets
import com.panel.balance.data.ServiceLocator
import com.panel.balance.net.FetchKind
import com.panel.balance.ui.common.StatusDot
import com.panel.balance.ui.common.lighten
import com.panel.balance.ui.common.tnum
import com.panel.balance.util.formatDateTime
import com.panel.balance.util.formatMoney
import com.panel.balance.util.relativeTime
import kotlinx.coroutines.launch

/** 账号详情页：当前余额卡、立即刷新、余额趋势图与历史记录。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ServiceLocator.repo(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var row by remember { mutableStateOf<DetailRow?>(null) }
    var records by remember { mutableStateOf<List<BalanceRecordEntity>>(emptyList()) }
    var tick by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id, tick) {
        row = repo.accountRow(id)?.let { DetailRow(it.first, it.second) }
        records = repo.recordsFor(id, 30)
    }

    val acc = row?.account
    val st = row?.state

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        acc?.name ?: "详情",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        var menuOpen by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("编辑") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = { menuOpen = false; showEdit = true },
                            )
                            DropdownMenuItem(
                                text = { Text("上移") },
                                leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, null) },
                                onClick = { menuOpen = false; scope.launch { repo.move(id, -1); tick++ } },
                            )
                            DropdownMenuItem(
                                text = { Text("下移") },
                                leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null) },
                                onClick = { menuOpen = false; scope.launch { repo.move(id, 1); tick++ } },
                            )
                            DropdownMenuItem(
                                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = { menuOpen = false; showDelete = true },
                            )
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (acc == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val preset = PlatformPresets.byId(acc.platformId)
        val displayCurrency = st?.currency?.takeIf { it.isNotBlank() } ?: acc.currency

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            // ---- 头部余额卡 ----
            val c = Color(acc.color)
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(lighten(c, 0.3f), c)))
                    .padding(20.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("当前余额", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text(
                                preset?.name ?: "自定义平台",
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (st != null && st.refreshAt > 0) {
                            formatMoney(st.balance, displayCurrency)
                        } else {
                            "待刷新"
                        },
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        style = tnum(LocalTextStyle.current),
                    )
                    if (st != null && st.used > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "已用 ${formatMoney(st.used, displayCurrency)}",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(st?.ok)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            relativeTime(st?.refreshAt ?: 0),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                        )
                        if (st != null && !st.ok && st.refreshAt > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                st.error ?: "查询失败",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // ---- 操作按钮 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            refreshing = true
                            runCatching { repo.refreshOne(id) }
                            tick++
                            refreshing = false
                        }
                    },
                    enabled = !refreshing,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("立即刷新")
                }
            }

            // ---- 趋势图 ----
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("余额趋势", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    if (records.size >= 2) {
                        BalanceLineChart(
                            values = records.map { it.balance.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                formatDateTime(records.first().time),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatDateTime(records.last().time),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Box(
                            Modifier.fillMaxWidth().height(110.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "刷新几次后即可查看余额趋势",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ---- 历史记录 ----
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("历史记录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    if (records.isEmpty()) {
                        Text(
                            "暂无记录",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val desc = records.asReversed().take(20)
                        desc.forEachIndexed { i, r ->
                            val older = desc.getOrNull(i + 1)
                            val delta = if (older != null) r.balance - older.balance else null
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(formatDateTime(r.time), fontSize = 13.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        formatMoney(r.balance, displayCurrency),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        style = tnum(LocalTextStyle.current),
                                    )
                                    if (delta != null && kotlin.math.abs(delta) > 0.0001) {
                                        val up = delta > 0
                                        Text(
                                            "${if (up) "▲" else "▼"} ${formatMoney(kotlin.math.abs(delta), displayCurrency)}",
                                            fontSize = 10.sp,
                                            color = if (up) Color(0xFF2FA46A) else Color(0xFFD64550),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- 对话框 ----
        if (showEdit) {
            EditAccountDialog(
                account = acc,
                onDismiss = { showEdit = false },
                onSave = { updated ->
                    scope.launch {
                        repo.updateAccount(updated)
                        showEdit = false
                        tick++
                    }
                },
            )
        }
        if (showDelete) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text("删除账号") },
                text = { Text("确认删除「${acc.name}」？该账号的历史记录也会一并删除。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDelete = false
                            scope.launch {
                                repo.deleteAccount(id)
                                onBack()
                            }
                        },
                    ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDelete = false }) { Text("取消") }
                },
            )
        }
    }
}

/** 详情页使用的快照包装。 */
private data class DetailRow(
    val account: AccountEntity,
    val state: AccountStateEntity?,
)

/** 编辑账号对话框：改名、换 Key（留空保持不变）、改接口地址与 JSON 路径。 */
@Composable
private fun EditAccountDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onSave: (AccountEntity) -> Unit,
) {
    val isCustom = account.kind == FetchKind.CUSTOM_JSON.name

    var name by remember(account.id) { mutableStateOf(account.name) }
    var apiKey by remember(account.id) { mutableStateOf("") }
    var baseUrl by remember(account.id) { mutableStateOf(account.baseUrl) }
    var jsonPath by remember(account.id) { mutableStateOf(account.customJsonPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账号") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key（留空保持不变）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("接口地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isCustom) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = jsonPath,
                        onValueChange = { jsonPath = it },
                        label = { Text("JSON 字段路径") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        account.copy(
                            name = name.trim().ifBlank { account.name },
                            apiKeyEnc = if (apiKey.isBlank()) account.apiKeyEnc else CryptoStore.protect(apiKey.trim()),
                            baseUrl = baseUrl.trim(),
                            customJsonPath = jsonPath.trim(),
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
