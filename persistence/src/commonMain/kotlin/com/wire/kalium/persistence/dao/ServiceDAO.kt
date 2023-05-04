/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.persistence.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.ServiceQueries
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

data class ServiceEntity(
    val id: BotIdEntity,
    val name: String,
    val description: String,
    val summary: String,
    val enabled: Boolean,
    val tags: List<String>,
    val previewAssetId: UserAssetIdEntity?,
    val completeAssetId: UserAssetIdEntity?
)

data class ServiceViewEntity(
    val service: ServiceEntity,
    val isMember: Boolean
)

@Suppress("LongParameterList")
internal fun mapToServiceEntity(
    id: BotIdEntity,
    name: String,
    description: String,
    summary: String,
    tags: List<String>,
    enabled: Boolean,
    previewAssetId: QualifiedIDEntity?,
    completeAssetId: QualifiedIDEntity?
): ServiceEntity = ServiceEntity(
    id = id,
    name = name,
    description = description,
    summary = summary,
    tags = tags,
    enabled = enabled,
    previewAssetId = previewAssetId,
    completeAssetId = completeAssetId
)

@Suppress("LongParameterList")
internal fun mapToServiceView(
    id: BotIdEntity,
    name: String,
    description: String,
    summary: String,
    tags: List<String>,
    enabled: Boolean,
    previewAssetId: QualifiedIDEntity?,
    completeAssetId: QualifiedIDEntity?,
    isMember: Boolean,
): ServiceViewEntity = ServiceViewEntity(
    service = mapToServiceEntity(
        id = id,
        name = name,
        description = description,
        summary = summary,
        tags = tags,
        enabled = enabled,
        previewAssetId = previewAssetId,
        completeAssetId = completeAssetId
    ),
    isMember = isMember
)

interface ServiceDAO {
    suspend fun byId(id: BotIdEntity): ServiceEntity?
    suspend fun byIdAndConversation(id: BotIdEntity, conversationId: ConversationIDEntity): ServiceViewEntity?
    suspend fun observeByIdAndConversation(id: BotIdEntity, conversationId: ConversationIDEntity): Flow<ServiceViewEntity?>
    suspend fun searchServicesWithConversation(query: String, conversationId: ConversationIDEntity): Flow<List<ServiceViewEntity>>
    suspend fun insert(service: ServiceEntity)
    suspend fun insert(serviceList: List<ServiceEntity>)

}

internal class ServiceDAOImpl(
    private val serviceQueries: ServiceQueries,
    private val context: CoroutineContext
) : ServiceDAO {
    override suspend fun byId(id: BotIdEntity): ServiceEntity? = withContext(context) {
        serviceQueries.byId(id, mapper = ::mapToServiceEntity).executeAsOneOrNull()
    }

    override suspend fun byIdAndConversation(id: BotIdEntity, conversationId: ConversationIDEntity): ServiceViewEntity? =
        withContext(context) {
            serviceQueries.byIdAndIsMember(conversationId, id, mapper = ::mapToServiceView).executeAsOneOrNull()
        }

    override suspend fun observeByIdAndConversation(id: BotIdEntity, conversationId: ConversationIDEntity): Flow<ServiceViewEntity?> =
        serviceQueries.byIdAndIsMember(conversationId, id, mapper = ::mapToServiceView).asFlow().flowOn(context).mapToOneOrNull(context)

    override suspend fun searchServicesWithConversation(
        query: String,
        conversationId: ConversationIDEntity
    ): Flow<List<ServiceViewEntity>> =
        serviceQueries.searchServices(conversationId, query, mapper = ::mapToServiceView).asFlow().flowOn(context).mapToList()

    override suspend fun insert(service: ServiceEntity) = withContext(context) {
        serviceQueries.insert(
            id = service.id,
            name = service.name,
            description = service.description,
            summary = service.summary,
            tags = service.tags,
            enabled = service.enabled,
            preview_asset_id = service.previewAssetId,
            complete_asset_id = service.completeAssetId
        )
    }

    override suspend fun insert(serviceList: List<ServiceEntity>) = withContext(context) {
        serviceQueries.transaction {
            serviceList.forEach { service ->
                serviceQueries.insert(
                    id = service.id,
                    name = service.name,
                    description = service.description,
                    summary = service.summary,
                    tags = service.tags,
                    enabled = service.enabled,
                    preview_asset_id = service.previewAssetId,
                    complete_asset_id = service.completeAssetId
                )
            }
        }
    }
}
