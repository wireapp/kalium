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
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.SystemMessageInserterArrangement
import com.wire.kalium.logic.util.arrangement.SystemMessageInserterArrangementImpl
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.JoinExistingMLSConversationUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.JoinExistingMLSConversationUseCaseArrangementImpl
import io.mockative.any
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MLSWrongEpochHandlerTest {

    @Test
    fun givenConversationIsNotMLS_whenHandlingEpochFailure_thenShouldNotInsertWarning() = runTest {
        val (arrangement, mlsWrongEpochHandler) = arrange {
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(proteusProtocol))
        }

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertLostCommitSystemMessage)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenMLSConversation_whenHandlingEpochFailure_thenShouldFetchConversationAgain() = runTest {
        val (arrangement, mlsWrongEpochHandler) = arrange {
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(mlsProtocol))
            withIsGroupOutOfSync(Either.Right(false))
        }

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversation)
            .with(eq(conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUpdatedMLSConversationHasDifferentEpoch_whenHandlingEpochFailure_thenShouldRejoinTheConversation() = runTest {
        val (arrangement, mlsWrongEpochHandler) = arrange {
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(mlsProtocol))
            withIsGroupOutOfSync(Either.Right(true))
            withJoinExistingMLSConversationUseCaseReturning(Either.Right(Unit))
            withInsertLostCommitSystemMessage(Either.Right(Unit))
        }

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.joinExistingMLSConversationUseCase)
            .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
            .with(eq(conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUpdatedMLSConversationHasSameEpoch_whenHandlingEpochFailure_thenShouldNotRejoinTheConversation() = runTest {
        val (arrangement, mlsWrongEpochHandler) = arrange {
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(mlsProtocol))
            withIsGroupOutOfSync(Either.Right(false))
        }

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.joinExistingMLSConversationUseCase)
            .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenRejoiningFails_whenHandlingEpochFailure_thenShouldNotPersistAnyMessage() = runTest {
        val (arrangement, mlsWrongEpochHandler) = arrange {
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(mlsProtocol))
            withIsGroupOutOfSync(Either.Right(true))
            withJoinExistingMLSConversationUseCaseReturning(Either.Left(CoreFailure.Unknown(null)))
        }

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, "date")

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertLostCommitSystemMessage)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenConversationIsRejoined_whenHandlingEpochFailure_thenShouldInsertMLSWarningWithCorrectDateAndConversation() = runTest {
        val date = "date"
        val (arrangement, mlsWrongEpochHandler) = arrange {
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(mlsProtocol))
            withIsGroupOutOfSync(Either.Right(true))
            withJoinExistingMLSConversationUseCaseReturning(Either.Right(Unit))
            withInsertLostCommitSystemMessage(Either.Right(Unit))
        }

        mlsWrongEpochHandler.onMLSWrongEpoch(conversationId, date)

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertLostCommitSystemMessage)
            .with(eq(conversationId), eq(date))
            .wasInvoked(exactly = once)
    }

    private class Arrangement(private val block: Arrangement.() -> Unit) :
        SystemMessageInserterArrangement by SystemMessageInserterArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl(),
        JoinExistingMLSConversationUseCaseArrangement by JoinExistingMLSConversationUseCaseArrangementImpl()
    {
        fun arrange() = run {
            block()
            this@Arrangement to MLSWrongEpochHandlerImpl(
                systemMessageInserter,
                conversationRepository,
                mlsConversationRepository,
                joinExistingMLSConversationUseCase
            )
        }
    }

    private companion object {
        fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        val conversationId = TestConversation.CONVERSATION.id
        val proteusProtocol = Conversation.ProtocolInfo.Proteus
        val mlsProtocol = TestConversation.MLS_CONVERSATION.protocol as Conversation.ProtocolInfo.MLS
    }
}
