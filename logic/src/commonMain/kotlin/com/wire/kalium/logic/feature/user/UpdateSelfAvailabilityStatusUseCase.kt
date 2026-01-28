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

package com.wire.kalium.logic.feature.user

import kotlin.uuid.Uuid
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.AccountRepository
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.messaging.sending.BroadcastMessage
import com.wire.kalium.messaging.sending.BroadcastMessageTarget
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Updates the current user's [UserAvailabilityStatus] status.
 * @see [UserAvailabilityStatus]
 */
// todo(interface). extract interface for use case
public class UpdateSelfAvailabilityStatusUseCase internal constructor(
    private val accountRepository: AccountRepository,
    private val messageSender: MessageSender,
    private val provideClientId: CurrentClientIdProvider,
    private val selfUserId: QualifiedID,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    /**
     * @param status the new [UserAvailabilityStatus] status.
     */
    public suspend operator fun invoke(status: UserAvailabilityStatus) {
        withContext(dispatchers.io) {
            accountRepository.updateSelfUserAvailabilityStatus(status)
            provideClientId().flatMap { selfClientId ->
                val id = Uuid.random().toString()

                val message = BroadcastMessage(
                    id = id,
                    content = MessageContent.Availability(status),
                    date = Clock.System.now(),
                    senderUserId = selfUserId,
                    senderClientId = selfClientId,
                    status = Message.Status.Pending,
                    isSelfMessage = true
                )
                messageSender.broadcastMessage(message, BroadcastMessageTarget.AllUsers(MAX_RECEIVERS))
            }
        }
    }

    internal companion object {
        private const val MAX_RECEIVERS = 500
    }
}
