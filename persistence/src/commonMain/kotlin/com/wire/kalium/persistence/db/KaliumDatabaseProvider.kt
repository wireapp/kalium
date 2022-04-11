package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO

expect class KaliumDatabaseProvider {
    val serverConfigurationDAO: ServerConfigurationDAO

    fun nuke(): Boolean
}
