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

package com.wire.kalium.logic.sync.receiver

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onSuccess

internal interface TeamEventReceiver : EventReceiver<Event.Team>

internal class TeamEventReceiverImpl(
    private val userRepository: UserRepository,
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: UserId,
) : TeamEventReceiver {

    override suspend fun onEvent(event: Event.Team, deliveryInfo: EventDeliveryInfo): Either<CoreFailure, Unit> {
        when {
            event is Event.Team.MemberLeave -> handleMemberLeave(event)
        }
        // TODO: Make sure errors are accounted for by each handler.
        //       onEvent now requires Either, so we can propagate errors,
        //       but not all handlers are using it yet.
        //       Returning Either.Right is the equivalent of how it was originally working.
        return Either.Right(Unit)
    }

    @Suppress("LongMethod")
    private suspend fun handleMemberLeave(event: Event.Team.MemberLeave) {
        val removedUser = UserId(event.memberId, selfUserId.domain)
        userRepository.markUserAsDeletedAndRemoveFromGroupConversations(removedUser)
            .onSuccess {
                it.forEach { conversationId ->
                    val message = Message.System(
                        id = uuid4().toString(), // We generate a random uuid for this new system message
                        content = MessageContent.MemberChange.RemovedFromTeam(listOf(removedUser)),
                        conversationId = conversationId,
                        date = event.timestampIso,
                        senderUserId = removedUser,
                        status = Message.Status.Sent,
                        visibility = Message.Visibility.VISIBLE,
                        expirationData = null
                    )
                    persistMessage(message)
                }
            }
    }
}
