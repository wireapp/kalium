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
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.ConversationTypeForCall
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
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
    private val conversationRepository: ConversationRepository,
    private val callMapper: CallMapper,
) : GetCallConversationTypeProvider {
    override suspend fun invoke(conversationId: ConversationId): ConversationTypeCalling {

        val type = conversationRepository.getConversationTypeById(conversationId).fold(
            { ConversationTypeForCall.Unknown },
            {
                conversationRepository.getConversationProtocolInfo(conversationId).fold({
                    ConversationTypeForCall.Unknown
                }, { protocol ->
                    callMapper.fromConversationTypeToConversationTypeForCall(it, protocol)
                })
            }
        )

        val callConversationType = callMapper.toConversationTypeCalling(type)

        return userConfigRepository.shouldUseSFTForOneOnOneCalls().fold({
            callConversationType
        }, { shouldUseSFTForOneOnOneCalls ->
            if (shouldUseSFTForOneOnOneCalls) {
                userConfigRepository.isMLSEnabled().map {
                    if (it) {
                        return@fold ConversationTypeCalling.ConferenceMls
                    }
                }
                ConversationTypeCalling.Conference
            } else {
                callConversationType
            }
        })
    }
}
