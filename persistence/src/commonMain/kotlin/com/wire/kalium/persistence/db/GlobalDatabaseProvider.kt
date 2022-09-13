package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.daoKaliumDB.AccountsDAO
import com.wire.kalium.persistence.daoKaliumDB.ServerConfigurationDAO
import kotlin.jvm.JvmInline

@JvmInline
value class GlobalDatabaseSecret(val value: ByteArray)

expect class GlobalDatabaseProvider {
    val serverConfigurationDAO: ServerConfigurationDAO
    val accountsDAO: AccountsDAO

    fun nuke(): Boolean
}
