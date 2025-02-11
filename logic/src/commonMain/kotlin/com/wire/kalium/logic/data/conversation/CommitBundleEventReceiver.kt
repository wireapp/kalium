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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.EventReceiver
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler

internal interface CommitBundleEventReceiver : EventReceiver<Event.Conversation>

internal class CommitBundleEventReceiverImpl(
    private val memberJoinEventHandler: MemberJoinEventHandler,
    private val memberLeaveEventHandler: MemberLeaveEventHandler
) : CommitBundleEventReceiver {
    override suspend fun onEvent(event: Event.Conversation, deliveryInfo: EventDeliveryInfo): Either<CoreFailure, Unit> {
        return when (event) {
            is Event.Conversation.MemberJoin -> memberJoinEventHandler.handle(event)
            is Event.Conversation.MemberLeave -> memberLeaveEventHandler.handle(event)
            else -> {
                // This should never happen. If it does, we assume a catastrophic failure and stop event processing.
                val exception = IllegalArgumentException("Unexpected event received by commit bundle: ${event.toLogString()}")
                kaliumLogger.e("Unexpected event received by commit bundle: ${event.toLogString()}", exception)
                Either.Left(MLSFailure.Generic(exception))
            }
        }
    }
}
