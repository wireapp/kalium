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
package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.NetworkFailure
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
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class StaleEpochHandlerTest {

    @Test
    fun givenConversationIsNotMLS_whenHandlingStaleEpoch_thenShouldNotInsertWarning() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(TestConversation.PROTEUS_PROTOCOL_INFO))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldFail()

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertLostCommitSystemMessage)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenMLSConversation_whenHandlingStaleEpoch_thenShouldFetchConversationAgain() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(false))
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldSucceed()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversation)
            .with(eq(CONVERSATION_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenEpochIsLatest_whenHandlingStaleEpoch_thenShouldNotRejoinTheConversation() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(false))
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO.copy(
                epochTimestamp = Clock.System.now().minus(60.minutes)
            )))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldSucceed()

        verify(arrangement.joinExistingMLSConversationUseCase)
            .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
            .with(eq(CONVERSATION_ID))
            .wasNotInvoked()
    }

    @Test
    fun givenStaleEpochAndEpochTimestampIsOlderThanOneHour_whenHandlingStaleEpoch_thenShouldRejoinTheConversation() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(true))
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO.copy(
                epochTimestamp = Clock.System.now().minus(60.minutes)
            )))
            withJoinExistingMLSConversationUseCaseReturning(Either.Right(Unit))
            withInsertLostCommitSystemMessage(Either.Right(Unit))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldSucceed()

        verify(arrangement.joinExistingMLSConversationUseCase)
            .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
            .with(eq(CONVERSATION_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenStaleEpochAndEpochTimestampIsNewerThanOneHour_whenHandlingEpochFailure_thenShouldNotRejoinTheConversation() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(true))
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO.copy(
                epochTimestamp = Clock.System.now().minus(59.minutes)
            )))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldSucceed()

        verify(arrangement.joinExistingMLSConversationUseCase)
            .suspendFunction(arrangement.joinExistingMLSConversationUseCase::invoke)
            .with(eq(CONVERSATION_ID))
            .wasNotInvoked()
    }

    @Test
    fun givenRejoiningFails_whenHandlingStaleEpoch_thenShouldNotInsertLostCommitSystemMessage() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(true))
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO.copy(
                epochTimestamp = Clock.System.now().minus(60.minutes)
            )))
            withJoinExistingMLSConversationUseCaseReturning(Either.Left(NetworkFailure.NoNetworkConnection(null)))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldFail()

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertLostCommitSystemMessage)
            .with(eq(CONVERSATION_ID), any())
            .wasNotInvoked()
    }

    @Test
    fun givenConversationIsRejoined_whenHandlingStaleEpoch_thenShouldInsertLostCommitSystemMessage() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(true))
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO.copy(
                epochTimestamp = Clock.System.now().minus(60.minutes)
            )))
            withJoinExistingMLSConversationUseCaseReturning(Either.Right(Unit))
            withInsertLostCommitSystemMessage(Either.Right(Unit))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldSucceed()

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertLostCommitSystemMessage)
            .with(eq(CONVERSATION_ID), any())
            .wasInvoked(once)
    }


    private class Arrangement(private val block: Arrangement.() -> Unit) :
        SystemMessageInserterArrangement by SystemMessageInserterArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl(),
        JoinExistingMLSConversationUseCaseArrangement by JoinExistingMLSConversationUseCaseArrangementImpl()
    {
        fun arrange() = run {
            block()
            this@Arrangement to StaleEpochHandlerImpl(
                systemMessageInserter = systemMessageInserter,
                conversationRepository = conversationRepository,
                mlsConversationRepository = mlsConversationRepository,
                joinExistingMLSConversation = joinExistingMLSConversationUseCase
            )
        }
    }

    private companion object {
        fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        val CONVERSATION_ID = TestConversation.ID
    }
}
