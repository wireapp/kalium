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

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ServerConfigurationQueries
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.model.ServerConfigWithUserIdEntity
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

internal class ServerConfigurationDAOImpl internal constructor(
    private val queries: ServerConfigurationQueries,
    private val queriesContext: CoroutineContext,
    private val mapper: ServerConfigMapper = ServerConfigMapper
) :
    ServerConfigurationDAO {

    override suspend fun deleteById(id: String) = withContext(queriesContext) {
        queries.deleteById(id)
    }

    override suspend fun insert(
        insertData: ServerConfigurationDAO.InsertData
    ) = withContext(queriesContext) {
        with(insertData) {
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
                domain ?: "",
                commonApiVersion,
                apiProxyHost ?: "",
                apiProxyNeedsAuthentication,
                apiProxyPort ?: 0
            )
        }
    }

    override suspend fun allConfigFlow(): Flow<List<ServerConfigEntity>> =
        queries.storedConfig(mapper = mapper::fromServerConfiguration).asFlow().flowOn(queriesContext).mapToList()

    override suspend fun allConfig(): List<ServerConfigEntity> = withContext(queriesContext) {
        queries.storedConfig(mapper = mapper::fromServerConfiguration).executeAsList()
    }

    override fun configById(id: String): ServerConfigEntity? =
        queries.getById(id, mapper = mapper::fromServerConfiguration).executeAsOneOrNull()

    override suspend fun configByLinks(links: ServerConfigEntity.Links): ServerConfigEntity? = withContext(queriesContext) {
        with(links) {
            queries.getByLinks(
                apiBaseUrl = api,
                webSocketBaseUrl = webSocket,
                title = title,
                api_proxy_host = apiProxy?.host ?: "",
                api_proxy_port = apiProxy?.port ?: 0,
                mapper = mapper::fromServerConfiguration
            )
        }.executeAsOneOrNull()
    }

    override suspend fun updateApiVersion(id: String, commonApiVersion: Int) = withContext(queriesContext) {
        queries.updateApiVersion(commonApiVersion, id)
    }

    override suspend fun updateApiVersionAndDomain(id: String, domain: String, commonApiVersion: Int) =
        withContext(queriesContext) {
            queries.updateApiVersionAndDomain(commonApiVersion, domain, id)
        }

    override suspend fun configForUser(userId: UserIDEntity): ServerConfigEntity? = withContext(queriesContext) {
        queries.getByUser(userId, mapper = mapper::fromServerConfiguration).executeAsOneOrNull()
    }

    override suspend fun setFederationToTrue(id: String) = withContext(queriesContext) {
        queries.setFederationToTrue(id)
    }

    override suspend fun getServerConfigsWithAccIdWithLastCheckBeforeDate(date: String): Flow<List<ServerConfigWithUserIdEntity>> =
        queries.getServerConfigsWithAccIdWithLastCheckBeforeDate(mapper::serverConfigWithAccId)
            .asFlow().flowOn(queriesContext)
            .mapToList()

    override suspend fun updateBlackListCheckDate(configIds: Set<String>, date: String) = withContext(queriesContext) {
        queries.updateLastBlackListCheckByIds(date, configIds)
    }
}
