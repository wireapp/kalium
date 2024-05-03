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
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdateHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdatedHandler
import com.wire.kalium.logic.util.arrangement.dao.ConversionDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.ConversionDAOArrangementImpl
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.persistence.dao.ConversationIDEntity
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CodeUpdateHandlerTest {

    @Test
    fun givenCodeUpdateEvent_whenHandlerIsInvoked_thenCodeIsUpdated() = runTest {
        val (arrangement, handler) = Arrangement().arrange {
            withUpdatedGuestRoomLink()
        }

        val event = Event.Conversation.CodeUpdated(
            conversationId = ConversationId("conversationId", "domain"),
            uri = "uri",
            isPasswordProtected = true,
            code = "code",
            key = "key",
            id = "event-id",
        )

        handler.handle(event)

        coVerify {
            arrangement.conversionDAO.updateGuestRoomLink(
                eq(
                    ConversationIDEntity(
                        event.conversationId.value,
                        event.conversationId.domain
                    )
                ),
                eq(event.uri!!),
                eq(event.isPasswordProtected)
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUriIsNull_whenUpdating_thenGenerateCodeFromKeyAndCode() = runTest {
        val (arrangement, handler) = Arrangement().arrange {
            withUpdatedGuestRoomLink()
        }

        val event = Event.Conversation.CodeUpdated(
            conversationId = ConversationId("conversationId", "domain"),
            uri = null,
            isPasswordProtected = true,
            code = "code",
            key = "key",
            id = "event-id",
        )

        val expected = "${arrangement.serverConfigLinks.accounts}?key=${event.key}&code=${event.code}"

        handler.handle(event)

        coVerify {
            arrangement.conversionDAO.updateGuestRoomLink(
                eq(
                    ConversationIDEntity(
                        event.conversationId.value,
                        event.conversationId.domain
                    )
                ),
                eq(expected),
                eq(event.isPasswordProtected)
            )
        }.wasInvoked(exactly = once)
    }

    private class Arrangement : ConversionDAOArrangement by ConversionDAOArrangementImpl() {

        val serverConfigLinks = newServerConfig(1).links

        private val handler: CodeUpdatedHandler = CodeUpdateHandlerImpl(conversionDAO, serverConfigLinks)

        fun arrange(block: suspend Arrangement.() -> Unit) = run {
            runBlocking { block() }
            this to handler
        }
    }
}
