package com.panel.balance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.panel.balance.data.AccountEntity
import com.panel.balance.data.AppDatabase
import com.panel.balance.data.ServiceLocator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PanelApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class PanelRepoSortTest {

    private val dao get() = AppDatabase.get(ApplicationProvider.getApplicationContext<Context>()).dao()

    @Before
    fun clearDatabase() {
        runBlocking {
            dao.clearAccounts()
            dao.clearStates()
            dao.clearRecords()
        }
    }

    @Test
    fun moveNormalizesLegacyDuplicateSortOrders() = runBlocking {
        val a = dao.insertAccount(account("A"))
        val b = dao.insertAccount(account("B"))
        val c = dao.insertAccount(account("C"))

        ServiceLocator.repo(ApplicationProvider.getApplicationContext()).move(a, 1)

        assertEquals(listOf(b, a, c), dao.allAccounts().map { it.id })
        assertEquals(listOf(0, 1, 2), dao.allAccounts().map { it.sortOrder })
    }

    @Test
    fun newAccountIsAppendedAfterExistingOrder() = runBlocking {
        val a = dao.insertAccount(account("A"))
        val b = dao.insertAccount(account("B"))
        ServiceLocator.repo(ApplicationProvider.getApplicationContext()).move(b, -1)

        val c = ServiceLocator.repo(ApplicationProvider.getApplicationContext()).addAccount(account("C"))

        assertEquals(listOf(b, a, c), dao.allAccounts().map { it.id })
        assertEquals(listOf(0, 1, 2), dao.allAccounts().map { it.sortOrder })
    }

    private fun account(name: String) = AccountEntity(
        name = name,
        platformId = "deepseek",
        kind = "DEEPSEEK",
        color = 0xFF4D6BFE,
    )
}
