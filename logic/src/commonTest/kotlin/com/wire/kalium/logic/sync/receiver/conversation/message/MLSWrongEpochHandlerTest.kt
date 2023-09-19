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
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.thenReturnSequentially
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MLSWrongEpochHandlerTest {

    @Test
    fun givenConversationIsNotMLS_whenHandlingEpochFailure_thenShouldNotInsertWarning() = runTest {
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withProtocolByIdReturningSequence(Either.Right(proteusProtocol))
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertLostCommitSystemMessage)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenConversationIsNotMLS_whenHandlingEpochFailure_thenShouldNotFetchConversationAgain() = runTest {
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withProtocolByIdReturningSequence(Either.Right(proteusProtocol))
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversation)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenMLSConversation_whenHandlingEpochFailure_thenShouldFetchConversationAgain() = runTest {
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withProtocolByIdReturning(Either.Right(mlsProtocol))
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversation)
            .with(eq(conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUpdatedMLSConversationHasDifferentEpoch_whenHandlingEpochFailure_thenShouldRejoinTheConversation() = runTest {
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withProtocolByIdReturningSequence(
                Either.Right(mlsProtocol),
                Either.Right(mlsProtocolWithUpdatedEpoch)
            )
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.joinExistingMLSConversationUseCase)
            .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
            .with(eq(conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUpdatedMLSConversationHasSameEpoch_whenHandlingEpochFailure_thenShouldNotRejoinTheConversation() = runTest {
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withProtocolByIdReturning(Either.Right(mlsProtocol))
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.joinExistingMLSConversationUseCase)
            .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenRejoiningFails_whenHandlingEpochFailure_thenShouldNotPersistAnyMessage() = runTest {
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withProtocolByIdReturningSequence(
                Either.Right(mlsProtocol),
                Either.Right(mlsProtocolWithUpdatedEpoch)
            )
            .withJoinExistingConversationReturning(Either.Left(CoreFailure.Unknown(null)))
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertLostCommitSystemMessage)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenConversationIsRejoined_whenHandlingEpochFailure_thenShouldInsertMLSWarningWithCorrectDateAndConversation() = runTest {
        val date = "date"
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withProtocolByIdReturningSequence(
                Either.Right(mlsProtocol),
                Either.Right(mlsProtocolWithUpdatedEpoch)
            )
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, date)

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertLostCommitSystemMessage)
            .with(eq(conversationId), eq(date))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val systemMessageInserter = mock(classOf<SystemMessageInserter>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val eventRepository = mock(classOf<EventRepository>())

        @Mock
        val joinExistingMLSConversationUseCase = mock(classOf<JoinExistingMLSConversationUseCase>())

        init {
            withFetchByIdSucceeding()
            withInsertLostCommitSystemMessageSucceeding()
            withJoinExistingConversationSucceeding()
        }

        fun withFetchByIdReturning(result: Either<CoreFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversation)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withFetchByIdSucceeding() = withFetchByIdReturning(Either.Right(Unit))

        fun withProtocolByIdReturning(result: Either<StorageFailure, Conversation.ProtocolInfo>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationProtocolInfo)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withProtocolByIdReturningSequence(vararg results: Either<StorageFailure, Conversation.ProtocolInfo>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationProtocolInfo)
                .whenInvokedWith(any())
                .thenReturnSequentially(*results)
        }

        fun withInsertLostCommitSystemMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            given(systemMessageInserter)
                .suspendFunction(systemMessageInserter::insertLostCommitSystemMessage)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withInsertLostCommitSystemMessageSucceeding() = withInsertLostCommitSystemMessageReturning(Either.Right(Unit))

        fun withJoinExistingConversationReturning(result: Either<CoreFailure, Unit>) = apply {
            given(joinExistingMLSConversationUseCase)
                .suspendFunction(joinExistingMLSConversationUseCase::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withJoinExistingConversationSucceeding() = withJoinExistingConversationReturning(Either.Right(Unit))

        fun arrange() = this to MLSWrongEpochHandlerImpl(
            systemMessageInserter,
            conversationRepository,
            joinExistingMLSConversationUseCase
        )
    }

    private companion object {
        val conversationId = TestConversation.CONVERSATION.id
        val proteusProtocol = Conversation.ProtocolInfo.Proteus

        val mlsProtocol = TestConversation.MLS_CONVERSATION.protocol as Conversation.ProtocolInfo.MLS
        val mlsProtocolWithUpdatedEpoch = mlsProtocol.copy(epoch = mlsProtocol.epoch + 1U)
    }
}
