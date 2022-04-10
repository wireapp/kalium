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
        ServerConfigEntity(apiBaseUrl, accountBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title)
    }
}

interface ServerConfigurationDAO {
    fun deleteById(id: Int)
    fun insert(serverConfigEntity: ServerConfigEntity): ServerConfigEntity
    fun allConfigFlow(): Flow<List<ServerConfigEntity>>
    fun allConfig(): List<ServerConfigEntity>
    fun configById(id: Int): ServerConfigEntity?
}

class ServerConfigurationDAOImpl(private val queries: ServerConfigurationQueries) : ServerConfigurationDAO {
    private val mapper: ServerConfigMapper = ServerConfigMapper()

    override fun deleteById(id: Int) = queries.deleteById(id.toLong())

    override fun insert(serverConfigEntity: ServerConfigEntity): ServerConfigEntity = with(serverConfigEntity) {
        queries.insert(apiBaseUrl, accountBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title)
    }.let { serverConfigEntity }

    override fun allConfigFlow(): Flow<List<ServerConfigEntity>> =
        queries.storedConfig().asFlow().mapToList().map { it.map(mapper::toModel) }

    override fun allConfig(): List<ServerConfigEntity> = queries.storedConfig().executeAsList().map(mapper::toModel)

    override fun configById(id: Int): ServerConfigEntity? = queries.getById(id.toLong()).executeAsOneOrNull()?.let { mapper.toModel(it) }

}
