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
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.logic.util.arrangement.CallRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
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
            .withGetConversationMembersReturning(Arrangement.MEMBERS)
            .withAddMembersSucceeds()
            .withoutAnyEstablishedCall()
            .arrange()

        migrator.migrateProteusConversations()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateProtocolRemotely)
            .with(eq(conversation.id), eq(Conversation.Protocol.MIXED))
            .wasInvoked(once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::establishMLSGroup)
            .with(eq(Arrangement.MIXED_PROTOCOL_INFO.groupId), eq(emptyList()))

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(eq(Arrangement.MIXED_PROTOCOL_INFO.groupId), eq(Arrangement.MEMBERS))
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
            .withGetConversationMembersReturning(Arrangement.MEMBERS)
            .withAddMembersSucceeds()
            .withEstablishedCall()
            .arrange()

        migrator.migrateProteusConversations()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateProtocolRemotely)
            .with(eq(conversation.id), eq(Conversation.Protocol.MIXED))
            .wasInvoked(once)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::establishMLSGroup)
            .with(eq(Arrangement.MIXED_PROTOCOL_INFO.groupId), eq(emptyList()))

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::addMemberToMLSGroup)
            .with(eq(Arrangement.MIXED_PROTOCOL_INFO.groupId), eq(Arrangement.MEMBERS))

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertProtocolChangedSystemMessage)
            .with(any(), any(), eq(Conversation.Protocol.MIXED))

        verify(arrangement.systemMessageInserter)
            .suspendFunction(arrangement.systemMessageInserter::insertProtocolChangedDuringACallSystemMessage)
            .with(any(), any())
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

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchAllOtherUsers)
            .wasInvoked(once)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateProtocolRemotely)
            .with(eq(conversation.id), eq(Conversation.Protocol.MLS))
            .wasInvoked(once)
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
        val userRepository = mock(classOf<UserRepository>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val selfTeamIdProvider = mock(classOf<SelfTeamIdProvider>())

        @Mock
        val systemMessageInserter = mock(classOf<SystemMessageInserter>())

        @Mock
        val callRepository = mock(classOf<CallRepository>())

        fun withFetchAllOtherUsersSucceeding() = apply {
            given(userRepository)
                .suspendFunction(userRepository::fetchAllOtherUsers)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))
        }

        fun withGetProteusTeamConversationsReturning(conversationsIds: List<ConversationId>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationIds)
                .whenInvokedWith(eq(Conversation.Type.GROUP), eq(Conversation.Protocol.PROTEUS), anything())
                .thenReturn(Either.Right(conversationsIds))
        }

        fun withGetProteusTeamConversationsReadyForFinalisationReturning(conversationsIds: List<ConversationId>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getTeamConversationIdsReadyToCompleteMigration)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(conversationsIds))
        }

        fun withGetConversationProtocolInfoReturning(protocolInfo: Conversation.ProtocolInfo) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationProtocolInfo)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(protocolInfo))
        }

        fun withGetConversationMembersReturning(members: List<UserId>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationMembers)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(members))
        }

        fun withFetchConversationSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversation)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }
        fun withUpdateProtocolReturns(result: Either<CoreFailure, Boolean> = Either.Right(true)) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateProtocolRemotely)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withEstablishGroupSucceeds(additionResult: MLSAdditionResult) = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::establishMLSGroup)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(additionResult))
        }

        fun withEstablishGroupFails() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::establishMLSGroup)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(MLS_STALE_MESSAGE_ERROR)))
        }

        fun withAddMembersSucceeds() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::addMemberToMLSGroup)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withEstablishedCall() = apply {
            given(callRepository)
                .suspendFunction(callRepository::establishedCallsFlow)
                .whenInvoked()
                .thenReturn(flowOf(listOf(CallRepositoryArrangementImpl.call)))
        }

        fun withoutAnyEstablishedCall() = apply {
            given(callRepository)
                .suspendFunction(callRepository::establishedCallsFlow)
                .whenInvoked()
                .thenReturn(flowOf(listOf()))
        }

        fun arrange() = this to MLSMigratorImpl(
            TestUser.SELF.id,
            selfTeamIdProvider,
            userRepository,
            conversationRepository,
            mlsConversationRepository,
            systemMessageInserter,
            callRepository
        )

        init {
            given(selfTeamIdProvider)
                .suspendFunction(selfTeamIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(TestTeam.TEAM_ID))
        }

        companion object {
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
                cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )
            val MLS_PROTOCOL_INFO = Conversation.ProtocolInfo.MLS(
                TestConversation.GROUP_ID,
                Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                0UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )
        }
    }
}
