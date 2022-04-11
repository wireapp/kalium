package com.wire.kalium.persistence.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.wire.kalium.persistence.KaliumDatabase
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAOImpl
import com.wire.kalium.persistence.util.FileNameUtil

actual class KaliumDatabaseProvider(passphrase: String) {

    val database: KaliumDatabase

    init {
        val driver = NativeSqliteDriver(KaliumDatabase.Schema, FileNameUtil.appDBName())
        database = KaliumDatabase(driver)

        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    }

    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = ServerConfigurationDAOImpl(database.serverConfigurationQueries)

    actual fun nuke(): Boolean {
        TODO("Not yet implemented")
    }
}
