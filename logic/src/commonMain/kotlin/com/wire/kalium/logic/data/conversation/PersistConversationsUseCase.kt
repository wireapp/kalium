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
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONVERSATIONS
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
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
internal interface PersistConversationsUseCase {
    suspend operator fun invoke(
        transactionContext: CryptoTransactionContext,
        conversations: List<ConversationResponse>,
        invalidateMembers: Boolean,
        reason: ConversationSyncReason = ConversationSyncReason.Other,
    ): Either<CoreFailure, Unit>
}

@OptIn(ConversationPersistenceApi::class)
internal class PersistConversationsUseCaseImpl(
    private val selfUserId: UserId,
    private val conversationRepository: ConversationRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val idMapper: IdMapper = IdMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId),
) : PersistConversationsUseCase {

    override suspend fun invoke(
        transactionContext: CryptoTransactionContext,
        conversations: List<ConversationResponse>,
        invalidateMembers: Boolean,
        reason: ConversationSyncReason,
    ): Either<CoreFailure, Unit> {
        val conversationEntities = conversations
            .map { conversationResponse ->
                val mlsGroupState = transactionContext.mls?.let { mlsContext ->
                    conversationResponse.groupId?.let {
                        mlsGroupState(mlsContext, idMapper.fromGroupIDEntity(it), reason)
                    }
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
        mlsContext: MlsCoreCryptoContext,
        groupId: GroupID,
        reason: ConversationSyncReason
    ): ConversationEntity.GroupState = hasEstablishedMLSGroup(mlsContext, groupId)
        .fold({ failure ->
            kaliumLogger.withFeatureId(CONVERSATIONS)
                .w("Error checking MLS group state, setting to PENDING_JOIN")
            ConversationEntity.GroupState.PENDING_JOIN
        }, { exists ->
            if (exists) {
                ConversationEntity.GroupState.ESTABLISHED
            } else {
                reason.newGroupState()
            }
        })

    private suspend fun hasEstablishedMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID
    ): Either<CoreFailure, Boolean> =
        wrapMLSRequest {
            mlsContext.conversationExists(groupID.toCrypto())
        }

}

sealed interface ConversationSyncReason {
    fun newGroupState(): ConversationEntity.GroupState

    data object Event : ConversationSyncReason {
        override fun newGroupState(): ConversationEntity.GroupState = ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE
    }

    data object ConversationReset : ConversationSyncReason {
        override fun newGroupState(): ConversationEntity.GroupState = ConversationEntity.GroupState.PENDING_AFTER_RESET
    }

    data object Other : ConversationSyncReason {
        override fun newGroupState(): ConversationEntity.GroupState = ConversationEntity.GroupState.PENDING_JOIN
    }
}
