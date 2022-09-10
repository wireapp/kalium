package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao_kalium_db.AccountsDAO
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import kotlin.jvm.JvmInline

@JvmInline
value class GlobalDatabaseSecret(val value: ByteArray)

expect class GlobalDatabaseProvider {
    val serverConfigurationDAO: ServerConfigurationDAO
    val accountsDAO: AccountsDAO

    fun nuke(): Boolean
}
