package com.wire.kalium.persistence.db

import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.ServerConfiguration
import com.wire.kalium.persistence.dao_kalium_db.CurrentAuthenticationServerDAO
import com.wire.kalium.persistence.dao_kalium_db.CurrentAuthenticationServerDAOImpl
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAOImpl
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
            ServerConfiguration.Adapter(
                commonApiVersionAdapter = IntColumnAdapter
            )
        )
    }

    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = ServerConfigurationDAOImpl(database.serverConfigurationQueries)

    actual val currentAuthenticationServerDAO: CurrentAuthenticationServerDAO
        get() = CurrentAuthenticationServerDAOImpl(database.currentAuthenticationServerQueries)
    actual fun nuke(): Boolean {
        return storePath.resolve(dbName).delete()
    }
}
