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
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandlerImpl
import com.wire.kalium.logic.util.arrangement.dao.ConversionDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.ConversionDAOArrangementImpl
import com.wire.kalium.persistence.dao.ConversationIDEntity
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CodeDeletedHandlerTest {

    @Test
    fun givenCodeUpdateEvent_whenHandlerIsInvoked_thenCodeIsUpdated() = runTest {
        val (arrangement, handler) = Arrangement().arrange {
            withDeleteGustLink()
        }

        val event = Event.Conversation.CodeDeleted(
            conversationId = ConversationId("conversationId", "domain"),
            id = "event-id",
        )

        handler.handle(event)

        verify(arrangement.conversionDAO)
            .suspendFunction(arrangement.conversionDAO::deleteGuestRoomLink)
            .with(
                eq(ConversationIDEntity(
                    event.conversationId.value,
                    event.conversationId.domain
                ))
            ).wasInvoked(exactly = once)
    }

    private class Arrangement : ConversionDAOArrangement by ConversionDAOArrangementImpl() {

        private val handler: CodeDeletedHandler = CodeDeletedHandlerImpl(conversionDAO)

        fun arrange(block: Arrangement.() -> Unit) = apply(block).run {
            this to handler
        }
    }
}
