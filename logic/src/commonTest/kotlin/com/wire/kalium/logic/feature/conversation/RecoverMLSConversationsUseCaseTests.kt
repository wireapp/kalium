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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable.GroupState
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.util.DateTimeUtil
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class RecoverMLSConversationsUseCaseTests {
    @Test
    fun givenOutdatedListGroups_ThenRequestToJoinThemPerformed() = runTest {
        val conversations = listOf(Arrangement.MLS_CONVERSATION1, Arrangement.MLS_CONVERSATION2)
        val (arrangement, recoverMLSConversationsUseCase) = Arrangement()
            .withConversationsByGroupStateReturns(Either.Right(conversations))
            .withJoinExistingMLSConversationUseCaseSuccessful()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withConversationIsOutOfSyncReturnsTrueFor(listOf(Arrangement.GROUP_ID1, Arrangement.GROUP_ID2))
            .arrange()

        val actual = recoverMLSConversationsUseCase(arrangement.transactionContext)

        verifySuspend(VerifyMode.exactly(conversations.size)) {
            arrangement.mlsConversationRepository.isLocalGroupEpochStale(any(), any(), any())
        }

        verifySuspend(VerifyMode.exactly(conversations.size)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }

        assertIs<RecoverMLSConversationsResult.Success>(actual)
    }

    @Test
    fun givenOutdatedListGroups_ThenRequestToUpdateSucceededPartially_ThenReturnFailed() = runTest {
        val conversations = listOf(Arrangement.MLS_CONVERSATION1, Arrangement.MLS_CONVERSATION2)
        val (arrangement, recoverMLSConversationsUseCase) = Arrangement()
            .withConversationsByGroupStateReturns(Either.Right(conversations))
            .withJoinExistingMLSConversationUseCaseFailsFor(Arrangement.MLS_CONVERSATION1.id)
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withConversationIsOutOfSyncReturnsTrueFor(listOf(Arrangement.GROUP_ID1, Arrangement.GROUP_ID2))
            .arrange()

        val actual = recoverMLSConversationsUseCase(arrangement.transactionContext)

        verifySuspend(VerifyMode.exactly(conversations.size)) {
            arrangement.mlsConversationRepository.isLocalGroupEpochStale(any(), any(), any())
        }

        verifySuspend(VerifyMode.exactly(conversations.size)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }

        assertIs<RecoverMLSConversationsResult.Failure>(actual)
    }

    @Test
    fun givenEmptyListOfOutdatedGroups_ThenUpdateShouldNotCalled() = runTest {
        val conversations = emptyList<Conversation>()
        val (arrangement, recoverMLSConversationsUseCase) = Arrangement()
            .withConversationsByGroupStateReturns(Either.Right(conversations))
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withJoinExistingMLSConversationUseCaseSuccessful()
            .withConversationIsOutOfSyncReturnsFalseFor(Arrangement.GROUP_ID1)
            .arrange()

        val actual = recoverMLSConversationsUseCase(arrangement.transactionContext)

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.isLocalGroupEpochStale(any(), any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }

        assertIs<RecoverMLSConversationsResult.Success>(actual)
    }

    @Test
    fun givenPartiallyOutdatedConversationsList_ThenJoinShouldBeInvokedCorrectly() = runTest {
        val conversations = listOf(Arrangement.MLS_CONVERSATION1, Arrangement.MLS_CONVERSATION2)
        val (arrangement, recoverMLSConversationsUseCase) = Arrangement()
            .withConversationsByGroupStateReturns(Either.Right(conversations))
            .withJoinExistingMLSConversationUseCaseSuccessful()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withConversationIsOutOfSyncReturnsFalseFor(Arrangement.GROUP_ID2)
            .arrange()

        val actual = recoverMLSConversationsUseCase(arrangement.transactionContext)

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.mlsConversationRepository.isLocalGroupEpochStale(any(), any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }

        assertIs<RecoverMLSConversationsResult.Success>(actual)
    }

    @Test
    fun givenEpochCheckFailsWithConversationNotFound_ThenGroupIsMarkedPendingAndOtherGroupsProcessed() = runTest {
        val conversations = listOf(Arrangement.MLS_CONVERSATION1, Arrangement.MLS_CONVERSATION2)
        val (arrangement, recoverMLSConversationsUseCase) = Arrangement()
            .withConversationsByGroupStateReturns(Either.Right(conversations))
            .withJoinExistingMLSConversationUseCaseSuccessful()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withUpdateConversationGroupStateSuccessful()
            .withEpochCheckFailsWithConversationNotFoundFor(Arrangement.GROUP_ID1)
            .withConversationIsOutOfSyncReturnsTrueFor(listOf(Arrangement.GROUP_ID2))
            .arrange()

        val actual = recoverMLSConversationsUseCase(arrangement.transactionContext)

        verifySuspend(VerifyMode.exactly(conversations.size)) {
            arrangement.mlsConversationRepository.isLocalGroupEpochStale(any(), any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateConversationGroupState(
                eq(Arrangement.GROUP_ID1), eq(GroupState.PENDING_AFTER_RESET)
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), eq(Arrangement.MLS_CONVERSATION2.id), any(), any())
        }

        assertIs<RecoverMLSConversationsResult.Success>(actual)
    }

    @Test
    fun givenAllGroupsFailWithConversationNotFound_ThenAllMarkedPendingAndResultIsSuccess() = runTest {
        val conversations = listOf(Arrangement.MLS_CONVERSATION1, Arrangement.MLS_CONVERSATION2)
        val (arrangement, recoverMLSConversationsUseCase) = Arrangement()
            .withConversationsByGroupStateReturns(Either.Right(conversations))
            .withJoinExistingMLSConversationUseCaseSuccessful()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withUpdateConversationGroupStateSuccessful()
            .withEpochCheckFailsWithConversationNotFoundFor(Arrangement.GROUP_ID1)
            .withEpochCheckFailsWithConversationNotFoundFor(Arrangement.GROUP_ID2)
            .arrange()

        val actual = recoverMLSConversationsUseCase(arrangement.transactionContext)

        verifySuspend(VerifyMode.exactly(conversations.size)) {
            arrangement.conversationRepository.updateConversationGroupState(
                any(), eq(GroupState.PENDING_AFTER_RESET)
            )
        }

        verifySuspend(VerifyMode.not) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }

        assertIs<RecoverMLSConversationsResult.Success>(actual)
    }

    @Test
    fun givenSelfIsNotConversationMember_whenRecovering_thenConversationIsSkipped() = runTest {
        val (arrangement, recoverMLSConversationsUseCase) = Arrangement()
            .withConversationsByGroupStateReturns(Either.Right(listOf(Arrangement.MLS_CONVERSATION1)))
            .withConversationMembers(Arrangement.MLS_CONVERSATION1.id, emptyList())
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .arrange()

        val actual = recoverMLSConversationsUseCase(arrangement.transactionContext)

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.isLocalGroupEpochStale(any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.conversationRepository.updateConversationGroupState(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
        }
        assertIs<RecoverMLSConversationsResult.Success>(actual)
    }

    @Test
    fun whenFetchingListOfConversationsFails_ThenShouldReturnFailure() = runTest {
        val (arrangement, recoverMLSConversationsUseCase) = Arrangement()
            .withConversationsByGroupStateReturns(Either.Left(StorageFailure.DataNotFound))
            .withJoinExistingMLSConversationUseCaseSuccessful()
            .withIsMLSSupported(true)
            .withHasRegisteredMLSClient(true)
            .withConversationIsOutOfSyncReturnsTrueFor(listOf())
            .arrange()

        val actual = recoverMLSConversationsUseCase(arrangement.transactionContext)

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.isLocalGroupEpochStale(any(), any(), any())
        }

        assertIs<RecoverMLSConversationsResult.Failure>(actual)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        val featureSupport = mock<FeatureSupport>(mode = MockMode.autoUnit)
        val joinExistingMLSConversationUseCase = mock<JoinExistingMLSConversationUseCase>(mode = MockMode.autoUnit)
        val clientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)

        private var recoverMLSConversationsUseCase = RecoverMLSConversationsUseCaseImpl(
            featureSupport,
            clientRepository,
            conversationRepository,
            mlsConversationRepository,
            joinExistingMLSConversationUseCase,
            SELF_USER_ID,
        )

        fun withIsMLSSupported(supported: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            } returns supported
        }

        suspend fun withHasRegisteredMLSClient(result: Boolean) = apply {
            everySuspend {
                clientRepository.hasRegisteredMLSClient()
            } returns Either.Right(result)
        }

        suspend fun withConversationsByGroupStateReturns(either: Either<StorageFailure, List<Conversation>>) = apply {
            everySuspend {
                conversationRepository.getConversationsByGroupState(any())
            } returns either
            everySuspend {
                conversationRepository.getConversationMembers(any())
            } returns Either.Right(listOf(SELF_USER_ID))
        }

        suspend fun withConversationMembers(conversationId: ConversationId, members: List<UserId>) = apply {
            everySuspend {
                conversationRepository.getConversationMembers(eq(conversationId))
            } returns Either.Right(members)
        }

        suspend fun withJoinExistingMLSConversationUseCaseSuccessful() = apply {
            everySuspend {
                joinExistingMLSConversationUseCase.invoke(any(), any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withConversationIsOutOfSyncReturnsTrueFor(groupIds: List<GroupID>) = apply {
            everySuspend {
                mlsConversationRepository.isLocalGroupEpochStale(any(), matching { it in groupIds }, any())
            } returns Either.Right(true)
        }

        suspend fun withConversationIsOutOfSyncReturnsFalseFor(groupID: GroupID) = apply {
            everySuspend {
                mlsConversationRepository.isLocalGroupEpochStale(any(), eq(groupID), any())
            } returns Either.Right(false)
            everySuspend {
                mlsConversationRepository.isLocalGroupEpochStale(any(), matching { it != groupID }, any())
            } returns Either.Right(true)
        }

        suspend fun withUpdateConversationGroupStateSuccessful() = apply {
            everySuspend {
                conversationRepository.updateConversationGroupState(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withEpochCheckFailsWithConversationNotFoundFor(groupID: GroupID) = apply {
            everySuspend {
                mlsConversationRepository.isLocalGroupEpochStale(any(), eq(groupID), any())
            } returns Either.Left(MLSFailure.ConversationNotFound)
        }

        suspend fun withJoinExistingMLSConversationUseCaseFailsFor(failedGroupId: ConversationId) = apply {
            everySuspend {
                joinExistingMLSConversationUseCase.invoke(any(), eq(failedGroupId), any(), any())
            } returns Either.Left(StorageFailure.DataNotFound)
            everySuspend {
                joinExistingMLSConversationUseCase.invoke(any(), matching { it != failedGroupId }, any(), any())
            } returns Either.Right(Unit)
        }

        fun arrange() = this to recoverMLSConversationsUseCase

        companion object {
            val SELF_USER_ID = UserId("self", "domain")
            val GROUP_ID1 = GroupID("group1")
            val GROUP_ID2 = GroupID("group2")
            val MLS_CONVERSATION1 = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID1,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 1UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id1", "domain"))

            val MLS_CONVERSATION2 = TestConversation.GROUP(
                Conversation.ProtocolInfo.MLS(
                    GROUP_ID2,
                    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN,
                    epoch = 1UL,
                    keyingMaterialLastUpdate = DateTimeUtil.currentInstant(),
                    cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
                )
            ).copy(id = ConversationId("id2", "domain"))
        }
    }
}
