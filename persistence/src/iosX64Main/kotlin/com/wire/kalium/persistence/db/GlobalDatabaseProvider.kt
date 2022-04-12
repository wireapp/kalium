package com.wire.kalium.persistence.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAOImpl
import com.wire.kalium.persistence.util.FileNameUtil

actual class GlobalDatabaseProvider(passphrase: String) {

    val database: GlobalDatabase

    init {
        val driver = NativeSqliteDriver(GlobalDatabase.Schema, FileNameUtil.globalDBName())
        database = GlobalDatabase(driver)

        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    }

    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = ServerConfigurationDAOImpl(database.serverConfigurationQueries)

    actual fun nuke(): Boolean {
        TODO("Not yet implemented")
    }
}
