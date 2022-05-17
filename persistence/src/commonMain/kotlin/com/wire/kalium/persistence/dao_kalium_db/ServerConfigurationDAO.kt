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
        ServerConfigEntity(
            id,
            apiBaseUrl,
            accountBaseUrl,
            webSocketBaseUrl,
            blackListUrl,
            teamsUrl,
            websiteUrl,
            title,
            federation,
            commonApiVersion,
            domain
        )
    }
}

interface ServerConfigurationDAO {
    fun deleteById(id: String)
    @Suppress("LongParameterList")
    fun insert(
        id: String,
        apiBaseUrl: String,
        accountBaseUrl: String,
        webSocketBaseUrl: String,
        blackListUrl: String,
        teamsUrl: String,
        websiteUrl: String,
        title: String,
        federation: Boolean,
        domain: String?,
        commonApiVersion: Int
    )

    fun allConfigFlow(): Flow<List<ServerConfigEntity>>
    fun allConfig(): List<ServerConfigEntity>
    fun configById(id: String): ServerConfigEntity?
    fun updateApiVersion(id: String, commonApiVersion: Int)
    fun updateApiVersionAndDomain(id: String, domain: String, commonApiVersion: Int)
    fun setFederationToTrue(id: String)
}

class ServerConfigurationDAOImpl(private val queries: ServerConfigurationQueries) : ServerConfigurationDAO {
    private val mapper: ServerConfigMapper = ServerConfigMapper()

    override fun deleteById(id: String) = queries.deleteById(id)

    @Suppress("LongParameterList")
    override fun insert(
        id: String,
        apiBaseUrl: String,
        accountBaseUrl: String,
        webSocketBaseUrl: String,
        blackListUrl: String,
        teamsUrl: String,
        websiteUrl: String,
        title: String,
        federation: Boolean,
        domain: String?,
        commonApiVersion: Int
    ) = queries.insert(
        id,
        apiBaseUrl,
        accountBaseUrl,
        webSocketBaseUrl,
        blackListUrl,
        teamsUrl,
        websiteUrl,
        title,
        federation,
        domain,
        commonApiVersion
    )

    override fun allConfigFlow(): Flow<List<ServerConfigEntity>> =
        queries.storedConfig().asFlow().mapToList().map { it.map(mapper::toModel) }

    override fun allConfig(): List<ServerConfigEntity> = queries.storedConfig().executeAsList().map(mapper::toModel)

    override fun configById(id: String): ServerConfigEntity? = queries.getById(id).executeAsOneOrNull()?.let { mapper.toModel(it) }

    override fun updateApiVersion(id: String, commonApiVersion: Int) = queries.updateApiVersion(commonApiVersion, id)

    override fun updateApiVersionAndDomain(id: String, domain: String, commonApiVersion: Int) =
        queries.updateApiVersionAndDomain(commonApiVersion, domain, id)

    override fun setFederationToTrue(id: String) = queries.setFederationToTrue(id)

}
