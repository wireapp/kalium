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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.DateTimeUtil
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
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

        val actual = recoverMLSConversationsUseCase()

        coVerify {
            arrangement.mlsConversationRepository.isGroupOutOfSync(any(), any())
        }.wasInvoked(conversations.size)

        coVerify {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any())
        }.wasInvoked(conversations.size)

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

        val actual = recoverMLSConversationsUseCase()

        coVerify {
            arrangement.mlsConversationRepository.isGroupOutOfSync(any(), any())
        }.wasInvoked(conversations.size)

        coVerify {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any())
        }.wasInvoked(conversations.size)

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

        val actual = recoverMLSConversationsUseCase()

        coVerify {
            arrangement.mlsConversationRepository.isGroupOutOfSync(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any())
        }.wasNotInvoked()

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

        val actual = recoverMLSConversationsUseCase()

        coVerify {
            arrangement.mlsConversationRepository.isGroupOutOfSync(any(), any())
        }.wasInvoked(twice)

        coVerify {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any())
        }.wasInvoked(once)

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

        val actual = recoverMLSConversationsUseCase()

        coVerify {
            arrangement.mlsConversationRepository.isGroupOutOfSync(any(), any())
        }.wasNotInvoked()

        assertIs<RecoverMLSConversationsResult.Failure>(actual)
    }

    private class Arrangement {
                val mlsConversationRepository = mock(MLSConversationRepository::class)
        val featureSupport = mock(FeatureSupport::class)
        val joinExistingMLSConversationUseCase = mock(JoinExistingMLSConversationUseCase::class)
        val clientRepository = mock(ClientRepository::class)
        val conversationRepository = mock(ConversationRepository::class)

        private var recoverMLSConversationsUseCase = RecoverMLSConversationsUseCaseImpl(
            featureSupport,
            clientRepository,
            conversationRepository,
            mlsConversationRepository,
            joinExistingMLSConversationUseCase
        )

        fun withIsMLSSupported(supported: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            }.returns(supported)
        }

        suspend fun withHasRegisteredMLSClient(result: Boolean) = apply {
            coEvery {
                clientRepository.hasRegisteredMLSClient()
            }.returns(Either.Right(result))
        }

        suspend fun withConversationsByGroupStateReturns(either: Either<StorageFailure, List<Conversation>>) = apply {
            coEvery {
                conversationRepository.getConversationsByGroupState(any())
            }.returns(either)
        }

        suspend fun withJoinExistingMLSConversationUseCaseSuccessful() = apply {
            coEvery {
                joinExistingMLSConversationUseCase.invoke(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withConversationIsOutOfSyncReturnsTrueFor(groupIds: List<GroupID>) = apply {
            coEvery {
                mlsConversationRepository.isGroupOutOfSync(matches { it in groupIds }, any())
            }.returns(Either.Right(true))
        }

        suspend fun withConversationIsOutOfSyncReturnsFalseFor(groupID: GroupID) = apply {
            coEvery {
                mlsConversationRepository.isGroupOutOfSync(eq(groupID), any())
            }.returns(Either.Right(false))
            coEvery {
                mlsConversationRepository.isGroupOutOfSync(matches { it != groupID }, any())
            }.returns(Either.Right(true))
        }

        suspend fun withJoinExistingMLSConversationUseCaseFailsFor(failedGroupId: ConversationId) = apply {
            coEvery {
                joinExistingMLSConversationUseCase.invoke(failedGroupId, null)
            }.returns(Either.Left(StorageFailure.DataNotFound))
            coEvery {
                joinExistingMLSConversationUseCase.invoke(matches { it != failedGroupId }, any())
            }.returns(Either.Right(Unit))
        }

        fun arrange() = this to recoverMLSConversationsUseCase

        companion object {
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
