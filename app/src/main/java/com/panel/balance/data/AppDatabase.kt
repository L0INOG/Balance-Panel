package com.panel.balance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room 数据库：accounts / account_state / balance_records 三张表，单例持有。 */
@Database(
    entities = [AccountEntity::class, AccountStateEntity::class, BalanceRecordEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PanelDao

    companion object {
        /** v2：account_state 增加 currency 列（记录接口返回的真实币种）。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE account_state ADD COLUMN currency TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v3：移除手动记录功能——清退手动账号及其数据，
         * accounts 表去掉 autoQuery / manualBalance 两列（SQLite 删列需重建表）。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM balance_records WHERE accountId IN (SELECT id FROM accounts WHERE kind = 'MANUAL')")
                db.execSQL("DELETE FROM account_state WHERE accountId IN (SELECT id FROM accounts WHERE kind = 'MANUAL')")
                db.execSQL("DELETE FROM accounts WHERE kind = 'MANUAL'")
                db.execSQL(
                    """
                    CREATE TABLE accounts_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        platformId TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        color INTEGER NOT NULL,
                        apiKeyEnc TEXT NOT NULL,
                        baseUrl TEXT NOT NULL,
                        currency TEXT NOT NULL,
                        customJsonPath TEXT NOT NULL,
                        customAuthHeader TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO accounts_new
                        SELECT id, name, platformId, kind, color, apiKeyEnc, baseUrl, currency,
                               customJsonPath, customAuthHeader, sortOrder, createdAt
                        FROM accounts
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE accounts")
                db.execSQL("ALTER TABLE accounts_new RENAME TO accounts")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "panel.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
