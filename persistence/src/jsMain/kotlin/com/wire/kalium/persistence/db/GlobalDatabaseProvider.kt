package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.daoKaliumDB.AccountsDAO
import com.wire.kalium.persistence.daoKaliumDB.ServerConfigurationDAO

actual class GlobalDatabaseProvider {
    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = TODO("Not yet implemented")

    actual fun nuke(): Boolean {
        TODO("Not yet implemented")
    }

    actual val accountsDAO: AccountsDAO
        get() = TODO("Not yet implemented")

}
