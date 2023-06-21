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
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MLSWrongEpochHandlerTest {

    @Test
    fun givenConversationIsNotMLS_whenHandlingEpochFailure_thenShouldNotInsertWarning() = runTest {
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withConversationByIdReturning { Either.Right(conversation.copy(protocol = Conversation.ProtocolInfo.Proteus)) }
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(conversation.id, "date")

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenConversationIsNotMLS_whenHandlingEpochFailure_thenShouldNotFetchConversationAgain() = runTest {
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withConversationByIdReturning { Either.Right(conversation.copy(protocol = Conversation.ProtocolInfo.Proteus)) }
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(conversation.id, "date")

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(any())
            .wasNotInvoked()
    }
    private class Arrangement {

        @Mock
        val persistMessageUseCase = mock(classOf<PersistMessageUseCase>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val joinExistingMLSConversationUseCase = mock(classOf<JoinExistingMLSConversationUseCase>())

        init {
            withFetchByIdSucceeding()
        }

        fun withFetchByIdReturning(result: Either<CoreFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversation)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withFetchByIdSucceeding() = withFetchByIdReturning(Either.Right(Unit))

        fun withConversationByIdReturning(resultProvider: () -> Either<StorageFailure, Conversation>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::baseInfoById)
                .whenInvokedWith(any())
                .thenInvoke(resultProvider)
        }

        fun arrange() = this to MLSWrongEpochHandlerImpl(
            TestUser.SELF.id,
            persistMessageUseCase,
            conversationRepository,
            joinExistingMLSConversationUseCase
        )
    }

    private companion object {
        val conversation = TestConversation.CONVERSATION
    }
}
