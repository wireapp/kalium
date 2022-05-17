package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao_kalium_db.CurrentAuthenticationServerDAO
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO

actual class GlobalDatabaseProvider {
    actual val serverConfigurationDAO: ServerConfigurationDAO
        get() = TODO("Not yet implemented")

    actual fun nuke(): Boolean {
        TODO("Not yet implemented")
    }

    actual val currentAuthenticationServerDAO: CurrentAuthenticationServerDAO
        get() = TODO("Not yet implemented")

}
