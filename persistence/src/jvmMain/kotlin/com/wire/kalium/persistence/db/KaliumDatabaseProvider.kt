package com.wire.kalium.persistence.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.KaliumDatabase
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAOImpl
import com.wire.kalium.persistence.util.FileNameUtil
import java.io.File
import java.util.Properties

actual class KaliumDatabaseProvider(private val storePath: File) {

    private val dbName = FileNameUtil.appDBName()
    private val database: KaliumDatabase


    init {
        val databasePath = storePath.resolve(dbName)
        val databaseExists = databasePath.exists()

        // Make sure all intermediate directories exist
        storePath.mkdirs()

        val driver: SqlDriver = JdbcSqliteDriver(
            "jdbc:sqlite:${databasePath.absolutePath}",
            Properties(1).apply { put("foreign_keys", "true") })

        if (!databaseExists) {
            KaliumDatabase.Schema.create(driver)
        }

        database = KaliumDatabase(driver)
    }

    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = ServerConfigurationDAOImpl(database.serverConfigurationQueries)

    actual fun nuke(): Boolean {
        return storePath.resolve(dbName).delete()
    }
}
