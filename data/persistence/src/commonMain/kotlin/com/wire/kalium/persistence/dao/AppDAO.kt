/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import com.wire.kalium.persistence.App
import com.wire.kalium.persistence.AppsQueries
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.mapToList
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class AppEntity(
    val id: QualifiedIDEntity,
    val name: String,
    val description: String,
    val category: String?,
    val teamId: String?,
    val previewAssetId: UserAssetIdEntity?,
    val completeAssetId: UserAssetIdEntity?
)

private fun mapToAppEntity(
    id: QualifiedIDEntity,
    name: String,
    description: String,
    category: String?,
    teamId: String?,
    previewAssetId: UserAssetIdEntity?,
    completeAssetId: UserAssetIdEntity?
): AppEntity = AppEntity(
    id = id,
    name = name,
    description = description,
    category = category,
    teamId = teamId,
    previewAssetId = previewAssetId,
    completeAssetId = completeAssetId
)

private fun mapAppToAppEntity(app: App): AppEntity =
    mapToAppEntity(
        id = app.id,
        name = app.name,
        description = app.description,
        category = app.category,
        teamId = app.team_id,
        previewAssetId = app.preview_asset_id,
        completeAssetId = app.complete_asset_id
    )

@Mockable
interface AppDAO {
    suspend fun insert(appEntity: AppEntity)
    suspend fun upsertApps(apps: List<AppEntity>)
    suspend fun byId(id: QualifiedIDEntity): AppEntity?
    suspend fun observeIsAppMember(
        appId: QualifiedIDEntity,
        conversationId: ConversationIDEntity
    ): Flow<QualifiedIDEntity?>

    suspend fun observeAllApps(): Flow<List<AppEntity>>
    suspend fun searchAppsByName(query: String): Flow<List<AppEntity>>
}

internal class AppDAOImpl(
    private val appsQueries: AppsQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher
) : AppDAO {
    override suspend fun insert(appEntity: AppEntity) {
        withContext(writeDispatcher.value) {
            appsQueries.insert(
                id = appEntity.id,
                name = appEntity.name,
                description = appEntity.description,
                category = appEntity.category,
                team_id = appEntity.teamId,
                preview_asset_id = appEntity.previewAssetId,
                complete_asset_id = appEntity.completeAssetId
            )
        }
    }

    override suspend fun upsertApps(apps: List<AppEntity>) = withContext(writeDispatcher.value) {
        appsQueries.transaction {
            for (appEntity: AppEntity in apps) {
                appsQueries.insert(
                    id = appEntity.id,
                    name = appEntity.name,
                    description = appEntity.description,
                    category = appEntity.category,
                    team_id = appEntity.teamId,
                    preview_asset_id = appEntity.previewAssetId,
                    complete_asset_id = appEntity.completeAssetId
                )
            }
        }
    }

    override suspend fun byId(id: QualifiedIDEntity): AppEntity? = withContext(readDispatcher.value) {
        appsQueries.byId(id = id, mapper = ::mapToAppEntity).executeAsOneOrNull()
    }

    /**
     *  Returns the userId only if the given user is a member of the conversation.
     *
     *  NOTE:
     *  This may look redundant since it returns the same userId that is passed in.
     *  However, this follows the existing patter used for legacy bots/services,
     *  where a serviceId is resolved to a userId via Member table.
     *
     *  We keep this behavior for consistency while old bots/services and new Apps coexist.
     *  Effectively, this acts as a member check + userId passthrough.
     *
     *  Can be refactored to a boolean check (isMember) in the future once old bots/services dependencies are removed.
     */
    override suspend fun observeIsAppMember(
        appId: QualifiedIDEntity,
        conversationId: ConversationIDEntity
    ): Flow<QualifiedIDEntity?> =
        appsQueries.getUserIdFromMember(conversationId, appId)
            .asFlow()
            .mapToOneOrNull(readDispatcher.value)
            .flowOn(readDispatcher.value)

    override suspend fun observeAllApps(): Flow<List<AppEntity>> =
        appsQueries.getAll()
            .asFlow()
            .mapToList()
            .map { it.map(::mapAppToAppEntity) }
            .flowOn(readDispatcher.value)

    override suspend fun searchAppsByName(query: String): Flow<List<AppEntity>> =
        appsQueries.searchByName(query)
            .asFlow()
            .mapToList()
            .map { it.map(::mapAppToAppEntity) }
            .flowOn(readDispatcher.value)
}
