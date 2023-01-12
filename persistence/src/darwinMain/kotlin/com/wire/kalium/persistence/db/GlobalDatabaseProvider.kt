package com.wire.kalium.persistence.db

import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import com.wire.kalium.persistence.Accounts
import com.wire.kalium.persistence.CurrentAccount
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.ServerConfiguration
import com.wire.kalium.persistence.dao.QualifiedIDAdapter
import com.wire.kalium.persistence.daokaliumdb.AccountsDAO
import com.wire.kalium.persistence.daokaliumdb.AccountsDAOImpl
import com.wire.kalium.persistence.daokaliumdb.LogoutReasonAdapter
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAOImpl
import com.wire.kalium.persistence.util.FileNameUtil
import platform.Foundation.NSFileManager

// TODO(refactor): Unify creation just like it's done for UserDataBase
actual class GlobalDatabaseProvider(private val storePath: String) {
    private val dbName = FileNameUtil.globalDBName()
    private val database: GlobalDatabase

    init {
        println("global store path: $storePath")

        NSFileManager.defaultManager.createDirectoryAtPath(storePath, true, null, null)

        val schema = GlobalDatabase.Schema
        val driver = NativeSqliteDriver(
            DatabaseConfiguration(
                name = dbName,
                version = schema.version,
                create = { connection ->
                    wrapConnection(connection) { schema.create(it) }
                },
                upgrade = { connection, oldVersion, newVersion ->
                    wrapConnection(connection) { schema.migrate(it, oldVersion, newVersion) }
                },
                extendedConfig = DatabaseConfiguration.Extended(
                    basePath = storePath,
                    foreignKeyConstraints = true
                )
            )
        )

        database = GlobalDatabase(
            driver,
            AccountsAdapter = Accounts.Adapter(QualifiedIDAdapter, LogoutReasonAdapter),
            CurrentAccountAdapter = CurrentAccount.Adapter(QualifiedIDAdapter),
            ServerConfigurationAdapter = ServerConfiguration.Adapter(
                commonApiVersionAdapter = IntColumnAdapter,
                apiProxyPortAdapter = IntColumnAdapter
            )
        )

        database.globalDatabasePropertiesQueries.enableForeignKeyContraints()
    }

    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = ServerConfigurationDAOImpl(database.serverConfigurationQueries)

    actual val accountsDAO: AccountsDAO
        get() = AccountsDAOImpl(database.accountsQueries, database.currentAccountQueries)

    actual fun nuke(): Boolean {
        return NSFileManager.defaultManager.removeItemAtPath(storePath, null)
    }

}
