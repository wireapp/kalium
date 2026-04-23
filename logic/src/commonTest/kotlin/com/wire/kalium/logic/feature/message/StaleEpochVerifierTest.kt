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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationSyncReason
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.SubConversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.SystemMessageInserterArrangement
import com.wire.kalium.logic.util.arrangement.SystemMessageInserterArrangementImpl
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.mls.MLSConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMockativeImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.SubconversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.SubconversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.JoinExistingMLSConversationUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.JoinExistingMLSConversationUseCaseArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.util.ConversationPersistenceApi
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ConversationPersistenceApi::class)
class StaleEpochVerifierTest {

    @Test
    fun givenConversationIsNotMLS_whenHandlingStaleEpoch_thenShouldNotInsertWarning() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withFetchConversationResponse(Either.Right(PROTEUS_CONVERSATION_RESPONSE))
        }

        staleEpochHandler.verifyEpoch(arrangement.transactionContext, CONVERSATION_ID).shouldFail()

        coVerify {
            arrangement.systemMessageInserter.insertLostCommitSystemMessage(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMLSConversation_whenHandlingStaleEpoch_thenShouldFetchConversationAgain() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(false))
            withFetchConversationResponse(Either.Right(MLS_CONVERSATION_RESPONSE))
        }

        staleEpochHandler.verifyEpoch(arrangement.transactionContext, CONVERSATION_ID).shouldSucceed()

        coVerify {
            arrangement.fetchConversationUseCase.invoke(
                eq(arrangement.transactionContext),
                eq(CONVERSATION_ID),
                eq(ConversationSyncReason.Other)
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenEpochIsLatest_whenHandlingStaleEpoch_thenShouldNotRejoinTheConversation() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(false))
            withFetchConversationResponse(Either.Right(MLS_CONVERSATION_RESPONSE))
        }

        staleEpochHandler.verifyEpoch(arrangement.transactionContext, CONVERSATION_ID).shouldSucceed()

        coVerify {
            arrangement.joinExistingMLSConversationUseCase.invoke(
                arrangement.transactionContext,
                CONVERSATION_ID,
                null,
                true
            )
        }.wasNotInvoked()
    }

    @Test
    fun givenStaleEpoch_whenHandlingStaleEpoch_thenShouldRejoinTheConversation() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(true))
            withFetchConversationResponse(Either.Right(MLS_CONVERSATION_RESPONSE))
            withJoinExistingMLSConversationUseCaseReturning(Either.Right(Unit))
            withInsertLostCommitSystemMessage(Either.Right(Unit))
        }

        staleEpochHandler.verifyEpoch(arrangement.transactionContext, CONVERSATION_ID).shouldSucceed()

        coVerify {
            arrangement.joinExistingMLSConversationUseCase.invoke(
                any(),
                eq(CONVERSATION_ID),
                any(),
                any()
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenRejoiningFails_whenHandlingStaleEpoch_thenShouldNotInsertLostCommitSystemMessage() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(true))
            withFetchConversationResponse(Either.Right(MLS_CONVERSATION_RESPONSE))
            withJoinExistingMLSConversationUseCaseReturning(Either.Left(NetworkFailure.NoNetworkConnection(null)))
        }

        staleEpochHandler.verifyEpoch(arrangement.transactionContext, CONVERSATION_ID).shouldFail()

        coVerify {
            arrangement.systemMessageInserter.insertLostCommitSystemMessage(eq(CONVERSATION_ID), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenConversationIsRejoined_whenHandlingStaleEpoch_thenShouldInsertLostCommitSystemMessage() = runTest {
        val (arrangement, staleEpochHandler) = arrange {
            withIsGroupOutOfSync(Either.Right(true))
            withFetchConversationResponse(Either.Right(MLS_CONVERSATION_RESPONSE))
            withJoinExistingMLSConversationUseCaseReturning(Either.Right(Unit))
            withInsertLostCommitSystemMessage(Either.Right(Unit))
        }

        staleEpochHandler.verifyEpoch(arrangement.transactionContext, CONVERSATION_ID).shouldSucceed()

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

        val result = staleEpochHandler.verifyEpoch(arrangement.transactionContext, CONVERSATION_ID, subConversationId, null)

        result.shouldSucceed()

        coVerify {
            arrangement.subconversationRepository.fetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId)
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsConversationRepository.isLocalGroupEpochStale(
                any(),
                eq(TestSubConversationDetails.groupId),
                eq(TestSubConversationDetails.epoch)
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

        val result = staleEpochHandler.verifyEpoch(arrangement.transactionContext, CONVERSATION_ID, subConversationId, null)

        result.shouldSucceed()

        coVerify {
            arrangement.subconversationRepository.fetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId)
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(
                any(),
                eq(TestSubConversationDetails.groupId),
                eq(TestGroupInfo)
            )
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

        val result = staleEpochHandler.verifyEpoch(arrangement.transactionContext, CONVERSATION_ID, subConversationId, null)

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

        val result = staleEpochHandler.verifyEpoch(arrangement.transactionContext, CONVERSATION_ID, subConversationId, null)

        result.shouldFail()

        coVerify {
            arrangement.subconversationRepository.fetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId)
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsConversationRepository.joinGroupByExternalCommit(
                any(), eq(TestSubConversationDetails.groupId), eq(TestGroupInfo)
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenSubconversationId_WhenVerified_ThenShouldNotCallFetchConversation() = runTest {
        val subConversationId = SubconversationId("subconversation-id")
        val (arrangement, staleEpochHandler) = arrange {
            withFetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId, Either.Right(TestSubConversationDetails))
            withIsGroupOutOfSync(Either.Right(false))
        }

        val result = staleEpochHandler.verifyEpoch(arrangement.transactionContext, CONVERSATION_ID, subConversationId, null)

        result.shouldSucceed()

        coVerify {
            arrangement.subconversationRepository.fetchRemoteSubConversationDetails(CONVERSATION_ID, subConversationId)
        }.wasInvoked(once)

        coVerify {
            arrangement.fetchConversationUseCase.invoke(any(), any(), any())
        }.wasNotInvoked()
    }


    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        SystemMessageInserterArrangement by SystemMessageInserterArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl(),
        SubconversationRepositoryArrangement by SubconversationRepositoryArrangementImpl(),
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMockativeImpl(),
        JoinExistingMLSConversationUseCaseArrangement by JoinExistingMLSConversationUseCaseArrangementImpl() {
        val fetchConversationUseCase = mock(FetchConversationUseCase::class)

        suspend fun withFetchConversationResponse(result: Either<CoreFailure, ConversationResponse>) {
            coEvery {
                fetchConversationUseCase.invoke(any(), any(), any())
            }.returns(
                when (result) {
                    is Either.Left -> Either.Left(result.value)
                    is Either.Right -> Either.Right(Unit)
                }
            )

            val localProtocolInfo: Either<StorageFailure, Conversation.ProtocolInfo> = when (result) {
                is Either.Left -> Either.Left(StorageFailure.Generic(IllegalStateException(result.value.toString())))
                is Either.Right -> when (result.value.protocol) {
                    ConvProtocol.MLS -> Either.Right(TestConversation.MLS_PROTOCOL_INFO)
                    else -> Either.Right(Conversation.ProtocolInfo.Proteus)
                }
            }

            coEvery {
                conversationRepository.getConversationProtocolInfo(any())
            }.returns(localProtocolInfo)
        }

        suspend fun arrange() = run {
            block()
            this@Arrangement to StaleEpochVerifierImpl(
                systemMessageInserter = systemMessageInserter,
                fetchConversationUseCase = fetchConversationUseCase,
                conversationRepository = conversationRepository,
                subconversationRepository = subconversationRepository,
                mlsConversationRepository = mlsConversationRepository,
                joinExistingMLSConversation = joinExistingMLSConversationUseCase
            )
        }
    }

    private companion object {
        suspend fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        val CONVERSATION_ID = ConversationId("conversation-value", "conversation-domain")

        val MLS_CONVERSATION_RESPONSE = ConversationResponse(
            creator = TestUser.USER_ID.value,
            members = ConversationMembersResponse(
                self = ConversationMemberDTO.Self(TestUser.SELF.id.toApi(), "wire_admin"),
                otherMembers = listOf(ConversationMemberDTO.Other(TestUser.OTHER.id.toApi(), "wire_member"))
            ),
            name = "mls-conversation",
            id = CONVERSATION_ID.toApi(),
            groupId = TestConversation.MLS_PROTOCOL_INFO.groupId.value,
            epoch = TestConversation.MLS_PROTOCOL_INFO.epoch,
            type = ConversationResponse.Type.GROUP,
            messageTimer = 0,
            teamId = null,
            protocol = ConvProtocol.MLS,
            lastEventTime = "2022-03-30T15:36:00.000Z",
            mlsCipherSuiteTag = null,
            access = setOf(ConversationAccessDTO.INVITE),
            accessRole = setOf(ConversationAccessRoleDTO.TEAM_MEMBER),
            receiptMode = ReceiptMode.DISABLED
        )

        val PROTEUS_CONVERSATION_RESPONSE = MLS_CONVERSATION_RESPONSE.copy(
            groupId = null,
            epoch = null,
            protocol = ConvProtocol.PROTEUS
        )

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
