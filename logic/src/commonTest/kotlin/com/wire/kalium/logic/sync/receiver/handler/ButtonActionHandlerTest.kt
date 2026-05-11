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
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ButtonActionHandlerTest {

    @Test
    fun givenContentWithButtonId_whenHandlingEvent_thenThatButtonIdAsSelected() = runTest {
        val convId = CONVERSATION_ID
        val senderId = TestUser.SELF.id
        val content = MessageContent.ButtonAction(
            referencedMessageId = "messageId",
            buttonId = "buttonId"
        )
        val (arrangement, handler) = Arrangement().withMarkSelected().arrange()

        handler.handle(convId, senderId, content.referencedMessageId, content.buttonId)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.compositeMessageRepository.markSelected(eq("messageId"), eq(convId), eq("buttonId"))
        }
    }

    @Test
    fun givenSenderIdIsNotTheSameAsSelfUserSender_whenHandlingEvent_thenIgnore() = runTest {
        val convId = CONVERSATION_ID
        val senderId = TestUser.OTHER_USER_ID

        val content = MessageContent.ButtonAction(
            referencedMessageId = "messageId",
            buttonId = "buttonId"
        )

        val (arrangement, handler) = Arrangement().withMarkSelected().arrange()

        handler.handle(convId, senderId, content.referencedMessageId, content.buttonId)

        verifySuspend(VerifyMode.not) {
            arrangement.compositeMessageRepository.markSelected(any(), any(), any())
        }
    }

    private companion object {
        val CONVERSATION_ID = ConversationId("conversationId", "domain")
    }

    private class Arrangement {

        val compositeMessageRepository = mock<CompositeMessageRepository>()

        suspend fun withMarkSelected(result: Either<StorageFailure, Unit> = Unit.right()) = apply {
            everySuspend { compositeMessageRepository.markSelected(any(), any(), any()) } returns result
        }

        fun arrange() = this to ButtonActionHandlerImpl(
            compositeMessageRepository = compositeMessageRepository,
            selfUserId = TestUser.SELF.id
        )
    }
}
