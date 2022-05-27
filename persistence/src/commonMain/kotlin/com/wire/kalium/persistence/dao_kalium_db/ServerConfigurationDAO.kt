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
            ServerConfigEntity.Links(
                api = apiBaseUrl,
                accounts = accountBaseUrl,
                webSocket = webSocketBaseUrl,
                blackList = blackListUrl,
                teams = teamsUrl,
                website = websiteUrl,
                title = title
            ),
            ServerConfigEntity.MetaData(
                federation = federation,
                apiVersion = commonApiVersion,
                domain = domain
            )
        )
    }
}

interface ServerConfigurationDAO {
    fun deleteById(id: String)
    fun insert(insertData: InsertData)
    fun allConfigFlow(): Flow<List<ServerConfigEntity>>
    fun allConfig(): List<ServerConfigEntity>
    fun configById(id: String): ServerConfigEntity?
    fun configByUniqueFields(title: String, apiBaseUrl: String, webSocketBaseUrl: String, domain: String?): ServerConfigEntity?
    fun configByLinks(title: String, apiBaseUrl: String, webSocketBaseUrl: String): ServerConfigEntity?
    fun updateApiVersion(id: String, commonApiVersion: Int)
    fun updateApiVersionAndDomain(id: String, domain: String, commonApiVersion: Int)
    fun setFederationToTrue(id: String)

    data class InsertData(
        val id: String,
        val apiBaseUrl: String,
        val accountBaseUrl: String,
        val webSocketBaseUrl: String,
        val blackListUrl: String,
        val teamsUrl: String,
        val websiteUrl: String,
        val title: String,
        val federation: Boolean,
        val domain: String?,
        val commonApiVersion: Int
    )
}

class ServerConfigurationDAOImpl(private val queries: ServerConfigurationQueries) : ServerConfigurationDAO {
    private val mapper: ServerConfigMapper = ServerConfigMapper()

    override fun deleteById(id: String) = queries.deleteById(id)

    @Suppress("LongParameterList")
    override fun insert(
        insertData: ServerConfigurationDAO.InsertData
    ) = with(insertData) {
        queries.insert(
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
    }

    override fun allConfigFlow(): Flow<List<ServerConfigEntity>> =
        queries.storedConfig().asFlow().mapToList().map { it.map(mapper::toModel) }

    override fun allConfig(): List<ServerConfigEntity> = queries.storedConfig().executeAsList().map(mapper::toModel)

    override fun configById(id: String): ServerConfigEntity? = queries.getById(id).executeAsOneOrNull()?.let { mapper.toModel(it) }

    override fun configByUniqueFields(title: String, apiBaseUrl: String, webSocketBaseUrl: String, domain: String?) =
        queries.getByUniqueFields(title, apiBaseUrl, webSocketBaseUrl, domain).executeAsOneOrNull()?.let { mapper.toModel(it) }

    override fun configByLinks(title: String, apiBaseUrl: String, webSocketBaseUrl: String): ServerConfigEntity? =
        queries.getByLinks(title, apiBaseUrl, webSocketBaseUrl).executeAsOneOrNull()?.let { mapper.toModel(it) }

    override fun updateApiVersion(id: String, commonApiVersion: Int) = queries.updateApiVersion(commonApiVersion, id)

    override fun updateApiVersionAndDomain(id: String, domain: String, commonApiVersion: Int) =
        queries.updateApiVersionAndDomain(commonApiVersion, domain, id)

    override fun setFederationToTrue(id: String) = queries.setFederationToTrue(id)

}
