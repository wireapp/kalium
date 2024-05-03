/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.db

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.Accounts
import com.wire.kalium.persistence.CurrentAccount
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.ServerConfiguration
import com.wire.kalium.persistence.adapter.LogoutReasonAdapter
import com.wire.kalium.persistence.adapter.QualifiedIDAdapter
import com.wire.kalium.persistence.daokaliumdb.AccountsDAO
import com.wire.kalium.persistence.daokaliumdb.AccountsDAOImpl
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAOImpl
import com.wire.kalium.persistence.util.FileNameUtil
import com.wire.kalium.util.KaliumDispatcherImpl
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteOpenMode
import java.io.File
import kotlin.coroutines.CoroutineContext


// TODO(refactor): Unify creation just like it's done for UserDataBase
actual class GlobalDatabaseProvider(
    private val storePath: File,
    private val queriesContext: CoroutineContext = KaliumDispatcherImpl.io,
    private val useInMemoryDatabase: Boolean = false
) {

    private val dbName = FileNameUtil.globalDBName()
    private val database: GlobalDatabase

    init {
        val driver = if (useInMemoryDatabase) {
            buildInMemoryDb()
        } else {
            buildFileBackedDb()
        }

        database = GlobalDatabase(
            driver,
            ServerConfigurationAdapter = ServerConfiguration.Adapter(
                commonApiVersionAdapter = IntColumnAdapter,
                apiProxyPortAdapter = IntColumnAdapter
            ),
            AccountsAdapter = Accounts.Adapter(
                idAdapter = QualifiedIDAdapter,
                logout_reasonAdapter = LogoutReasonAdapter,
                managed_byAdapter = EnumColumnAdapter()
            ),
            CurrentAccountAdapter = CurrentAccount.Adapter(
                user_idAdapter = QualifiedIDAdapter
            )
        )

        database.globalDatabasePropertiesQueries.enableForeignKeyContraints()
    }

    private fun buildFileBackedDb(): SqlDriver {
        val databasePath = storePath.resolve(dbName)
        val databaseExists = databasePath.exists()

        // Make sure all intermediate directories exist
        storePath.mkdirs()

        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${databasePath.absolutePath}")

        if (!databaseExists) {
            GlobalDatabase.Schema.create(driver)
        }
        return driver
    }

    private fun buildInMemoryDb(): SqlDriver {
        val config = SQLiteConfig()
        config.setOpenMode(SQLiteOpenMode.READWRITE)
        config.setOpenMode(SQLiteOpenMode.CREATE)
        config.setOpenMode(SQLiteOpenMode.NOMUTEX)
        config.setTransactionMode(SQLiteConfig.TransactionMode.EXCLUSIVE)
        config.setJournalMode(SQLiteConfig.JournalMode.MEMORY)
        config.toProperties()

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, config.toProperties())
        GlobalDatabase.Schema.create(driver)
        return driver
    }

    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = ServerConfigurationDAOImpl(database.serverConfigurationQueries, queriesContext)

    actual val accountsDAO: AccountsDAO
        get() = AccountsDAOImpl(database.accountsQueries, database.currentAccountQueries, queriesContext)

    actual fun nuke(): Boolean {
        return storePath.resolve(dbName).delete()
    }
}
