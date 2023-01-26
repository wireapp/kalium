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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.EventReceiver
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler

interface CommitBundleEventReceiver : EventReceiver<Event.Conversation>

class CommitBundleEventReceiverImpl(
    private val memberJoinEventHandler: MemberJoinEventHandler,
    private val memberLeaveEventHandler: MemberLeaveEventHandler
) : CommitBundleEventReceiver {
    override suspend fun onEvent(event: Event.Conversation) {
        when (event) {
            is Event.Conversation.MemberJoin -> memberJoinEventHandler.handle(event)
            is Event.Conversation.MemberLeave -> memberLeaveEventHandler.handle(event)
            else -> kaliumLogger.w("Unexpected event received by commit bundle: $event")
        }
    }
}
