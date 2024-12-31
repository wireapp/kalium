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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.InteractionAvailability
import com.wire.kalium.logic.data.conversation.interactionAvailability
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Use case that check if self user is able to interact in conversation.
 *
 * To interact with a conversation means to be able to send messages. This includes non-standalone messages,
 * like [MessageContent.Reaction], [MessageContent.ButtonAction], etc.
 *
 * @see InteractionAvailability
 */
class ObserveConversationInteractionAvailabilityUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val selfUserId: UserId,
    private val selfClientIdProvider: CurrentClientIdProvider,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) {

    /**
     * @param conversationId the id of the conversation where user checks his interaction availability
     * @return an [IsInteractionAvailableResult] containing Success or Failure cases
     */
    suspend operator fun invoke(conversationId: ConversationId): Flow<IsInteractionAvailableResult> = withContext(dispatcher.io) {

        val isSelfClientMlsCapable = selfClientIdProvider().flatMap {
            userRepository.isClientMlsCapable(selfUserId, it)
        }.getOrElse {
            return@withContext flow { IsInteractionAvailableResult.Failure(it) }
        }

        kaliumLogger.withTextTag("ObserveConversationInteractionAvailabilityUseCase").d("isSelfClientMlsCapable $isSelfClientMlsCapable")

        conversationRepository.observeConversationDetailsById(conversationId).map { eitherConversation ->
            eitherConversation.fold({ failure -> IsInteractionAvailableResult.Failure(failure) }, { conversationDetails ->
                val isProtocolSupported = doesUserSupportConversationProtocol(
                    conversationDetails = conversationDetails,
                    isSelfClientMlsCapable = isSelfClientMlsCapable
                )
                if (!isProtocolSupported) { // short-circuit to Unsupported Protocol if it's the case
                    return@fold IsInteractionAvailableResult.Success(InteractionAvailability.UNSUPPORTED_PROTOCOL)
                }
                val availability = conversationDetails.interactionAvailability()
                IsInteractionAvailableResult.Success(availability)
            })
        }
    }

    private fun doesUserSupportConversationProtocol(
        conversationDetails: ConversationDetails,
        isSelfClientMlsCapable: Boolean
    ): Boolean = when (conversationDetails.conversation.protocol) {
        is Conversation.ProtocolInfo.MLS -> isSelfClientMlsCapable
        // Messages in mixed conversations are sent through Proteus
        is Conversation.ProtocolInfo.Mixed,
        Conversation.ProtocolInfo.Proteus -> true
    }
}

sealed class IsInteractionAvailableResult {
    data class Success(val interactionAvailability: InteractionAvailability) : IsInteractionAvailableResult()
    data class Failure(val coreFailure: CoreFailure) : IsInteractionAvailableResult()
}
