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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.message.SystemMessageInserter
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.mlsmigration.MLSMigratorTest.Arrangement.Companion.CIPHER_SUITE
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.UpdateConversationProtocolUseCase
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.CallRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class MLSMigratorTest {

    @Test
    fun givenTeamConversation_whenMigrating_thenProtocolIsUpdatedToMixedAndGroupIsEstablished() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.Group.Regular,
            teamId = TestTeam.TEAM_ID
        )

        val (arrangement, migrator) = Arrangement()
            .withGetProteusTeamConversationsReturning(listOf(conversation.id))
            .withUpdateProtocolReturns()
            .withGetConversationProtocolInfoReturning(Arrangement.MIXED_PROTOCOL_INFO)
            .withEstablishGroupSucceeds(Arrangement.SUCCESSFUL_ADDITION_RESULT)
            .withGetConversationMembersReturning(Arrangement.MEMBERS.right())
            .withAddMembersSucceeds()
            .withoutAnyEstablishedCall()
            .arrange()

        migrator.migrateProteusConversations()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationProtocol(
                any(),
                eq(conversation.id),
                eq(Conversation.Protocol.MIXED),
                eq(false)
            )
        }

        verifySuspend(VerifyMode.atLeast(1)) {
            arrangement.mlsConversationRepository.establishMLSGroup(
                groupID = Arrangement.MIXED_PROTOCOL_INFO.groupId,
                members = Arrangement.MEMBERS,
                publicKeys = null,
                allowSkippingUsersWithoutKeyPackages = true,
                mlsContext = arrangement.mlsContext
            )
        }

    }

    @Test
    fun givenAnOngoingCall_whenMigrating_thenInsertSystemMessages() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.Group.Regular,
            teamId = TestTeam.TEAM_ID
        )

        val (arrangement, migrator) = Arrangement()
            .withGetProteusTeamConversationsReturning(listOf(conversation.id))
            .withUpdateProtocolReturns()
            .withGetConversationProtocolInfoReturning(Arrangement.MIXED_PROTOCOL_INFO)
            .withEstablishGroupSucceeds(Arrangement.SUCCESSFUL_ADDITION_RESULT)
            .withGetConversationMembersReturning(Arrangement.MEMBERS.right())
            .withAddMembersSucceeds()
            .withEstablishedCall()
            .arrange()

        migrator.migrateProteusConversations()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationProtocol(
                any(),
                eq(conversation.id),
                eq(Conversation.Protocol.MIXED),
                eq(false)
            )
        }

        verifySuspend(VerifyMode.atLeast(1)) {
            arrangement.mlsConversationRepository.establishMLSGroup(
                groupID = Arrangement.MIXED_PROTOCOL_INFO.groupId,
                members = Arrangement.MEMBERS,
                publicKeys = null,
                allowSkippingUsersWithoutKeyPackages = true,
                mlsContext = arrangement.mlsContext
            )
        }

        verifySuspend(VerifyMode.atLeast(1)) {
            arrangement.systemMessageInserter.insertProtocolChangedSystemMessage(any(), any(), eq(Conversation.Protocol.MIXED))
        }

        verifySuspend(VerifyMode.atLeast(1)) {
            arrangement.systemMessageInserter.insertProtocolChangedDuringACallSystemMessage(any(), any())
        }
    }

    @Test
    fun givenAnError_whenMigrating_thenStillConsiderItASuccess() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.Group.Regular,
            teamId = TestTeam.TEAM_ID
        )

        val (_, migrator) = Arrangement()
            .withGetProteusTeamConversationsReturning(listOf(conversation.id))
            .withUpdateProtocolReturns()
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
            type = Conversation.Type.Group.Regular,
            teamId = TestTeam.TEAM_ID
        )

        val (arrangement, migrator) = Arrangement()
            .withFetchAllOtherUsersSucceeding()
            .withGetProteusTeamConversationsReadyForFinalisationReturning(listOf(conversation.id))
            .withUpdateProtocolReturns()
            .withGetConversationProtocolInfoReturning(Arrangement.MLS_PROTOCOL_INFO)
            .arrange()

        migrator.finaliseProteusConversations()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchAllOtherUsers()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateConversationProtocol(any(), eq(conversation.id), eq(Conversation.Protocol.MLS), eq(false))
        }
    }

    @Test
    fun givenAnError_whenFinalising_thenStillConsiderItASuccess() = runTest {
        val conversation = TestConversation.CONVERSATION.copy(
            type = Conversation.Type.Group.Regular,
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

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        val selfTeamIdProvider = mock<SelfTeamIdProvider>(mode = MockMode.autoUnit)
        val systemMessageInserter = mock<SystemMessageInserter>(mode = MockMode.autoUnit)
        val callRepository = mock<CallRepository>(mode = MockMode.autoUnit)
        val updateConversationProtocol = mock<UpdateConversationProtocolUseCase>(mode = MockMode.autoUnit)

        suspend fun withFetchAllOtherUsersSucceeding() = apply {
            everySuspend {
                userRepository.fetchAllOtherUsers()
            } returns Either.Right(Unit)
        }

        suspend fun withGetProteusTeamConversationsReturning(conversationsIds: List<ConversationId>) = apply {
            everySuspend {
                conversationRepository.getConversationIds(
                    Conversation.Type.Group.Regular,
                    Conversation.Protocol.PROTEUS,
                    TeamId(value = "Some-team")
                )
            } returns Either.Right(conversationsIds)
        }

        suspend fun withGetProteusTeamConversationsReadyForFinalisationReturning(conversationsIds: List<ConversationId>) = apply {
            everySuspend {
                conversationRepository.getTeamConversationIdsReadyToCompleteMigration(any())
            } returns Either.Right(conversationsIds)
        }

        suspend fun withGetConversationProtocolInfoReturning(protocolInfo: Conversation.ProtocolInfo) = apply {
            everySuspend {
                conversationRepository.getConversationProtocolInfo(any())
            } returns Either.Right(protocolInfo)
        }

        suspend fun withGetConversationMembersReturning(result: Either<StorageFailure, List<UserId>>) = apply {
            everySuspend {
                conversationRepository.getConversationMembers(any())
            } returns result
        }

        suspend fun withUpdateProtocolReturns(result: Either<CoreFailure, Boolean> = Either.Right(true)) = apply {
            everySuspend {
                updateConversationProtocol(any(), any(), any(), any())
            } returns result
        }

        suspend fun withEstablishGroupSucceeds(additionResult: MLSAdditionResult) = apply {
            everySuspend {
                mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
            } returns Either.Right(additionResult)
        }

        suspend fun withEstablishGroupFails() = apply {
            everySuspend {
                mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
            } returns Either.Left(NetworkFailure.ServerMiscommunication(MLS_STALE_MESSAGE_ERROR))
        }

        suspend fun withAddMembersSucceeds() = apply {
            everySuspend {
                mlsConversationRepository.addMemberToMLSGroup(any(), any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withEstablishedCall() = apply {
            everySuspend {
                callRepository.establishedCallsFlow()
            } returns flowOf(listOf(CallRepositoryArrangementImpl.call))
        }

        suspend fun withoutAnyEstablishedCall() = apply {
            everySuspend {
                callRepository.establishedCallsFlow()
            } returns flowOf(listOf())
        }

        suspend fun arrange() = this to MLSMigratorImpl(
            TestUser.SELF.id,
            selfTeamIdProvider,
            userRepository,
            conversationRepository,
            mlsConversationRepository,
            systemMessageInserter,
            callRepository,
            updateConversationProtocol,
            cryptoTransactionProvider
        ).also {
            everySuspend {
                selfTeamIdProvider.invoke()
            } returns Either.Right(TestTeam.TEAM_ID)
            withTransactionReturning(Either.Right(Unit))
        }

        companion object {
            val CIPHER_SUITE = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            val MLS_STALE_MESSAGE_ERROR = KaliumException.InvalidRequestError(
                ErrorResponse(409, "", "mls-stale-message")
            )
            val MEMBERS = listOf(TestUser.USER_ID)
            val SUCCESSFUL_ADDITION_RESULT = MLSAdditionResult(MEMBERS.toSet(), emptySet(), emptySet())
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
