/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.persistence.daokaliumdb

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.model.ServerConfigWithUserIdEntity
import kotlinx.coroutines.flow.Flow

@Suppress("FunctionParameterNaming", "LongParameterList")
object ServerConfigMapper {

    @Suppress("UNUSED_PARAMETER")
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
    suspend fun deleteById(id: String)
    suspend fun insert(insertData: InsertData)
    suspend fun allConfigFlow(): Flow<List<ServerConfigEntity>>
    suspend fun allConfig(): List<ServerConfigEntity>
    fun configById(id: String): ServerConfigEntity?
    suspend fun configByLinks(links: ServerConfigEntity.Links): ServerConfigEntity?
    suspend fun updateApiVersion(id: String, commonApiVersion: Int)
    suspend fun updateApiVersionAndDomain(id: String, domain: String, commonApiVersion: Int)
    suspend fun configForUser(userId: UserIDEntity): ServerConfigEntity?
    suspend fun setFederationToTrue(id: String)
    suspend fun getServerConfigsWithAccIdWithLastCheckBeforeDate(date: String): Flow<List<ServerConfigWithUserIdEntity>>
    suspend fun updateBlackListCheckDate(configIds: Set<String>, date: String)

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
