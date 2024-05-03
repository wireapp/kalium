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
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class StaleEpochVerifierTest {

    @Test
    fun givenConversationIsNotMLS_whenHandlingStaleEpoch_thenShouldNotInsertWarning() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(TestConversation.PROTEUS_PROTOCOL_INFO))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldFail()

        coVerify {
            arrangement.systemMessageInserter.insertLostCommitSystemMessage(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMLSConversation_whenHandlingStaleEpoch_thenShouldFetchConversationAgain() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(false))
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldSucceed()

        coVerify {
            arrangement.conversationRepository.fetchConversation(eq(CONVERSATION_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenEpochIsLatest_whenHandlingStaleEpoch_thenShouldNotRejoinTheConversation() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(false))
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldSucceed()

        coVerify {
            arrangement.joinExistingMLSConversationUseCase.invoke(eq(CONVERSATION_ID))
        }.wasNotInvoked()
    }

    @Test
    fun givenStaleEpoch_whenHandlingStaleEpoch_thenShouldRejoinTheConversation() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(true))
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
            withJoinExistingMLSConversationUseCaseReturning(Either.Right(Unit))
            withInsertLostCommitSystemMessage(Either.Right(Unit))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldSucceed()

        coVerify {
            arrangement.joinExistingMLSConversationUseCase.invoke(eq(CONVERSATION_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenRejoiningFails_whenHandlingStaleEpoch_thenShouldNotInsertLostCommitSystemMessage() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(true))
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
            withJoinExistingMLSConversationUseCaseReturning(Either.Left(NetworkFailure.NoNetworkConnection(null)))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldFail()

        coVerify {
            arrangement.systemMessageInserter.insertLostCommitSystemMessage(eq(CONVERSATION_ID), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConversationIsRejoined_whenHandlingStaleEpoch_thenShouldInsertLostCommitSystemMessage() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(true))
            withFetchConversation(Either.Right(Unit))
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
            withJoinExistingMLSConversationUseCaseReturning(Either.Right(Unit))
            withInsertLostCommitSystemMessage(Either.Right(Unit))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldSucceed()

        coVerify {
            arrangement.systemMessageInserter.insertLostCommitSystemMessage(eq(CONVERSATION_ID), any())
        }.wasInvoked(once)
    }


    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        SystemMessageInserterArrangement by SystemMessageInserterArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl(),
        JoinExistingMLSConversationUseCaseArrangement by JoinExistingMLSConversationUseCaseArrangementImpl()
    {
        suspend fun arrange() = run {
            block()
            this@Arrangement to StaleEpochVerifierImpl(
                systemMessageInserter = systemMessageInserter,
                conversationRepository = conversationRepository,
                mlsConversationRepository = mlsConversationRepository,
                joinExistingMLSConversation = joinExistingMLSConversationUseCase
            )
        }
    }

    private companion object {
        suspend fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        val CONVERSATION_ID = TestConversation.ID
    }
}
