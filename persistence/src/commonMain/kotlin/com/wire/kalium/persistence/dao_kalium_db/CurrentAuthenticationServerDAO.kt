package com.wire.kalium.persistence.dao_kalium_db

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.wire.kalium.persistence.CurrentAuthenticationServerQueries
import kotlinx.coroutines.flow.Flow

interface CurrentAuthenticationServerDAO {
    fun update(serverConfigId: String)
    fun currentConfigId(): String?
    fun currentConfigIdFlow(): Flow<String>
}

internal class CurrentAuthenticationServerDAOImpl(
    private val queries: CurrentAuthenticationServerQueries
): CurrentAuthenticationServerDAO {
    override fun update(serverConfigId: String) = queries.update(serverConfigId)

    override fun currentConfigId(): String? = queries.select().executeAsOneOrNull()

    override fun currentConfigIdFlow(): Flow<String> = queries.select().asFlow().mapToOne()
}
