package com.wire.kalium.persistence.daokaliumdb

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.wire.kalium.persistence.ServerConfigurationQueries
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.ServerConfigEntity
import kotlinx.coroutines.flow.Flow

@Suppress("FunctionParameterNaming", "LongParameterList")
internal object ServerConfigMapper {

    fun fromServerConfiguration(
        id: String,
        title: String,
        apiBaseUrl: String,
        accountBaseUrl: String,
        webSocketBaseUrl: String,
        blackListUrl: String,
        teamsUrl: String,
        websiteUrl: String,
        isOnPremises: Boolean,
        domain: String?,
        commonApiVersion: Int,
        federation: Boolean,
        proxyApi: String?,
        proxyNeedsAuthentication: Boolean?,
        proxyPort: Int?
    ): ServerConfigEntity = ServerConfigEntity(
        id,
        ServerConfigEntity.Links(
            api = apiBaseUrl,
            accounts = accountBaseUrl,
            webSocket = webSocketBaseUrl,
            blackList = blackListUrl,
            teams = teamsUrl,
            website = websiteUrl,
            title = title,
            isOnPremises = isOnPremises,
            proxy = if (proxyApi != null && proxyNeedsAuthentication != null && proxyPort != null) {
                ServerConfigEntity.Proxy(
                    needsAuthentication = proxyNeedsAuthentication,
                    proxyApi = proxyApi,
                    proxyPort = proxyPort
                )
            } else null
        ),
        ServerConfigEntity.MetaData(
            federation = federation,
            apiVersion = commonApiVersion,
            domain = domain
        )
    )
}

interface ServerConfigurationDAO {
    fun deleteById(id: String)
    fun insert(insertData: InsertData)
    fun allConfigFlow(): Flow<List<ServerConfigEntity>>
    fun allConfig(): List<ServerConfigEntity>
    fun configById(id: String): ServerConfigEntity?
    fun configByLinks(title: String, apiBaseUrl: String, webSocketBaseUrl: String): ServerConfigEntity?
    fun updateApiVersion(id: String, commonApiVersion: Int)
    fun updateApiVersionAndDomain(id: String, domain: String, commonApiVersion: Int)
    fun configForUser(userId: UserIDEntity): ServerConfigEntity?
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
        val isOnPremises: Boolean,
        val federation: Boolean,
        val domain: String?,
        val commonApiVersion: Int,
        val proxyApi: String?,
        val proxyNeedsAuthentication: Boolean?,
        val proxyPort: Int?
    )
}

internal class ServerConfigurationDAOImpl internal constructor(
    private val queries: ServerConfigurationQueries,
    private val mapper: ServerConfigMapper = ServerConfigMapper
) :
    ServerConfigurationDAO {

    override fun deleteById(id: String) = queries.deleteById(id)

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
            isOnPremises,
            federation,
            domain,
            commonApiVersion,
            proxyApi,
            proxyNeedsAuthentication,
            proxyPort
        )
    }

    override fun allConfigFlow(): Flow<List<ServerConfigEntity>> =
        queries.storedConfig(mapper = mapper::fromServerConfiguration).asFlow().mapToList()

    override fun allConfig(): List<ServerConfigEntity> =
        queries.storedConfig(mapper = mapper::fromServerConfiguration).executeAsList()

    override fun configById(id: String): ServerConfigEntity? =
        queries.getById(id, mapper = mapper::fromServerConfiguration).executeAsOneOrNull()

    override fun configByLinks(title: String, apiBaseUrl: String, webSocketBaseUrl: String): ServerConfigEntity? =
        queries.getByLinks(title, apiBaseUrl, webSocketBaseUrl, mapper = mapper::fromServerConfiguration)
            .executeAsOneOrNull()

    override fun updateApiVersion(id: String, commonApiVersion: Int) = queries.updateApiVersion(commonApiVersion, id)

    override fun updateApiVersionAndDomain(id: String, domain: String, commonApiVersion: Int) =
        queries.updateApiVersionAndDomain(commonApiVersion, domain, id)

    override fun configForUser(userId: UserIDEntity): ServerConfigEntity? =
        queries.getByUser(userId, mapper = mapper::fromServerConfiguration).executeAsOneOrNull()

    override fun setFederationToTrue(id: String) = queries.setFederationToTrue(id)

}
