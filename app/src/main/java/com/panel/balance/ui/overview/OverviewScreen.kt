package com.panel.balance.ui.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panel.balance.data.PlatformPresets
import com.panel.balance.data.ServiceLocator
import com.panel.balance.ui.common.PlatformAvatar
import com.panel.balance.ui.common.StatusDot
import com.panel.balance.ui.common.tnum
import com.panel.balance.util.formatMoney
import com.panel.balance.util.relativeTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 总览页：主平台渐变卡 + 平台余额列表（长按拖动排序、下拉刷新）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onAdd: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: OverviewViewModel = viewModel()
    val state by vm.state.collectAsState()

    val context = LocalContext.current
    val repo = remember { ServiceLocator.repo(context.applicationContext) }
    val haptics = LocalHapticFeedback.current

    // ---- 长按拖动排序状态 ----
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragDeltaY by remember { mutableStateOf(0f) }
    var dragOrderIds by remember { mutableStateOf<List<Long>?>(null) }
    var dragSession by remember { mutableStateOf(0) }
    val itemHeightsPx = remember { mutableMapOf<Long, Int>() }
    val reorderMutex = remember { Mutex() }
    val scope = rememberCoroutineScope()
    var reorderTail by remember { mutableStateOf<Job?>(null) }

    // 数据库 Flow 在拖动期间可能还没来得及发出新列表。用一个临时的 id 顺序
    // 立即更新界面，松手前的每次移动再按顺序串行写回数据库。
    val rowById = state.items.associateBy { it.account.id }
    val orderedRows = dragOrderIds
        ?.mapNotNull { rowById[it] }
        .orEmpty()
        .let { draggedRows ->
            if (dragOrderIds == null) {
                state.items
            } else {
                val draggedIds = draggedRows.map { it.account.id }.toSet()
                draggedRows + state.items.filter { it.account.id !in draggedIds }
            }
    }
    val moveAccount: (Long, Int) -> Unit = remember(repo, reorderMutex, scope) {
        moveHandler@{ id, dir ->
            val ids = dragOrderIds ?: return@moveHandler
            val index = ids.indexOf(id)
            val target = index + dir
            if (index < 0 || target !in ids.indices) return@moveHandler

            dragOrderIds = ids.toMutableList().apply {
                add(target, removeAt(index))
            }
            // Room 写入必须串行，否则快速拖动时多个协程会读取同一个旧列表。
            // 用 Job 链保证前一次移动完成后，下一次才读取数据库并交换。
            val previous = reorderTail
            reorderTail = scope.launch {
                previous?.join()
                reorderMutex.withLock { repo.move(id, dir) }
            }
        }
    }
    val moveHandler by rememberUpdatedState(moveAccount)
    val hapticsState by rememberUpdatedState(haptics)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                state = listState,
                userScrollEnabled = draggingId == null,
                contentPadding = PaddingValues(bottom = 100.dp),
            ) {
                item { Header(onAdd, onOpenSettings) }
                item {
                    Hero(
                        state = state,
                        primary = orderedRows.firstOrNull(),
                        onPrimaryClick = { id -> onOpenDetail(id) },
                        onRefresh = { vm.refresh() },
                    )
                }
                if (state.loaded && state.items.isEmpty()) {
                    item { EmptyState() }
                } else {
                    item { SectionLabel("其他平台余额") }
                    items(orderedRows.drop(1), key = { it.account.id }) { row ->
                        AccountCard(
                            row = row,
                            isDragging = draggingId == row.account.id,
                            dragDeltaY = dragDeltaY,
                            itemHeightsPx = itemHeightsPx,
                            currentRows = orderedRows,
                            moveHandler = moveHandler,
                            haptics = hapticsState,
                            onDragStateChange = { id ->
                                if (id != null) {
                                    dragSession++
                                    draggingId = id
                                    dragOrderIds = orderedRows.map { it.account.id }
                                } else {
                                    draggingId = null
                                    val session = dragSession
                                    val pending = reorderTail
                                    scope.launch {
                                        try {
                                            pending?.join()
                                        } finally {
                                            // 若用户已开始下一次拖动，不能清掉新拖动的临时顺序。
                                            if (dragSession == session && draggingId == null) {
                                                dragOrderIds = null
                                                reorderTail = null
                                            }
                                        }
                                    }
                                }
                            },
                            onDragDelta = { v -> dragDeltaY = v },
                            onClick = { onOpenDetail(row.account.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 顶栏：标题 + 添加平台 / 设置入口。 */
@Composable
private fun Header(onAdd: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("余额面板", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = "添加平台")
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = "设置")
        }
    }
}

/** 主平台渐变卡：展示列表首位账号的余额、币种、异常数与刷新时间。 */
@Composable
private fun Hero(
    state: OverviewUiState,
    primary: AccountRow?,
    onPrimaryClick: (Long) -> Unit,
    onRefresh: () -> Unit,
) {
    val gradient = Brush.linearGradient(
        listOf(Color(0xFF655BF0), Color(0xFF9A5CF5), Color(0xFFE163C8)),
        start = Offset.Zero,
        end = Offset(700f, 500f),
    )
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("主平台", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                if (primary != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        primary.account.name,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onPrimaryClick(primary.account.id) },
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(30.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                        .clickable(onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            val primaryState = primary?.state
            val primaryCurrency = primaryState?.currency
                ?.takeIf { it.isNotBlank() }
                ?: primary?.account?.currency
            Text(
                text = when {
                    primary == null -> "¥ 0"
                    primaryState == null || primaryState.refreshAt == 0L -> "待刷新"
                    else -> formatMoney(primaryState.balance, primaryCurrency ?: "CNY")
                },
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                style = tnum(LocalTextStyle.current),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (primary != null) {
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            "${PlatformPresets.byId(primary.account.platformId)?.name ?: "自定义平台"} · ${primaryCurrency ?: "CNY"}",
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                if (state.errorCount > 0) {
                    Surface(
                        color = Color(0x4DFF4D4D),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            "⚠ ${state.errorCount} 个异常",
                            color = Color(0xFFFFE1E1),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "更新于 ${relativeTime(state.lastRefresh)}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
            )
        }
    }
}

/** 单个平台余额卡片，支持长按拖动换位。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountCard(
    row: AccountRow,
    isDragging: Boolean,
    dragDeltaY: Float,
    itemHeightsPx: MutableMap<Long, Int>,
    currentRows: List<AccountRow>,
    moveHandler: (Long, Int) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onDragStateChange: (Long?) -> Unit,
    onDragDelta: (Float) -> Unit,
    onClick: () -> Unit,
) {
    val preset = PlatformPresets.byId(row.account.platformId)
    val st = row.state
    val displayCurrency = st?.currency?.takeIf { it.isNotBlank() } ?: row.account.currency
    val rowId = row.account.id
    // pointerInput(rowId) 在同一账号重新排序时不会重启，手势内部必须读取最新的
    // 列表和回调，否则第二次拖动仍会使用第一次组合时捕获的旧顺序。
    val latestRows by rememberUpdatedState(currentRows)
    val latestMoveHandler by rememberUpdatedState(moveHandler)
    val latestHaptics by rememberUpdatedState(haptics)
    val latestDragStateChange by rememberUpdatedState(onDragStateChange)
    val latestDragDelta by rememberUpdatedState(onDragDelta)
    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                if (isDragging) {
                    translationY = dragDeltaY
                    shadowElevation = 28f
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            }
            .onGloballyPositioned { itemHeightsPx[rowId] = it.size.height }
            .pointerInput(rowId) {
                var delta = 0f
                val dragOrder = mutableListOf<Long>()

                fun endDrag() {
                    latestDragStateChange(null)
                    latestDragDelta(0f)
                }

                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        delta = 0f
                        dragOrder.clear()
                        dragOrder += latestRows.map { it.account.id }
                        latestHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestDragStateChange(rowId)
                    },
                    onDragEnd = ::endDrag,
                    onDragCancel = ::endDrag,
                ) { change, dragAmount ->
                    // 官方手势识别器已经完成长按和拖动判定；消费事件以阻止 Card 点击。
                    change.consume()
                    delta += dragAmount.y
                    latestDragDelta(delta)

                    // 拖过相邻卡片一半高度时交换顺序。
                    val idx = dragOrder.indexOf(rowId)
                    if (idx == -1) return@detectDragGesturesAfterLongPress
                    if (idx > 0) {
                        val aboveId = dragOrder[idx - 1]
                        val hAbove = itemHeightsPx[aboveId] ?: 0
                        if (hAbove > 0 && delta < -hAbove / 2f) {
                            latestMoveHandler(rowId, -1)
                            dragOrder.add(idx - 1, dragOrder.removeAt(idx))
                            delta += hAbove
                            latestDragDelta(delta)
                            return@detectDragGesturesAfterLongPress
                        }
                    }
                    if (idx < dragOrder.lastIndex) {
                        val belowId = dragOrder[idx + 1]
                        val hBelow = itemHeightsPx[belowId] ?: 0
                        if (hBelow > 0 && delta > hBelow / 2f) {
                            latestMoveHandler(rowId, 1)
                            dragOrder.add(idx + 1, dragOrder.removeAt(idx))
                            delta -= hBelow
                            latestDragDelta(delta)
                        }
                    }
                }
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PlatformAvatar(preset?.name ?: row.account.name, row.account.color)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.account.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${preset?.name ?: "自定义平台"} · $displayCurrency",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (st != null && !st.ok && st.refreshAt > 0) {
                    Text(
                        st.error ?: "查询失败",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        // 从未成功过才显示待刷新；刷新失败时保留上次已知余额（陈旧但可参考）
                        st == null || st.refreshAt == 0L -> "待刷新"
                        else -> formatMoney(st.balance, displayCurrency)
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    style = tnum(LocalTextStyle.current),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StatusDot(st?.ok)
                    Text(
                        relativeTime(st?.refreshAt ?: 0),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 列表分组小标题。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
    )
}

/** 空态引导：还没有账号时提示去添加。 */
@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Savings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("还没有管理的平台", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "添加 DeepSeek、Kimi、硅基流动等平台\n自动监控余额变化",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "点击右上角 + 添加平台",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
