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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.conversation.SubConversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.SystemMessageInserterArrangement
import com.wire.kalium.logic.util.arrangement.SystemMessageInserterArrangementImpl
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.SubconversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.SubconversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.FetchConversationUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.FetchConversationUseCaseArrangementImpl
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
            withFetchConversationSucceeding()
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
            withFetchConversationSucceeding()
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldSucceed()

        coVerify {
            arrangement.fetchConversation(eq(CONVERSATION_ID))
        }.wasInvoked(once)
    }

    @Test
    fun givenEpochIsLatest_whenHandlingStaleEpoch_thenShouldNotRejoinTheConversation() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(false))
            withFetchConversationSucceeding()
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldSucceed()

        coVerify {
            arrangement.joinExistingMLSConversationUseCase.invoke(CONVERSATION_ID, null)
        }.wasNotInvoked()
    }

    @Test
    fun givenStaleEpoch_whenHandlingStaleEpoch_thenShouldRejoinTheConversation() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(true))
            withFetchConversationSucceeding()
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
            withJoinExistingMLSConversationUseCaseReturning(Either.Right(Unit))
            withInsertLostCommitSystemMessage(Either.Right(Unit))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldSucceed()

        coVerify {
            arrangement.joinExistingMLSConversationUseCase.invoke(CONVERSATION_ID, null)
        }.wasInvoked(once)
    }

    @Test
    fun givenRejoiningFails_whenHandlingStaleEpoch_thenShouldNotInsertLostCommitSystemMessage() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(true))
            withFetchConversationSucceeding()
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
            withFetchConversationSucceeding()
            withGetConversationProtocolInfo(Either.Right(TestConversation.MLS_PROTOCOL_INFO))
            withJoinExistingMLSConversationUseCaseReturning(Either.Right(Unit))
            withInsertLostCommitSystemMessage(Either.Right(Unit))
        }

        staleEpochHandler.verifyEpoch(CONVERSATION_ID).shouldSucceed()

        coVerify {
            arrangement.systemMessageInserter.insertLostCommitSystemMessage(eq(CONVERSATION_ID), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenSubconversationIdAndValidEpoch_WhenVerified_ThenShouldSucceed() = runTest {
        val subConversationId = SubconversationId("subconversation-id")
        val (arrangement, staleEpochHandler) = arrange {
            withFetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId, Either.Right(TestSubConversationDetails))
            withIsGroupOutOfSync(Either.Right(false))
        }

        val result = staleEpochHandler.verifyEpoch(CONVERSATION_ID, subConversationId, null)

        result.shouldSucceed()

        coVerify {
            arrangement.subconversationRepository.fetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId)
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsConversationRepository.isGroupOutOfSync(
                TestSubConversationDetails.groupId,
                TestSubConversationDetails.epoch
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenSubconversationIdAndStaleEpoch_WhenVerified_ThenShouldJoinUsingExternalCommit() = runTest {
        val subConversationId = SubconversationId("subconversation-id")
        val (arrangement, staleEpochHandler) = arrange {
            withFetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId, Either.Right(TestSubConversationDetails))
            withIsGroupOutOfSync(Either.Right(true))
            withFetchRemoteSubConversationGroupInfo(CONVERSATION_ID, subConversationId, Either.Right(TestGroupInfo))
            withJoinGroupByExternalCommit(TestSubConversationDetails.groupId, TestGroupInfo, Either.Right(Unit))
        }

        val result = staleEpochHandler.verifyEpoch(CONVERSATION_ID, subConversationId, null)

        result.shouldSucceed()

        coVerify {
            arrangement.subconversationRepository.fetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId)
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(TestSubConversationDetails.groupId, TestGroupInfo)
        }.wasInvoked(once)
    }

    @Test
    fun givenSubconversationIdAndFetchDetailsFails_WhenVerified_ThenShouldFail() = runTest {
        val subConversationId = SubconversationId("subconversation-id")
        val (arrangement, staleEpochHandler) = arrange {
            withFetchRemoteSubConversationDetails(
                CONVERSATION_ID,
                subConversationId,
                Either.Left(NetworkFailure.NoNetworkConnection(null))
            )
        }

        val result = staleEpochHandler.verifyEpoch(CONVERSATION_ID, subConversationId, null)

        result.shouldFail()

        coVerify {
            arrangement.subconversationRepository.fetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId)
        }.wasInvoked(once)
    }

    @Test
    fun givenSubconversationIdAndExternalCommitFails_WhenVerified_ThenShouldFail() = runTest {
        val subConversationId = SubconversationId("subconversation-id")
        val (arrangement, staleEpochHandler) = arrange {
            withFetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId, Either.Right(TestSubConversationDetails))
            withIsGroupOutOfSync(Either.Right(true))
            withFetchRemoteSubConversationGroupInfo(CONVERSATION_ID, subConversationId, Either.Right(TestGroupInfo))
            withJoinGroupByExternalCommit(
                TestSubConversationDetails.groupId,
                TestGroupInfo,
                Either.Left(CoreFailure.Unknown(null))
            )
        }

        val result = staleEpochHandler.verifyEpoch(CONVERSATION_ID, subConversationId, null)

        result.shouldFail()

        coVerify {
            arrangement.subconversationRepository.fetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId)
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(TestSubConversationDetails.groupId, TestGroupInfo)
        }.wasInvoked(once)
    }

    @Test
    fun givenSubconversationId_WhenVerified_ThenShouldNotCallFetchConversation() = runTest {
        val subConversationId = SubconversationId("subconversation-id")
        val (arrangement, staleEpochHandler) = arrange {
            withFetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId, Either.Right(TestSubConversationDetails))
            withIsGroupOutOfSync(Either.Right(false))
        }

        val result = staleEpochHandler.verifyEpoch(CONVERSATION_ID, subConversationId, null)

        result.shouldSucceed()

        coVerify {
            arrangement.subconversationRepository.fetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId)
        }.wasInvoked(once)

        coVerify {
            arrangement.fetchConversation(any())
        }.wasNotInvoked()
    }


    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        SystemMessageInserterArrangement by SystemMessageInserterArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl(),
        SubconversationRepositoryArrangement by SubconversationRepositoryArrangementImpl(),
        FetchConversationUseCaseArrangement by FetchConversationUseCaseArrangementImpl(),
        JoinExistingMLSConversationUseCaseArrangement by JoinExistingMLSConversationUseCaseArrangementImpl() {
        suspend fun arrange() = run {
            block()
            this@Arrangement to StaleEpochVerifierImpl(
                systemMessageInserter = systemMessageInserter,
                conversationRepository = conversationRepository,
                subconversationRepository = subconversationRepository,
                mlsConversationRepository = mlsConversationRepository,
                joinExistingMLSConversation = joinExistingMLSConversationUseCase,
                fetchConversation = fetchConversation
            )
        }
    }

    private companion object {
        suspend fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        val CONVERSATION_ID = ConversationId("conversation-value", "conversation-domain")

        val TestSubConversationDetails = SubConversation(
            id = SubconversationId("subconversation-value"),
            parentId = CONVERSATION_ID,
            groupId = GroupID("sub-group-id"),
            epoch = 123UL,
            epochTimestamp = null,
            mlsCipherSuiteTag = null,
            members = emptyList()
        )

        val TestGroupInfo = ByteArray(0)
    }
}
