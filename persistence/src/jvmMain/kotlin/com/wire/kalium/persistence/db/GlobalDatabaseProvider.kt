package com.wire.kalium.persistence.db

import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.Accounts
import com.wire.kalium.persistence.CurrentAccount
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.ServerConfiguration
import com.wire.kalium.persistence.dao.QualifiedIDAdapter
import com.wire.kalium.persistence.daoKaliumDB.AccountsDAO
import com.wire.kalium.persistence.daoKaliumDB.AccountsDAOImpl
import com.wire.kalium.persistence.daoKaliumDB.LogoutReasonAdapter
import com.wire.kalium.persistence.daoKaliumDB.ServerConfigurationDAO
import com.wire.kalium.persistence.daoKaliumDB.ServerConfigurationDAOImpl
import com.wire.kalium.persistence.util.FileNameUtil
import java.io.File
import java.util.Properties

actual class GlobalDatabaseProvider(private val storePath: File) {

    private val dbName = FileNameUtil.globalDBName()
    private val database: GlobalDatabase

    init {
        val databasePath = storePath.resolve(dbName)
        val databaseExists = databasePath.exists()

        // Make sure all intermediate directories exist
        storePath.mkdirs()

        val driver: SqlDriver = JdbcSqliteDriver(
            "jdbc:sqlite:${databasePath.absolutePath}",
            Properties(1).apply { put("foreign_keys", "true") })

        if (!databaseExists) {
            GlobalDatabase.Schema.create(driver)
        }

        database = GlobalDatabase(
            driver,
            ServerConfigurationAdapter = ServerConfiguration.Adapter(
                commonApiVersionAdapter = IntColumnAdapter
            ),
            AccountsAdapter = Accounts.Adapter(
                idAdapter = QualifiedIDAdapter,
                logoutReasonAdapter = LogoutReasonAdapter
            ),
            CurrentAccountAdapter = CurrentAccount.Adapter(
                user_idAdapter = QualifiedIDAdapter
            )
        )
    }

    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = ServerConfigurationDAOImpl(database.serverConfigurationQueries)

    actual val accountsDAO: AccountsDAO
        get() = AccountsDAOImpl(database.accountsQueries, database.currentAccountQueries)
    actual fun nuke(): Boolean {
        return storePath.resolve(dbName).delete()
    }
}
