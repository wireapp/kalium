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
import app.cash.sqldelight.db.SqlDriver
import com.wire.kalium.persistence.Accounts
import com.wire.kalium.persistence.CurrentAccount
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.ServerConfiguration
import com.wire.kalium.persistence.adapter.BooleanAdapter
import com.wire.kalium.persistence.adapter.IntAdapterAdapter
import com.wire.kalium.persistence.adapter.LogoutReasonAdapter
import com.wire.kalium.persistence.adapter.QualifiedIDAdapter
import com.wire.kalium.persistence.daokaliumdb.AccountsDAO
import com.wire.kalium.persistence.daokaliumdb.AccountsDAOImpl
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAOImpl
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmInline

@JvmInline
value class GlobalDatabaseSecret(val value: ByteArray)

class GlobalDatabaseBuilder internal constructor(
    private val sqlDriver: SqlDriver,
    private val platformDatabaseData: PlatformDatabaseData,
    private val queriesContext: CoroutineContext = KaliumDispatcherImpl.io
):WireUserDb {

    internal val database: GlobalDatabase = GlobalDatabase(
        sqlDriver,
        ServerConfigurationAdapter = ServerConfiguration.Adapter(
            commonApiVersionAdapter = IntAdapterAdapter,
            apiProxyPortAdapter = IntAdapterAdapter,
            apiProxyNeedsAuthenticationAdapter = BooleanAdapter,
            isOnPremisesAdapter = BooleanAdapter,
            federationAdapter = BooleanAdapter
        ),
        AccountsAdapter = Accounts.Adapter(
            idAdapter = QualifiedIDAdapter,
            logout_reasonAdapter = LogoutReasonAdapter,
            managed_byAdapter = EnumColumnAdapter(),
            isPersistentWebSocketEnabledAdapter = BooleanAdapter
        ),
        CurrentAccountAdapter = CurrentAccount.Adapter(
            user_idAdapter = QualifiedIDAdapter
        )
    )

    init {
        database.globalDatabasePropertiesQueries.enableForeignKeyContraints()
    }

    override val serverConfigurationDAO: ServerConfigurationDAO
        get() = ServerConfigurationDAOImpl(database.serverConfigurationQueries, queriesContext)

    override val accountsDAO: AccountsDAO
        get() = AccountsDAOImpl(database.accountsQueries, database.currentAccountQueries, queriesContext)

    fun nuke(): Boolean {
        sqlDriver.close()
        return nuke(platformDatabaseData)
    }
}

expect fun nuke(platformDatabaseData: PlatformDatabaseData): Boolean

/**
 * Creates a new instance of the [GlobalDatabaseBuilder].
 * @param platformDatabaseData the platform specific database data.
 * @param queriesContext the context in which the queries will be executed.
 * @param passphrase the passphrase to use for the database encryption.
 * @param enableWAL whether to enable Write-Ahead Logging.
 * @param encryptionEnabled whether to enable encryption.
 *
 * @return a new instance of the [GlobalDatabaseBuilder].
 */
expect fun pgGlobalDatabaseProvider(
    platformDatabaseData: PlatformDatabaseData,
    queriesContext: CoroutineDispatcher = KaliumDispatcherImpl.io,
    passphrase: GlobalDatabaseSecret?,
    enableWAL: Boolean = false,
): GlobalDatabaseBuilder
