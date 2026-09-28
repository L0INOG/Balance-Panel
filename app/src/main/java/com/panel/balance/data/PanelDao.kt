package com.panel.balance.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 账号、余额状态、历史快照三张表的数据访问。 */
@Dao
interface PanelDao {

    // ---------- accounts ----------

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    suspend fun allAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun account(id: Long): AccountEntity?

    @Insert
    suspend fun insertAccount(a: AccountEntity): Long

    @Update
    suspend fun updateAccount(a: AccountEntity)

    @Query("UPDATE accounts SET sortOrder = :sort WHERE id = :id")
    suspend fun setSort(id: Long, sort: Int)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM account_state")
    suspend fun clearStates()

    @Query("DELETE FROM balance_records")
    suspend fun clearRecords()

    // ---------- account_state ----------

    @Query("SELECT * FROM account_state")
    fun observeStates(): Flow<List<AccountStateEntity>>

    @Query("SELECT * FROM account_state")
    suspend fun allStates(): List<AccountStateEntity>

    @Query("SELECT * FROM account_state WHERE accountId = :id")
    suspend fun state(id: Long): AccountStateEntity?

    @Query("SELECT MAX(refreshAt) FROM account_state")
    suspend fun latestRefresh(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(s: AccountStateEntity)

    @Query("DELETE FROM account_state WHERE accountId = :id")
    suspend fun deleteState(id: Long)

    // ---------- balance_records ----------

    @Insert
    suspend fun insertRecord(r: BalanceRecordEntity)

    @Query("SELECT * FROM balance_records WHERE accountId = :id ORDER BY time DESC LIMIT 1")
    suspend fun latestRecord(id: Long): BalanceRecordEntity?

    @Query("UPDATE balance_records SET time = :time, balance = :balance, used = :used WHERE id = :id")
    suspend fun updateRecordValues(id: Long, time: Long, balance: Double, used: Double)

    @Query("SELECT * FROM balance_records WHERE accountId = :id ORDER BY time DESC LIMIT :limit")
    suspend fun records(id: Long, limit: Int): List<BalanceRecordEntity>

    @Query("DELETE FROM balance_records WHERE accountId = :id")
    suspend fun deleteRecords(id: Long)

    // 每个账号最多保留 200 条快照
    @Query(
        "DELETE FROM balance_records WHERE accountId = :id AND id NOT IN " +
            "(SELECT id FROM balance_records WHERE accountId = :id ORDER BY time DESC LIMIT 200)"
    )
    suspend fun pruneRecords(id: Long)
}
