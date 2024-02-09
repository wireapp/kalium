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

import com.wire.kalium.persistence.SearchQueries
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

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
    suspend fun getKnownContacts(): List<UserSearchEntity>
    suspend fun searchList(query: String): List<UserSearchEntity>
    suspend fun getKnownContactsExcludingAConversation(conversationId: ConversationIDEntity): List<UserSearchEntity>
    suspend fun searchListExcludingAConversation(conversationId: ConversationIDEntity, query: String): List<UserSearchEntity>
    suspend fun handleSearch(searchQuery: String): List<UserSearchEntity>
    suspend fun handleSearchExcludingAConversation(
        searchQuery: String,
        conversationId: ConversationIDEntity
    ): List<UserSearchEntity>
}

internal class SearchDAOImpl internal constructor(
    private val searchQueries: SearchQueries,
    private val coroutineContext: CoroutineContext
) : SearchDAO {

    override suspend fun getKnownContacts(): List<UserSearchEntity> = withContext(coroutineContext) {
        searchQueries.selectAllConnectedUsers(mapper = UserSearchEntityMapper::map).executeAsList()
    }

    override suspend fun searchList(query: String): List<UserSearchEntity> = withContext(coroutineContext) {
        searchQueries.searchByName(query, mapper = UserSearchEntityMapper::map).executeAsList()
    }

    override suspend fun getKnownContactsExcludingAConversation(conversationId: ConversationIDEntity): List<UserSearchEntity> =
        withContext(coroutineContext) {
            searchQueries.selectAllConnectedUsersNotInConversation(
                conversationId,
                mapper = UserSearchEntityMapper::map
            ).executeAsList()
        }

    override suspend fun searchListExcludingAConversation(
        conversationId: ConversationIDEntity,
        query: String
    ): List<UserSearchEntity> = withContext(coroutineContext) {
        searchQueries.searchMyNameExcludingAConversation(
            query,
            conversationId,
            mapper = UserSearchEntityMapper::map
        ).executeAsList()
    }

    override suspend fun handleSearch(searchQuery: String): List<UserSearchEntity> = withContext(coroutineContext) {
        searchQueries.searchByHandle(
            searchQuery,
            mapper = UserSearchEntityMapper::map
        ).executeAsList()
    }

    override suspend fun handleSearchExcludingAConversation(
        searchQuery: String,
        conversationId: ConversationIDEntity
    ): List<UserSearchEntity> = withContext(coroutineContext) {
        searchQueries.searchByHandleExcludingAConversation(
            searchQuery,
            conversationId,
            mapper = UserSearchEntityMapper::map
        ).executeAsList()
    }
}
