package com.wire.kalium.persistence.db

import app.cash.sqldelight.adapter.primitive.IntColumnAdapter
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.wire.kalium.persistence.GlobalDatabase
import com.wire.kalium.persistence.ServerConfiguration
import com.wire.kalium.persistence.dao_kalium_db.CurrentAuthenticationServerDAO
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAOImpl
import com.wire.kalium.persistence.util.FileNameUtil

actual class GlobalDatabaseProvider(passphrase: String) {

    val database: GlobalDatabase

    init {
        val driver = NativeSqliteDriver(GlobalDatabase.Schema, FileNameUtil.globalDBName())
        database = GlobalDatabase(
            driver, ServerConfiguration.Adapter(
                commonApiVersionAdapter = IntColumnAdapter
            )
        )

        driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    }

    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = ServerConfigurationDAOImpl(database.serverConfigurationQueries)

    actual val currentAuthenticationServerDAO: CurrentAuthenticationServerDAO
        get() = TODO("Not yet implemented")

    actual fun nuke(): Boolean {
        TODO("Not yet implemented")
    }
}
