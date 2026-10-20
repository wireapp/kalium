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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.event.removedFromTeamMessage
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.UserSessionBinding
import dev.zacsweers.metro.Inject
import kotlin.uuid.Uuid

internal interface TeamEventReceiver : EventReceiver<Event.Team>

@Inject
internal class TeamEventReceiverImpl(
    @UserSessionBinding("userRepository")
    private val userRepository: UserRepository,
    @UserSessionBinding("persistMessage")
    private val persistMessage: PersistMessageUseCase,
    @UserSessionBinding("userId")
    private val selfUserId: UserId,
) : TeamEventReceiver {

    override suspend fun onEvent(
        transactionContext: CryptoTransactionContext,
        event: Event.Team,
        deliveryInfo: EventDeliveryInfo
    ): Either<CoreFailure, Unit> {
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
                    persistMessage(event.removedFromTeamMessage(selfUserId.domain, conversationId, Uuid.random().toString()))
                }
            }
    }
}
