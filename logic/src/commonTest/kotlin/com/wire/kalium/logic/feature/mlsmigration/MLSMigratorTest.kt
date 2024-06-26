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
package com.wire.kalium.logic.feature.mlsmigration

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.mlsmigration.MLSMigratorTest.Arrangement.Companion.CIPHER_SUITE
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.logic.util.arrangement.CallRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class MLSMigratorTest {

    @Test
    fun givenTeamConversation_whenMigrating_thenProtocolIsUpdatedToMixedAndGroupIsEstablished() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.GROUP,
            teamId = TestTeam.TEAM_ID
        )

        val (arrangement, migrator) = Arrangement()
            .withGetProteusTeamConversationsReturning(listOf(conversation.id))
            .withUpdateProtocolReturns()
            .withFetchConversationSucceeding()
            .withGetConversationProtocolInfoReturning(Arrangement.MIXED_PROTOCOL_INFO)
            .withEstablishGroupSucceeds(Arrangement.SUCCESSFUL_ADDITION_RESULT)
            .withGetConversationMembersReturning(Arrangement.MEMBERS.right())
            .withAddMembersSucceeds()
            .withoutAnyEstablishedCall()
            .arrange()

        migrator.migrateProteusConversations()

        coVerify {
            arrangement.conversationRepository.updateProtocolRemotely(eq(conversation.id), eq(Conversation.Protocol.MIXED))
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsConversationRepository.establishMLSGroup(eq(Arrangement.MIXED_PROTOCOL_INFO.groupId), eq(emptyList()), any())
        }

        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                eq(Arrangement.MIXED_PROTOCOL_INFO.groupId),
                eq(Arrangement.MEMBERS),
                eq(CIPHER_SUITE)
            )
        }
    }

    @Test
    fun givenAnOngoingCall_whenMigrating_thenInsertSystemMessages() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.GROUP,
            teamId = TestTeam.TEAM_ID
        )

        val (arrangement, migrator) = Arrangement()
            .withGetProteusTeamConversationsReturning(listOf(conversation.id))
            .withUpdateProtocolReturns()
            .withFetchConversationSucceeding()
            .withGetConversationProtocolInfoReturning(Arrangement.MIXED_PROTOCOL_INFO)
            .withEstablishGroupSucceeds(Arrangement.SUCCESSFUL_ADDITION_RESULT)
            .withGetConversationMembersReturning(Arrangement.MEMBERS.right())
            .withAddMembersSucceeds()
            .withEstablishedCall()
            .arrange()

        migrator.migrateProteusConversations()

        coVerify {
            arrangement.conversationRepository.updateProtocolRemotely(eq(conversation.id), eq(Conversation.Protocol.MIXED))
        }.wasInvoked(once)

        coVerify {
            arrangement.mlsConversationRepository.establishMLSGroup(eq(Arrangement.MIXED_PROTOCOL_INFO.groupId), eq(emptyList()), any())
        }

        coVerify {
            arrangement.mlsConversationRepository.addMemberToMLSGroup(
                eq(Arrangement.MIXED_PROTOCOL_INFO.groupId),
                eq(Arrangement.MEMBERS),
                eq(CIPHER_SUITE)
            )
        }

        coVerify {
            arrangement.systemMessageInserter.insertProtocolChangedSystemMessage(any(), any(), eq(Conversation.Protocol.MIXED))
        }

        coVerify {
            arrangement.systemMessageInserter.insertProtocolChangedDuringACallSystemMessage(any(), any())
        }
    }

    @Test
    fun givenAnError_whenMigrating_thenStillConsiderItASuccess() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.GROUP,
            teamId = TestTeam.TEAM_ID
        )

        val (_, migrator) = Arrangement()
            .withGetProteusTeamConversationsReturning(listOf(conversation.id))
            .withUpdateProtocolReturns()
            .withFetchConversationSucceeding()
            .withGetConversationProtocolInfoReturning(Arrangement.MIXED_PROTOCOL_INFO)
            .withGetConversationMembersReturning(StorageFailure.DataNotFound.left())
            .withEstablishGroupFails()
            .withoutAnyEstablishedCall()
            .arrange()

        val result = migrator.migrateProteusConversations()

        result.shouldSucceed()
    }

    @Test
    fun givenTeamConversation_whenFinalising_thenKnownUsersAreFetchedAndProtocolIsUpdatedToMls() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.GROUP,
            teamId = TestTeam.TEAM_ID
        )

        val (arrangement, migrator) = Arrangement()
            .withFetchAllOtherUsersSucceeding()
            .withGetProteusTeamConversationsReadyForFinalisationReturning(listOf(conversation.id))
            .withUpdateProtocolReturns()
            .withFetchConversationSucceeding()
            .withGetConversationProtocolInfoReturning(Arrangement.MLS_PROTOCOL_INFO)
            .arrange()

        migrator.finaliseProteusConversations()

        coVerify {
            arrangement.userRepository.fetchAllOtherUsers()
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationRepository.updateProtocolRemotely(eq(conversation.id), eq(Conversation.Protocol.MLS))
        }.wasInvoked(once)
    }

    @Test
    fun givenAnError_whenFinalising_thenStillConsiderItASuccess() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.GROUP,
            teamId = TestTeam.TEAM_ID
        )

        val (_, migrator) = Arrangement()
            .withFetchAllOtherUsersSucceeding()
            .withGetProteusTeamConversationsReadyForFinalisationReturning(listOf(conversation.id))
            .withUpdateProtocolReturns(Either.Left(TestNetworkResponseError.noNetworkConnection()))
            .arrange()

        val result = migrator.finaliseProteusConversations()
        result.shouldSucceed()
    }

    private class Arrangement {

        @Mock
        val userRepository = mock(UserRepository::class)

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val mlsConversationRepository = mock(MLSConversationRepository::class)

        @Mock
        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)

        @Mock
        val systemMessageInserter = mock(SystemMessageInserter::class)

        @Mock
        val callRepository = mock(CallRepository::class)

        suspend fun withFetchAllOtherUsersSucceeding() = apply {
            coEvery {
                userRepository.fetchAllOtherUsers()
            }.returns(Either.Right(Unit))
        }

        suspend fun withGetProteusTeamConversationsReturning(conversationsIds: List<ConversationId>) = apply {
            coEvery {
                conversationRepository.getConversationIds(eq(Conversation.Type.GROUP), eq(Conversation.Protocol.PROTEUS), any())
            }.returns(Either.Right(conversationsIds))
        }

        suspend fun withGetProteusTeamConversationsReadyForFinalisationReturning(conversationsIds: List<ConversationId>) = apply {
            coEvery {
                conversationRepository.getTeamConversationIdsReadyToCompleteMigration(any())
            }.returns(Either.Right(conversationsIds))
        }

        suspend fun withGetConversationProtocolInfoReturning(protocolInfo: Conversation.ProtocolInfo) = apply {
            coEvery {
                conversationRepository.getConversationProtocolInfo(any())
            }.returns(Either.Right(protocolInfo))
        }

        suspend fun withGetConversationMembersReturning(result: Either<StorageFailure, List<UserId>>) = apply {
            coEvery {
                conversationRepository.getConversationMembers(any())
            }.returns(result)
        }

        suspend fun withFetchConversationSucceeding() = apply {
            coEvery {
                conversationRepository.fetchConversation(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withUpdateProtocolReturns(result: Either<CoreFailure, Boolean> = Either.Right(true)) = apply {
            coEvery {
                conversationRepository.updateProtocolRemotely(any(), any())
            }.returns(result)
        }

        suspend fun withEstablishGroupSucceeds(additionResult: MLSAdditionResult) = apply {
            coEvery {
                mlsConversationRepository.establishMLSGroup(any(), any(), any())
            }.returns(Either.Right(additionResult))
        }

        suspend fun withEstablishGroupFails() = apply {
            coEvery {
                mlsConversationRepository.establishMLSGroup(any(), any(), any())
            }.returns(Either.Left(NetworkFailure.ServerMiscommunication(MLS_STALE_MESSAGE_ERROR)))
        }

        suspend fun withAddMembersSucceeds() = apply {
            coEvery {
                mlsConversationRepository.addMemberToMLSGroup(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withEstablishedCall() = apply {
            coEvery {
                callRepository.establishedCallsFlow()
            }.returns(flowOf(listOf(CallRepositoryArrangementImpl.call)))
        }

        suspend fun withoutAnyEstablishedCall() = apply {
            coEvery {
                callRepository.establishedCallsFlow()
            }.returns(flowOf(listOf()))
        }

        suspend fun arrange() = this to MLSMigratorImpl(
            TestUser.SELF.id,
            selfTeamIdProvider,
            userRepository,
            conversationRepository,
            mlsConversationRepository,
            systemMessageInserter,
            callRepository
        ).also {
            coEvery {
                selfTeamIdProvider.invoke()
            }.returns(Either.Right(TestTeam.TEAM_ID))
        }

        companion object {
            val CIPHER_SUITE = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            val MLS_STALE_MESSAGE_ERROR = KaliumException.InvalidRequestError(
                ErrorResponse(409, "", "mls-stale-message")
            )
            val MEMBERS = listOf(TestUser.USER_ID)
            val SUCCESSFUL_ADDITION_RESULT = MLSAdditionResult(MEMBERS.toSet(), emptySet())
            val MIXED_PROTOCOL_INFO = Conversation.ProtocolInfo.Mixed(
                TestConversation.GROUP_ID,
                Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = CIPHER_SUITE
            )
            val MLS_PROTOCOL_INFO = Conversation.ProtocolInfo.MLS(
                TestConversation.GROUP_ID,
                Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = CIPHER_SUITE
            )
        }
    }
}
