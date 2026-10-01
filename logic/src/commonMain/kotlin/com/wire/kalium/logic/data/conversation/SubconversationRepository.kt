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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.network.api.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.persistence.dao.conversation.SubconversationDAO

internal interface SubconversationRepository {

    suspend fun insertSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId,
        groupId: GroupID
    )

    suspend fun getSubconversationInfo(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): GroupID?

    suspend fun deleteSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    )

    suspend fun containsSubconversation(groupId: GroupID): Boolean
    suspend fun deleteRemoteSubConversation(
        conversationId: ConversationId,
        subConversationId: SubconversationId,
        subConversationDeleteRequest: SubconversationDeleteRequest
    ): Either<CoreFailure, Unit>

    suspend fun fetchRemoteSubConversationGroupInfo(
        conversationId: ConversationId,
        subConversationId: SubconversationId
    ): Either<CoreFailure, ByteArray>

    suspend fun fetchRemoteSubConversationDetails(
        conversationId: ConversationId,
        subConversationId: SubconversationId
    ): Either<NetworkFailure, SubConversation>
}

internal class SubconversationRepositoryImpl(
    private val conversationApi: ConversationApi,
    private val subconversations: SubconversationDAO,
) : SubconversationRepository {

    override suspend fun insertSubconversation(conversationId: ConversationId, subconversationId: SubconversationId, groupId: GroupID) =
        subconversations.upsert(conversationId.toDao(), subconversationId.value, groupId.value)

    override suspend fun getSubconversationInfo(conversationId: ConversationId, subconversationId: SubconversationId): GroupID? =
        subconversations.getGroupId(conversationId.toDao(), subconversationId.value)?.let(::GroupID)

    override suspend fun containsSubconversation(groupId: GroupID): Boolean = subconversations.containsGroup(groupId.value)

    override suspend fun deleteSubconversation(conversationId: ConversationId, subconversationId: SubconversationId) =
        subconversations.delete(conversationId.toDao(), subconversationId.value)

    override suspend fun deleteRemoteSubConversation(
        conversationId: ConversationId,
        subConversationId: SubconversationId,
        subConversationDeleteRequest: SubconversationDeleteRequest
    ): Either<CoreFailure, Unit> =
        wrapApiRequest {
            conversationApi.deleteSubconversation(
                conversationId.toApi(),
                subConversationId.toApi(),
                subConversationDeleteRequest
            )
        }.onSuccess {
            kaliumLogger.i("Subconversation deleted successfully")
        }.onFailure {
            kaliumLogger.i("Failed to delete subconversation")
        }

    override suspend fun fetchRemoteSubConversationGroupInfo(
        conversationId: ConversationId,
        subConversationId: SubconversationId
    ): Either<CoreFailure, ByteArray> = wrapApiRequest {
        conversationApi.fetchSubconversationGroupInfo(
            conversationId.toApi(),
            subConversationId.toApi()
        )
    }

    override suspend fun fetchRemoteSubConversationDetails(
        conversationId: ConversationId,
        subConversationId: SubconversationId
    ): Either<NetworkFailure, SubConversation> = wrapApiRequest {
        conversationApi.fetchSubconversationDetails(
            conversationId.toApi(),
            subConversationId.toApi()
        )
    }.map {
        it.toModel()
    }
}
