package com.panel.balance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.panel.balance.data.AccountEntity
import com.panel.balance.data.AccountStateEntity
import com.panel.balance.data.AppDatabase
import com.panel.balance.data.BalanceRecordEntity
import com.panel.balance.data.ServiceLocator
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import java.io.File

/**
 * 截图测试：用 Robolectric 原生图形渲染真实 Compose 界面并输出 PNG，
 * 用于视觉审查与 UI 打磨。
 * 运行：gradlew testDebugUnitTest --tests "com.panel.balance.ScreenshotTest"
 * 输出：app/build/screenshots 目录下的 PNG 文件
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PanelApp::class, qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val dao get() = AppDatabase.get(ApplicationProvider.getApplicationContext<Context>()).dao()

    private fun now() = System.currentTimeMillis()

    @Before
    fun seed() {
        val d = dao
        kotlinx.coroutines.runBlocking {
            // 每个测试方法可能复用同一个数据库文件，先清空再播种
            d.clearAccounts()
            d.clearStates()
            d.clearRecords()
            val deepseek = d.insertAccount(
                AccountEntity(
                    name = "DeepSeek 主力号", platformId = "deepseek", kind = "DEEPSEEK",
                    color = 0xFF4D6BFE, baseUrl = "https://api.deepseek.com", currency = "CNY",
                    sortOrder = 0,
                )
            )
            val kimi = d.insertAccount(
                AccountEntity(
                    name = "Kimi 开发者", platformId = "moonshot", kind = "MOONSHOT",
                    color = 0xFF1E293B, baseUrl = "https://api.moonshot.cn", currency = "CNY",
                    sortOrder = 1,
                )
            )
            val silicon = d.insertAccount(
                AccountEntity(
                    name = "硅基流动", platformId = "siliconflow", kind = "SILICONFLOW",
                    color = 0xFF6C47FF, baseUrl = "https://api.siliconflow.cn", currency = "CNY",
                    sortOrder = 2,
                )
            )
            val openrouter = d.insertAccount(
                AccountEntity(
                    name = "OpenRouter", platformId = "openrouter", kind = "OPENROUTER",
                    color = 0xFF5E6AD2, baseUrl = "https://openrouter.ai", currency = "USD",
                    sortOrder = 3,
                )
            )
            val glm = d.insertAccount(
                AccountEntity(
                    name = "智谱 GLM", platformId = "zhipu", kind = "ZHIPU",
                    color = 0xFF3859FF, baseUrl = "https://bigmodel.cn", currency = "CNY",
                    sortOrder = 4,
                )
            )
            val glmTok = d.insertAccount(
                AccountEntity(
                    name = "智谱资源包", platformId = "zhipu_tokens", kind = "ZHIPU_PACKAGES",
                    color = 0xFF3B82F6, baseUrl = "https://bigmodel.cn", currency = "TOKEN",
                    sortOrder = 5,
                )
            )
            val bad = d.insertAccount(
                AccountEntity(
                    name = "中转站 A", platformId = "oneapi", kind = "OPENAI_BILLING",
                    color = 0xFF0EA5E9, currency = "CNY", sortOrder = 6,
                )
            )

            d.upsertState(AccountStateEntity(deepseek, 128.45, 61.55, true, null, now() - 3 * 60_000, "CNY"))
            d.upsertState(AccountStateEntity(kimi, 56.20, 43.80, true, null, now() - 40 * 60_000, "CNY"))
            d.upsertState(AccountStateEntity(silicon, 89.60, 10.40, true, null, now() - 2 * 3_600_000, "CNY"))
            d.upsertState(AccountStateEntity(openrouter, 12.34, 7.66, true, null, now() - 5 * 3_600_000, "USD"))
            d.upsertState(AccountStateEntity(glm, 18.32, 1.68, true, null, now() - 7 * 3_600_000, "CNY"))
            d.upsertState(AccountStateEntity(glmTok, 23_000_120.0, 0.0, true, null, now() - 7 * 3_600_000, "TOKEN"))
            // 刷新失败但保留上次已知余额（¥88.00，小组件中以琥珀色提示陈旧）
            d.upsertState(
                AccountStateEntity(bad, 88.0, 0.0, false, "API Key 无效或无权限（HTTP 401）", now() - 25 * 60_000, "CNY")
            )

            // DeepSeek 的历史曲线：先降后升
            val history = listOf(150.0, 138.2, 120.4, 98.9, 85.3, 92.6, 108.9, 121.4, 128.45)
            val start = now() - history.size * 6 * 3_600_000L
            history.forEachIndexed { i, v ->
                d.insertRecord(BalanceRecordEntity(accountId = deepseek, time = start + i * 6 * 3_600_000L, balance = v))
            }
        }
    }

    private fun openAddScreen() {
        compose.onNodeWithContentDescription("添加平台").performClick()
    }

    @Test
    fun s01_overview() {
        compose.waitForIdle()
        capture("01_overview")
    }

    @Test
    fun s02_add_picker() {
        compose.waitForIdle()
        openAddScreen()
        compose.waitForIdle()
        capture("02_add_picker")
    }

    @Test
    fun s03_add_form_auto() {
        compose.waitForIdle()
        openAddScreen()
        compose.waitForIdle()
        compose.onNodeWithText("DeepSeek").performClick()
        compose.waitForIdle()
        capture("03_add_form_auto")
    }

    @Test
    fun s05_detail() {
        compose.waitForIdle()
        compose.onNodeWithText("DeepSeek 主力号").performClick()
        compose.waitForIdle()
        capture("05_detail")
    }

    @Test
    fun s06_settings() {
        compose.waitForIdle()
        compose.onNodeWithContentDescription("设置").performClick()
        compose.waitForIdle()
        capture("06_settings")
    }

    @Test
    fun s07_add_save_flow() {
        // 功能回归：自动平台表单填写 + 保存后总览联动
        compose.waitForIdle()
        openAddScreen()
        compose.waitForIdle()
        compose.onNodeWithText("DeepSeek").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("API Key").performTextInput("sk-test-key")
        compose.waitForIdle()
        capture("07_add_form_filled")
        // 取消仅回到选择页（不触发导航弹栈，Robolectric 下导航弹栈有线程限制）
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("DeepSeek").assertExists()
        // 仓库层走真实保存路径，验证落盘 + 总览联动
        kotlinx.coroutines.runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            ServiceLocator.repo(context).addAccount(
                AccountEntity(
                    name = "DeepSeek 备用号", platformId = "deepseek", kind = "DEEPSEEK",
                    color = 0xFF4D6BFE, currency = "CNY",
                )
            )
        }
        // 在主线程上触发系统返回，回到总览页（此时新卡片应已出现）
        compose.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        // 新增账号按排序规则追加到列表末尾，卡片可能不在首屏。
        compose.onNode(androidx.compose.ui.test.hasScrollAction(), true)
            .performScrollToNode(androidx.compose.ui.test.hasText("DeepSeek 备用号"))
        compose.waitForIdle()
        compose.onNodeWithText("DeepSeek 备用号").assertExists()
        compose.waitForIdle()
        capture("08_overview_after_save")
    }

    private fun capture(name: String) {
        val activity = compose.activity
        val decor = activity.window.decorView
        val width = decor.width.coerceAtLeast(1)
        val height = decor.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()
    }
}
