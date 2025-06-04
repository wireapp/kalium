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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationMetaDataRepository
import io.mockative.Mockable

/**
 * This class is responsible for providing the conversation type for a call.
 */
@Mockable
interface GetCallConversationTypeProvider {
    suspend operator fun invoke(conversationId: ConversationId): ConversationTypeCalling
}

internal class GetCallConversationTypeProviderImpl(
    private val userConfigRepository: UserConfigRepository,
    private val conversationMetaDataRepository: ConversationMetaDataRepository,
) : GetCallConversationTypeProvider {
    override suspend fun invoke(conversationId: ConversationId): ConversationTypeCalling {
        return conversationMetaDataRepository.getConversationTypeAndProtocolInfo(conversationId)
            .flatMap { (type: Conversation.Type, protocolInfo: Conversation.ProtocolInfo) ->
                when (type) {
                    is Conversation.Type.Group -> {
                        handleGroupConversation(protocolInfo)
                    }

                    Conversation.Type.OneOnOne -> {
                        handleOneToOne(protocolInfo)
                    }

                    Conversation.Type.ConnectionPending,
                    Conversation.Type.Self -> {
                        ConversationTypeCalling.Unknown.right()
                    }
                }
            }.fold(
                { ConversationTypeCalling.Unknown },
                { it }
            )
    }

    private fun handleGroupConversation(
        protocolInfo: Conversation.ProtocolInfo
    ): Either.Right<ConversationTypeCalling> = when (protocolInfo) {
        is Conversation.ProtocolInfo.MLS -> ConversationTypeCalling.ConferenceMls
        is Conversation.ProtocolInfo.Proteus,
        is Conversation.ProtocolInfo.Mixed -> ConversationTypeCalling.Conference
    }.right()

    private suspend fun handleOneToOne(protocolInfo: Conversation.ProtocolInfo): Either<StorageFailure, ConversationTypeCalling> =
        userConfigRepository.shouldUseSFTForOneOnOneCalls().flatMap { shouldUseSFTForOneOnOneCalls ->
            if (!shouldUseSFTForOneOnOneCalls) {
                return@flatMap ConversationTypeCalling.OneOnOne.right()
            }
            // in the case of using SFT for 1:1 calls must be treated as group
            handleGroupConversation(protocolInfo)
        }
}
