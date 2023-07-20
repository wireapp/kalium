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
package com.wire.kalium.logic.sync.handler

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandler
import com.wire.kalium.logic.sync.receiver.handler.ButtonActionConfirmationHandlerImpl
import com.wire.kalium.logic.util.arrangement.repository.CompositeMessageRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.CompositeMessageRepositoryArrangementImpl
import io.mockative.any
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ButtonActionConfirmationHandlerTest {

    @Test
    fun givenContentWithButtonId_whenHandlingEvent_thenThatButtonIdAsSelected() = runTest {
        val convId = CONVERSATION_ID
        val content = MessageContent.ButtonActionConfirmation(
            referencedMessageId = "messageId",
            buttonId = "buttonId"
        )
        val (arrangement, handler) = Arrangement().arrange {
            withMarkSelected(result = Either.Right(Unit))
        }

        handler.handle(convId, content)

        verify(arrangement.compositeMessageRepository)
            .suspendFunction(arrangement.compositeMessageRepository::markSelected)
            .with(eq("messageId"), eq(convId), eq("buttonId"))
            .wasInvoked(exactly = once)

        verify(arrangement.compositeMessageRepository)
            .suspendFunction(arrangement.compositeMessageRepository::resetSelection)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenContentWithNoButtonId_whenHandlingEvent_thenThanSelectionIsReset() = runTest {
        val convId = CONVERSATION_ID
        val content = MessageContent.ButtonActionConfirmation(
            referencedMessageId = "messageId",
            buttonId = null
        )

        val (arrangement, handler) = Arrangement().arrange {
             withClearSelection(result = Either.Right(Unit))
        }

        handler.handle(convId, content)

        verify(arrangement.compositeMessageRepository)
            .suspendFunction(arrangement.compositeMessageRepository::markSelected)
            .with(any(), any(), any())
            .wasNotInvoked()


        verify(arrangement.compositeMessageRepository)
            .suspendFunction(arrangement.compositeMessageRepository::resetSelection)
            .with(eq("messageId"), eq(convId))
            .wasInvoked(exactly = once)
    }

private companion object {
    val CONVERSATION_ID = ConversationId("conversationId", "domain")
}

private class Arrangement : CompositeMessageRepositoryArrangement by CompositeMessageRepositoryArrangementImpl() {

    private val handler: ButtonActionConfirmationHandler = ButtonActionConfirmationHandlerImpl(
        compositeMessageRepository = compositeMessageRepository
    )

    fun arrange(block: Arrangement.() -> Unit): Pair<Arrangement, ButtonActionConfirmationHandler> {
        block()
        return this to handler
    }

}
}
