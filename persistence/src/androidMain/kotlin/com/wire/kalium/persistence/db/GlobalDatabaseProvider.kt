package com.wire.kalium.persistence.db

import android.content.Context
import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.wire.kalium.persistence.Accounts
import com.wire.kalium.persistence.CurrentAccount
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.ServerConfiguration
import com.wire.kalium.persistence.adapter.QualifiedIDAdapter
import com.wire.kalium.persistence.daokaliumdb.AccountsDAO
import com.wire.kalium.persistence.daokaliumdb.AccountsDAOImpl
import com.wire.kalium.persistence.adapter.LogoutReasonAdapter
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAOImpl
import com.wire.kalium.persistence.util.FileNameUtil
import net.sqlcipher.database.SupportFactory

// TODO(refactor): Unify creation just like it's done for UserDataBase
actual class GlobalDatabaseProvider(private val context: Context, passphrase: GlobalDatabaseSecret, encrypt: Boolean = true) {
    private val dbName = FileNameUtil.globalDBName()
    private val driver: AndroidSqliteDriver
    private val database: GlobalDatabase

    init {
        driver = if (encrypt) {
            AndroidSqliteDriver(
                schema = GlobalDatabase.Schema,
                context = context,
                name = dbName,
                factory = SupportFactory(passphrase.value)
            )
        } else {
            AndroidSqliteDriver(
                schema = GlobalDatabase.Schema,
                context = context,
                name = dbName
            )
        }

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
        driver.close()
        return context.deleteDatabase(dbName)
    }

}
