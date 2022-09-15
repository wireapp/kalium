package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.daokaliumdb.AccountsDAO
import com.wire.kalium.persistence.daokaliumdb.MLSPublicKeysDAO
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import kotlin.jvm.JvmInline

@JvmInline
value class GlobalDatabaseSecret(val value: ByteArray)

expect class GlobalDatabaseProvider {
    val serverConfigurationDAO: ServerConfigurationDAO
    val accountsDAO: AccountsDAO
    val mlsPublicKeysDAO: MLSPublicKeysDAO

    fun nuke(): Boolean
}
