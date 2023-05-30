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

import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.ConversationEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NewGroupConversationStartedMessageCreatorTest {

    @Test
    fun givenASuccessConversationResponse_whenPersistingAGroupConversation_ThenShouldCreateASystemMessage() = runTest {
        val (arrangement, sysMessageCreator) = Arrangement()
            .withPersistMessageSuccess()
            .arrange()

        val result = sysMessageCreator.createSystemMessage(
            TestConversation.ENTITY.copy(type = ConversationEntity.Type.GROUP)
        )

        result.shouldSucceed()

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                (it.content is MessageContent.System && it.content is MessageContent.ConversationCreated)
            })
            .wasInvoked(once)
    }

    @Test
    fun givenASuccessConversationResponse_whenPersistingNOTAGroupConversation_ThenShouldNOTCreateASystemMessage() = runTest {
        val (arrangement, newGroupConversationCreatedHandler) = Arrangement()
            .withPersistMessageSuccess()
            .arrange()

        val result = newGroupConversationCreatedHandler.createSystemMessage(
            TestConversation.ENTITY.copy(type = ConversationEntity.Type.ONE_ON_ONE)
        )

        result.shouldSucceed()

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                (it.content is MessageContent.System && it.content is MessageContent.ConversationCreated)
            })
            .wasNotInvoked()
    }

    private class Arrangement {
        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        fun withPersistMessageSuccess() = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .then { Either.Right(Unit) }
        }

        fun arrange() = this to NewGroupConversationStartedMessageCreatorImpl(
            persistMessage, TestUser.SELF.id,
        )
    }

}
