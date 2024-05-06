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
import platform.Foundation.NSFileManager
import kotlin.coroutines.CoroutineContext

// TODO(refactor): Unify creation just like it's done for UserDataBase
actual class GlobalDatabaseProvider(
    private val storePath: String,
    private val queriesContext: CoroutineContext = KaliumDispatcherImpl.io
) {
    private val dbName = FileNameUtil.globalDBName()
    private val database: GlobalDatabase

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(storePath, true, null, null)
        val schema = GlobalDatabase.Schema
        val driver = DriverBuilder().withWALEnabled(false)
            .build(driverUri = storePath, dbName = dbName, schema = schema)

        database = GlobalDatabase(
            driver,
            AccountsAdapter = Accounts.Adapter(QualifiedIDAdapter, LogoutReasonAdapter, EnumColumnAdapter()),
            CurrentAccountAdapter = CurrentAccount.Adapter(QualifiedIDAdapter),
            ServerConfigurationAdapter = ServerConfiguration.Adapter(
                commonApiVersionAdapter = IntColumnAdapter,
                apiProxyPortAdapter = IntColumnAdapter
            )
        )

        database.globalDatabasePropertiesQueries.enableForeignKeyContraints()
    }

    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = ServerConfigurationDAOImpl(database.serverConfigurationQueries, queriesContext)

    actual val accountsDAO: AccountsDAO
        get() = AccountsDAOImpl(database.accountsQueries, database.currentAccountQueries, queriesContext)

    actual fun nuke(): Boolean {
        return NSFileManager.defaultManager.removeItemAtPath(storePath, null)
    }

}
