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
        connection_strate: ConnectionEntity.State
    ): UserSearchEntity {
        return UserSearchEntity(
            qualified_id,
            name,
            completeAssetId = complete_asset_id,
            previewAssetId = preview_asset_id,
            user_type,
            connectionStatus = connection_strate
        )
    }
}

interface SearchDAO {
    suspend fun initialSearchList(): List<UserSearchEntity>
    suspend fun searchList(query: String): List<UserSearchEntity>
    suspend fun initialSearchListExcludingAConversation(conversationId: ConversationIDEntity): List<UserSearchEntity>
    suspend fun searchListExcludingAConversation(conversationId: ConversationIDEntity, query: String): List<UserSearchEntity>
}

internal class SearchDAOImpl internal constructor(
    private val searchQueries: SearchQueries,
    private val coroutineContext: CoroutineContext
) : SearchDAO {

    override suspend fun initialSearchList(): List<UserSearchEntity> = withContext(coroutineContext) {
        searchQueries.initialSearchList(mapper = UserSearchEntityMapper::map).executeAsList()
    }

    override suspend fun searchList(query: String): List<UserSearchEntity> = withContext(coroutineContext) {
        searchQueries.searchMyName(query, mapper = UserSearchEntityMapper::map).executeAsList()
    }

    override suspend fun initialSearchListExcludingAConversation(conversationId: ConversationIDEntity): List<UserSearchEntity> =
        withContext(coroutineContext) {
            searchQueries.initialSearchListExcludingAConversation(
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
}
