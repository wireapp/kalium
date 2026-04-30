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
@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isWrongConversationPassword
import com.wire.kalium.persistence.dao.message.LocalId

/**
 * Use case for joining a conversation via a code invite code.
 * the param can be obtained from the deep link
 * @param code The code of the conversation to join.
 * @param key The key of the conversation to join.
 * @param domain optional domain of the conversation to join.
 */
// todo(interface). extract interface for use case
public class JoinConversationViaCodeUseCase internal constructor(
    private val conversionsGroupRepository: ConversationGroupRepository,
    private val conversationRepository: ConversationRepository,
    private val memberJoinEventHandler: MemberJoinEventHandler,
    private val joinExistingMLSConversation: JoinExistingMLSConversationUseCase,
    private val mlsConversationRepository: MLSConversationRepository,
    private val transactionProvider: CryptoTransactionProvider,
    private val selfUserId: UserId,
    private val eventMapper: EventMapper = MapperProvider.eventMapper(selfUserId),
) {
    public suspend operator fun invoke(
        code: String,
        key: String,
        domain: String?,
        password: String?
    ): Result =
    // the swagger docs say that the URI is optional, without explaining what uri need to be used
        // nevertheless the request works fine without the uri, so we are not going to use it
        conversionsGroupRepository.joinViaInviteCode(
            code,
            key,
            null,
            password
        )
            .fold({
                onError(it)
            }, { response ->
                when (response) {
                    is ConversationMemberAddedResponse.Changed -> onConversationChanged(response)
                    ConversationMemberAddedResponse.Unchanged -> onConversationUnChanged(code, key, domain)
                }
            })

    private suspend fun onConversationChanged(response: ConversationMemberAddedResponse.Changed): Result {
        val event = eventMapper.fromEventContentDTO(
            LocalId.generate(),
            response.event
        ) as Event.Conversation.MemberJoin
        val conversationId = response.event.qualifiedConversation.toModel()
        transactionProvider.transaction("joinViaInviteCode") { transactionContext ->
            memberJoinEventHandler.handle(transactionContext, event)
            joinMLSGroupIfNeeded(transactionContext, conversationId)
        }
        return Result.Success.Changed(conversationId)
    }

    private suspend fun joinMLSGroupIfNeeded(
        transactionContext: com.wire.kalium.cryptography.CryptoTransactionContext,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> =
        conversationRepository.getConversationProtocolInfo(conversationId).flatMap { protocol ->
            when (protocol) {
                is Conversation.ProtocolInfo.MLSCapable ->
                    joinExistingMLSConversation(transactionContext, conversationId).flatMap {
                        transactionContext.wrapInMLSContext { mlsContext ->
                            mlsConversationRepository.addMemberToMLSGroup(
                                mlsContext,
                                protocol.groupId,
                                listOf(selfUserId),
                                protocol.cipherSuite
                            )
                        }
                    }

                is Conversation.ProtocolInfo.Proteus ->
                    Either.Right(Unit)
            }
        }

    private fun onError(networkFailure: NetworkFailure): Result.Failure = when (networkFailure) {
        is NetworkFailure.ServerMiscommunication -> {
            if (networkFailure.kaliumException is KaliumException.InvalidRequestError &&
                (networkFailure.kaliumException as KaliumException.InvalidRequestError).isWrongConversationPassword()
            ) {
                Result.Failure.IncorrectPassword
            } else {
                Result.Failure.Generic(networkFailure)
            }
        }

        else -> Result.Failure.Generic(networkFailure)
    }

    private suspend fun onConversationUnChanged(
        code: String,
        key: String,
        domain: String?
    ): Result =
        conversionsGroupRepository.fetchLimitedInfoViaInviteCode(code, key)
            .fold({
                Result.Success.Unchanged(null)
            }, {
                ConversationId(it.nonQualifiedId, domain ?: selfUserId.domain).let { conversationId ->
                    Result.Success.Unchanged(conversationId)
                }
            })

    public sealed interface Result {
        public sealed interface Success : Result {
            public val conversationId: ConversationId?

            public data class Changed(
                override val conversationId: ConversationId,
            ) : Success

            public data class Unchanged(
                override val conversationId: ConversationId?,
            ) : Success
        }

        public sealed interface Failure : Result {

            public data object IncorrectPassword : Failure
            public data class Generic(
                val failure: NetworkFailure
            ) : Failure
        }
    }
}
