package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao_kalium_db.CurrentAuthenticationServerDAO
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO

expect class GlobalDatabaseProvider {
    val serverConfigurationDAO: ServerConfigurationDAO
    val currentAuthenticationServerDAO: CurrentAuthenticationServerDAO

    fun nuke(): Boolean
}
