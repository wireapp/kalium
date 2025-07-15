/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONVERSATIONS
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.ConversationPersistenceApi
import io.mockative.Mockable

/**
 * Use case responsible for persisting a list of conversations in the local database.
 *
 * It resolves the MLS group state for each conversation (if applicable),
 * maps them to local entities, and persists them. It also updates the conversation members
 * if necessary.
 *
 * @param conversations List of conversations received from the backend.
 * @param invalidateMembers Whether the existing member list should be refreshed.
 * @param originatedFromEvent Whether the call originates from an event (affects MLS group state resolution).
 */
@Mockable
interface PersistConversationsUseCase {
    suspend operator fun invoke(
        conversations: List<ConversationResponse>,
        invalidateMembers: Boolean,
        originatedFromEvent: Boolean = false,
    ): Either<CoreFailure, Unit>
}

@OptIn(ConversationPersistenceApi::class)
internal class PersistConversationsUseCaseImpl(
    private val selfUserId: UserId,
    private val conversationRepository: ConversationRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val idMapper: IdMapper = IdMapperImpl(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId),
) : PersistConversationsUseCase {

    override suspend fun invoke(
        conversations: List<ConversationResponse>,
        invalidateMembers: Boolean,
        originatedFromEvent: Boolean
    ): Either<CoreFailure, Unit> {
        val conversationEntities = conversations
            .map { conversationResponse ->
                val mlsGroupState = conversationResponse.groupId?.let {
                    mlsGroupState(idMapper.fromGroupIDEntity(it), originatedFromEvent)
                }
                conversationMapper.fromApiModelToDaoModel(
                    conversationResponse,
                    mlsGroupState = mlsGroupState,
                    selfTeamIdProvider().getOrNull(),
                )
            }

        return conversationRepository.persistConversations(conversationEntities).onSuccess {
            conversationRepository.updateConversationMembers(
                conversations,
                selfTeamIdProvider.invoke().getOrNull(),
                invalidateMembers = invalidateMembers
            )
        }
    }

    private suspend fun mlsGroupState(
        groupId: GroupID,
        originatedFromEvent: Boolean = false
    ): ConversationEntity.GroupState = hasEstablishedMLSGroup(groupId)
        .fold({ failure ->
            kaliumLogger.withFeatureId(CONVERSATIONS)
                .w("Error checking MLS group state, setting to ${ConversationEntity.GroupState.PENDING_JOIN}")
            ConversationEntity.GroupState.PENDING_JOIN
        }, { exists ->
            if (exists) {
                ConversationEntity.GroupState.ESTABLISHED
            } else {
                if (originatedFromEvent) {
                    ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE
                } else {
                    ConversationEntity.GroupState.PENDING_JOIN
                }
            }
        })

    private suspend fun hasEstablishedMLSGroup(groupID: GroupID): Either<CoreFailure, Boolean> =
        mlsClientProvider.getMLSClient()
            .flatMap {
                wrapMLSRequest {
                    it.conversationExists(groupID.toCrypto())
                }
            }

}
