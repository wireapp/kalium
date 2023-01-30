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

package com.wire.kalium.logic.feature.user

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.message.MessageTarget
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.util.DateTimeUtil

/**
 * Updates the current user's [UserAvailabilityStatus] status.
 * @see [UserAvailabilityStatus]
 */
class UpdateSelfAvailabilityStatusUseCase internal constructor(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val messageSender: MessageSender,
    private val provideClientId: CurrentClientIdProvider,
    private val selfUserId: QualifiedID,
) {
    /**
     * @param status the new [UserAvailabilityStatus] status.
     */
    suspend operator fun invoke(status: UserAvailabilityStatus) {
        val result = provideClientId().flatMap { selfClientId ->
            println("cyka selfClientId $selfClientId")
            conversationRepository.getProteusSelfConversationId()
                .flatMap { conversationIds ->
                    println("cyka conversationIds $conversationIds")
                    userRepository.getTeamRecipients(500)
                        .flatMap { recipients ->
                            val message = Message.Signaling(
                                id = uuid4().toString(),
                                content = MessageContent.Availability(status),
                                conversationId = conversationIds,
                                date = DateTimeUtil.currentIsoDateTimeString(),
                                senderUserId = selfUserId,
                                senderClientId = selfClientId,
                                status = Message.Status.SENT
                            )

                            println("cyka recipients ${recipients.size}")

                            val recipientsFiltered = recipients
                                .map { it.copy(clients = it.clients.filter { clientId -> clientId != selfClientId }) }

                            messageSender.sendMessage(message, MessageTarget.Client(recipientsFiltered))
                        }
                }
        }
        println("cyka result $result")
        // TODO: Handle possibility of being offline. Storing the broadcast to be sent when Sync is done.
        // For now, we don't need Sync, as we do not broadcast the availability to other devices or users.
        userRepository.updateSelfUserAvailabilityStatus(status)
    }
}
