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
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.thenReturnSequentially
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
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
            .withConversationByIdReturningSequence(Either.Right(proteusConversation))
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(mlsConversation.id, "date")

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenConversationIsNotMLS_whenHandlingEpochFailure_thenShouldNotFetchConversationAgain() = runTest {
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withConversationByIdReturningSequence(Either.Right(proteusConversation))
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(mlsConversation.id, "date")

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversation)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenMLSConversation_whenHandlingEpochFailure_thenShouldFetchConversationAgain() = runTest {
        val conversationId = mlsConversation.id
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withConversationByIdReturning(Either.Right(mlsConversation))
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversation)
            .with(eq(conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUpdatedMLSConversationHasDifferentEpoch_whenHandlingEpochFailure_thenShouldRejoinTheConversation() = runTest {
        val conversationId = mlsConversation.id
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withConversationByIdReturningSequence(
                Either.Right(mlsConversation),
                Either.Right(mlsConversationWithUpdatedEpoch)
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
        val conversationId = mlsConversation.id
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withConversationByIdReturning(Either.Right(mlsConversation))
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.joinExistingMLSConversationUseCase)
            .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenRejoiningFails_whenHandlingEpochFailure_thenShouldNotPersistAnyMessage() = runTest {
        val conversationId = mlsConversation.id
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withConversationByIdReturningSequence(
                Either.Right(mlsConversation),
                Either.Right(mlsConversationWithUpdatedEpoch)
            )
            .withJoinExistingConversationReturning(Either.Left(CoreFailure.Unknown(null)))
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenConversationIsRejoined_whenHandlingEpochFailure_thenShouldInsertMLSWarningWithCorrectDateAndConversation() = runTest {
        val conversationId = mlsConversation.id
        val date = "date"
        val (arrangement, mlsWrongEpochHandler) = Arrangement()
            .withConversationByIdReturningSequence(
                Either.Right(mlsConversation),
                Either.Right(mlsConversationWithUpdatedEpoch)
            )
            .arrange()

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, date)

        verify(arrangement.persistMessageUseCase)
            .suspendFunction(arrangement.persistMessageUseCase::invoke)
            .with(
                matching {
                    it.conversationId == conversationId &&
                    it.content == MessageContent.MLSWrongEpochWarning &&
                    it.date == date
                }
            )
            .wasInvoked(exactly = once)
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
            withPersistMessageSucceeding()
            withJoinExistingConversationSucceeding()
        }

        fun withFetchByIdReturning(result: Either<CoreFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversation)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withFetchByIdSucceeding() = withFetchByIdReturning(Either.Right(Unit))

        fun withConversationByIdReturning(result: Either<StorageFailure, Conversation>) = apply {
            given(conversationRepository)
                    .suspendFunction(conversationRepository::baseInfoById)
                    .whenInvokedWith(any())
                    .thenReturn(result)
        }

        fun withConversationByIdReturningSequence(vararg results: Either<StorageFailure, Conversation>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::baseInfoById)
                .whenInvokedWith(any())
                .thenReturnSequentially(*results)
        }

        fun withPersistMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            given(persistMessageUseCase)
                .suspendFunction(persistMessageUseCase::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withPersistMessageSucceeding() = withPersistMessageReturning(Either.Right(Unit))

        fun withJoinExistingConversationReturning(result: Either<CoreFailure, Unit>) = apply {
            given(joinExistingMLSConversationUseCase)
                .suspendFunction(joinExistingMLSConversationUseCase::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withJoinExistingConversationSucceeding() = withJoinExistingConversationReturning(Either.Right(Unit))

        fun arrange() = this to MLSWrongEpochHandlerImpl(
            TestUser.SELF.id,
            persistMessageUseCase,
            conversationRepository,
            joinExistingMLSConversationUseCase
        )
    }

    private companion object {
        val mlsConversation = TestConversation.MLS_CONVERSATION
        val proteusConversation = mlsConversation.copy(protocol = Conversation.ProtocolInfo.Proteus)

        val originalProtocolInfo = mlsConversation.protocol as Conversation.ProtocolInfo.MLS
        val protocolWithUpdatedEpoch = originalProtocolInfo.copy(epoch = originalProtocolInfo.epoch + 1U)
        val mlsConversationWithUpdatedEpoch = mlsConversation.copy(protocol = protocolWithUpdatedEpoch)
    }
}
