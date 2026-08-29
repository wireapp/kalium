/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.sync.receiver.meeting.MeetingCreateEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingDeleteEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingMemberAddEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingUpdateEventHandler

public class MeetingEventReceiverImpl public constructor(
    private val meetingCreateEventHandler: MeetingCreateEventHandler,
    private val meetingDeleteEventHandler: MeetingDeleteEventHandler,
    private val meetingUpdateEventHandler: MeetingUpdateEventHandler,
    private val meetingMemberAddEventHandler: MeetingMemberAddEventHandler,
) : MeetingEventReceiver {

    override suspend fun onEvent(
        transactionContext: CryptoTransactionContext,
        event: Event.Meeting,
        deliveryInfo: EventDeliveryInfo
    ): Either<CoreFailure, Unit> = when (event) {
        is Event.Meeting.Create -> meetingCreateEventHandler.handle(event)
        is Event.Meeting.Delete -> meetingDeleteEventHandler.handle(event)
        is Event.Meeting.Update -> meetingUpdateEventHandler.handle(event)
        is Event.Meeting.MemberAdd -> meetingMemberAddEventHandler.handle(event)
    }

}
