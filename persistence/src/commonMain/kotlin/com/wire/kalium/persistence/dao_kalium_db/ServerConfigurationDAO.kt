package com.wire.kalium.persistence.dao_kalium_db

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.wire.kalium.persistence.ServerConfiguration
import com.wire.kalium.persistence.ServerConfigurationQueries
import com.wire.kalium.persistence.model.ServerConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class ServerConfigMapper() {
    fun toModel(serverConfiguration: ServerConfiguration) = with(serverConfiguration) {
        ServerConfigEntity(id, apiBaseUrl, accountBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title)
    }
}

interface ServerConfigurationDAO {
    fun deleteByTitle(id: String)
    fun insert(
        id: String,
        apiBaseUrl: String,
        accountBaseUrl: String,
        webSocketBaseUrl: String,
        blackListUrl: String,
        teamsUrl: String,
        websiteUrl: String,
        title: String
    )

    fun allConfigFlow(): Flow<List<ServerConfigEntity>>
    fun allConfig(): List<ServerConfigEntity>
    fun configByTitle(id: String): ServerConfigEntity?
}

class ServerConfigurationDAOImpl(private val queries: ServerConfigurationQueries) : ServerConfigurationDAO {
    private val mapper: ServerConfigMapper = ServerConfigMapper()

    override fun deleteByTitle(id: String) = queries.deleteById(id)

    override fun insert(
        id: String,
        apiBaseUrl: String,
        accountBaseUrl: String,
        webSocketBaseUrl: String,
        blackListUrl: String,
        teamsUrl: String,
        websiteUrl: String,
        title: String
    ) = queries.insert(id, apiBaseUrl, accountBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title)

    override fun allConfigFlow(): Flow<List<ServerConfigEntity>> =
        queries.storedConfig().asFlow().mapToList().map { it.map(mapper::toModel) }

    override fun allConfig(): List<ServerConfigEntity> = queries.storedConfig().executeAsList().map(mapper::toModel)

    override fun configByTitle(id: String): ServerConfigEntity? = queries.getById(id).executeAsOneOrNull()?.let { mapper.toModel(it) }

}
