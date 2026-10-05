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

import app.cash.sqldelight.async.coroutines.awaitAsList

import com.wire.kalium.persistence.SearchQueries
import com.wire.kalium.persistence.db.ReadDispatcher
import kotlinx.coroutines.withContext

data class UserSearchEntity(
    val id: QualifiedIDEntity,
    val name: String?,
    val handle: String?,
    val completeAssetId: QualifiedIDEntity?,
    val previewAssetId: QualifiedIDEntity?,
    val type: UserTypeEntity,
    val connectionStatus: ConnectionEntity.State
)

private object UserSearchEntityMapper {
    @Suppress("FunctionParameterNaming", "LongParameterList")
    fun map(
        qualified_id: QualifiedIDEntity,
        name: String?,
        complete_asset_id: QualifiedIDEntity?,
        preview_asset_id: QualifiedIDEntity?,
        user_type: UserTypeEntity,
        connection_state: ConnectionEntity.State,
        handle: String?,
    ): UserSearchEntity {
        return UserSearchEntity(
            id = qualified_id,
            name = name,
            completeAssetId = complete_asset_id,
            previewAssetId = preview_asset_id,
            type = user_type,
            connectionStatus = connection_state,
            handle = handle
        )
    }
}

interface SearchDAO {
    suspend fun getKnownContacts(
        excludeConversationId: ConversationIDEntity? = null,
        onlyTeamId: String? = null,
        onlyDomain: String? = null,
    ): List<UserSearchEntity>
    suspend fun searchByName(
        searchQuery: String,
        excludeConversationId: ConversationIDEntity? = null,
        onlyTeamId: String? = null,
        onlyDomain: String? = null,
    ): List<UserSearchEntity>
    suspend fun searchByHandle(
        searchQuery: String,
        excludeConversationId: ConversationIDEntity? = null,
        onlyTeamId: String? = null,
        onlyDomain: String? = null,
    ): List<UserSearchEntity>
}

internal class SearchDAOImpl internal constructor(
    private val searchQueries: SearchQueries,
    private val readDispatcher: ReadDispatcher,
) : SearchDAO {

    override suspend fun getKnownContacts(
        excludeConversationId: ConversationIDEntity?,
        onlyTeamId: String?,
        onlyDomain: String?,
    ): List<UserSearchEntity> = withContext(readDispatcher.value) {
        searchQueries.selectAllConnectedUsers(
            excludeConversationId = excludeConversationId,
            onlyTeamId = onlyTeamId,
            onlyDomain = onlyDomain,
            mapper = UserSearchEntityMapper::map
        ).awaitAsList()
    }

    override suspend fun searchByName(
        searchQuery: String,
        excludeConversationId: ConversationIDEntity?,
        onlyTeamId: String?,
        onlyDomain: String?,
    ): List<UserSearchEntity> = withContext(readDispatcher.value) {
        searchQueries.searchByName(
            searchQuery = searchQuery,
            excludeConversationId = excludeConversationId,
            onlyTeamId = onlyTeamId,
            onlyDomain = onlyDomain,
            mapper = UserSearchEntityMapper::map
        ).awaitAsList()
    }

    override suspend fun searchByHandle(
        searchQuery: String,
        excludeConversationId: ConversationIDEntity?,
        onlyTeamId: String?,
        onlyDomain: String?,
    ): List<UserSearchEntity> = withContext(readDispatcher.value) {
        searchQueries.searchByHandle(
            searchQuery = searchQuery,
            excludeConversationId = excludeConversationId,
            onlyTeamId = onlyTeamId,
            onlyDomain = onlyDomain,
            mapper = UserSearchEntityMapper::map
        ).awaitAsList()
    }
}
