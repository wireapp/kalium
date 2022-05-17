package com.wire.kalium.persistence.dao_kalium_db

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.wire.kalium.persistence.CurrentAuthenticationServerQueries
import kotlinx.coroutines.flow.Flow

interface CurrentAuthenticationServerDAO {
    fun update(serverConfigId: String)
    fun select(): String?
    fun selectFlow(): Flow<String>
}

internal class CurrentAuthenticationServerDAOImpl(
    private val queries: CurrentAuthenticationServerQueries
): CurrentAuthenticationServerDAO {
    override fun update(serverConfigId: String) = queries.update(serverConfigId)

    override fun select(): String? = queries.select().executeAsOneOrNull()

    override fun selectFlow(): Flow<String> = queries.select().asFlow().mapToOne()
}
