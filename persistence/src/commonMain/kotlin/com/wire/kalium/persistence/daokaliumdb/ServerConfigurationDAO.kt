package com.wire.kalium.persistence.daokaliumdb

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.wire.kalium.persistence.ServerConfigurationQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.ServerConfigWithUserIdEntity
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
        apiProxyHost: String?,
        apiProxyNeedsAuthentication: Boolean?,
        apiProxyPort: Int?,
        lastBlackListCheck: String?
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
            apiProxy = if (apiProxyHost != null && apiProxyNeedsAuthentication != null && apiProxyPort != null) {
                ServerConfigEntity.ApiProxy(
                    needsAuthentication = apiProxyNeedsAuthentication,
                    host = apiProxyHost,
                    port = apiProxyPort
                )
            } else null
        ),
        ServerConfigEntity.MetaData(
            federation = federation,
            apiVersion = commonApiVersion,
            domain = domain
        )
    )

    fun serverConfigWithAccId(
        id: String?,
        title: String?,
        apiBaseUrl: String?,
        accountBaseUrl: String?,
        webSocketBaseUrl: String?,
        blackListUrl: String?,
        teamsUrl: String?,
        websiteUrl: String?,
        isOnPremises: Boolean?,
        domain: String?,
        commonApiVersion: Int?,
        federation: Boolean?,
        apiProxyHost: String?,
        apiProxyNeedsAuthentication: Boolean?,
        apiProxyPort: Int?,
        lastBlackListCheck: String?,
        id_: QualifiedIDEntity,
    ): ServerConfigWithUserIdEntity = ServerConfigWithUserIdEntity(
        userId = id_,
        serverConfig = fromServerConfiguration(
            id = id!!,
            title = title!!,
            apiBaseUrl = apiBaseUrl!!,
            accountBaseUrl = accountBaseUrl!!,
            webSocketBaseUrl = webSocketBaseUrl!!,
            blackListUrl = blackListUrl!!,
            teamsUrl = teamsUrl!!,
            websiteUrl = websiteUrl!!,
            isOnPremises = isOnPremises!!,
            domain = domain,
            commonApiVersion = commonApiVersion!!,
            federation = federation!!,
            apiProxyHost = apiProxyHost,
            apiProxyNeedsAuthentication = apiProxyNeedsAuthentication,
            apiProxyPort = apiProxyPort,
            lastBlackListCheck = lastBlackListCheck
        ),
    )
}

interface ServerConfigurationDAO {
    fun deleteById(id: String)
    fun insert(insertData: InsertData)
    fun allConfigFlow(): Flow<List<ServerConfigEntity>>
    fun allConfig(): List<ServerConfigEntity>
    fun configById(id: String): ServerConfigEntity?
    fun configByLinks(links: ServerConfigEntity.Links): ServerConfigEntity?
    fun updateApiVersion(id: String, commonApiVersion: Int)
    fun updateApiVersionAndDomain(id: String, domain: String, commonApiVersion: Int)
    fun configForUser(userId: UserIDEntity): ServerConfigEntity?
    fun setFederationToTrue(id: String)
    fun getServerConfigsWithAccIdWithLastCheckBeforeDate(date: String): Flow<List<ServerConfigWithUserIdEntity>>
    fun updateBlackListCheckDate(configIds: Set<String>, date: String)

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
        val apiProxyHost: String?,
        val apiProxyNeedsAuthentication: Boolean?,
        val apiProxyPort: Int?
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
            apiProxyHost,
            apiProxyNeedsAuthentication,
            apiProxyPort
        )
    }

    override fun allConfigFlow(): Flow<List<ServerConfigEntity>> =
        queries.storedConfig(mapper = mapper::fromServerConfiguration).asFlow().mapToList()

    override fun allConfig(): List<ServerConfigEntity> =
        queries.storedConfig(mapper = mapper::fromServerConfiguration).executeAsList()

    override fun configById(id: String): ServerConfigEntity? =
        queries.getById(id, mapper = mapper::fromServerConfiguration).executeAsOneOrNull()

    override fun configByLinks(links: ServerConfigEntity.Links): ServerConfigEntity? = with(links) {
        queries.getByLinks(
            apiBaseUrl = api,
            webSocketBaseUrl = webSocket,
            title = title,
            api_proxy_host = apiProxy?.host,
            api_proxy_port = apiProxy?.port,
            mapper = mapper::fromServerConfiguration
        )
    }.executeAsOneOrNull()

    override fun updateApiVersion(id: String, commonApiVersion: Int) = queries.updateApiVersion(commonApiVersion, id)

    override fun updateApiVersionAndDomain(id: String, domain: String, commonApiVersion: Int) =
        queries.updateApiVersionAndDomain(commonApiVersion, domain, id)

    override fun configForUser(userId: UserIDEntity): ServerConfigEntity? =
        queries.getByUser(userId, mapper = mapper::fromServerConfiguration).executeAsOneOrNull()

    override fun setFederationToTrue(id: String) = queries.setFederationToTrue(id)

    override fun getServerConfigsWithAccIdWithLastCheckBeforeDate(date: String): Flow<List<ServerConfigWithUserIdEntity>> =
        queries.getServerConfigsWithAccIdWithLastCheckBeforeDate(date, mapper::serverConfigWithAccId).asFlow().mapToList()

    override fun updateBlackListCheckDate(configIds: Set<String>, date: String) =
        queries.updateLastBlackListCheckByIds(date, configIds)

}
