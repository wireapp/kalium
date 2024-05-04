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

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import com.wire.kalium.persistence.Accounts
import com.wire.kalium.persistence.CurrentAccount
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.ServerConfiguration
import com.wire.kalium.persistence.adapter.CrossCompatBooleanAdapter
import com.wire.kalium.persistence.adapter.LogoutReasonAdapter
import com.wire.kalium.persistence.adapter.QualifiedIDAdapter
import com.wire.kalium.persistence.dao.BooleanEntity
import com.wire.kalium.persistence.daokaliumdb.AccountsDAO
import com.wire.kalium.persistence.daokaliumdb.AccountsDAOImpl
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAOImpl
import com.wire.kalium.persistence.util.FileNameUtil
import com.wire.kalium.util.KaliumDispatcherImpl
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import javax.sql.DataSource
import kotlin.coroutines.CoroutineContext

// TODO(refactor): Unify creation just like it's done for UserDataBase
actual class GlobalDatabaseProvider(
    private val storePath: File,
    private val queriesContext: CoroutineContext = KaliumDispatcherImpl.io
) {

    private val dbName = FileNameUtil.globalDBName()
    private val database: GlobalDatabase

    init {
        val databasePath = storePath.resolve(dbName)
        val databaseExists = databasePath.exists()

        // Make sure all intermediate directories exist
        storePath.mkdirs()

        val driver: SqlDriver = createDataSource("jdbc:postgresql://localhost:5432/$dbName").asJdbcDriver()

//         if (!databaseExists) {
            GlobalDatabase.Schema.create(driver)
//         }

        database = GlobalDatabase(
            driver,
            ServerConfigurationAdapter = ServerConfiguration.Adapter(
                commonApiVersionAdapter = IntColumnAdapter,
                isOnPremisesAdapter = CrossCompatBooleanAdapter as ColumnAdapter<BooleanEntity, Long>,
                apiProxyPortAdapter = IntColumnAdapter,
                federationAdapter = CrossCompatBooleanAdapter as ColumnAdapter<BooleanEntity, Long>,
                apiProxyNeedsAuthenticationAdapter = CrossCompatBooleanAdapter as ColumnAdapter<BooleanEntity, Long>
            ),
            AccountsAdapter = Accounts.Adapter(
                idAdapter = QualifiedIDAdapter,
                logout_reasonAdapter = LogoutReasonAdapter,
                managed_byAdapter = EnumColumnAdapter(),
                isPersistentWebSocketEnabledAdapter = CrossCompatBooleanAdapter as ColumnAdapter<BooleanEntity, Long>
            ),
            CurrentAccountAdapter = CurrentAccount.Adapter(
                user_idAdapter = QualifiedIDAdapter
            )
        )

        //database.globalDatabasePropertiesQueries.enableForeignKeyContraints()
    }

    private fun createDataSource(driverUri: String): DataSource {
        val dataSourceConfig = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver" // todo. parameterize
            jdbcUrl = driverUri
            username = "global"
            password = "global"
            maximumPoolSize = 3
            isAutoCommit = true
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(dataSourceConfig)
    }

    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = ServerConfigurationDAOImpl(database.serverConfigurationQueries, queriesContext)

    actual val accountsDAO: AccountsDAO
        get() = AccountsDAOImpl(database.accountsQueries, database.currentAccountQueries, queriesContext)

    actual fun nuke(): Boolean {
        return storePath.resolve(dbName).delete()
    }
}
