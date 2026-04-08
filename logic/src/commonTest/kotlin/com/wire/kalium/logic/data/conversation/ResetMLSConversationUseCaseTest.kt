/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.feature.backup.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.util.thenReturnSequentially
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.util.ConversationPersistenceApi
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ResetMLSConversationUseCaseTest {

    private val TEST_CONVERSATION_ID = UserId("testConversation", "domain")

    @Test
    fun givenCompileTimeFlagDisabled_whenUseCaseCalled_thenResetConversationNotStarted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withCompileTimeFlagDisabled()
            .withRuntimeFlagEnabled()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenCompileTimeFlagEnabledAndRuntimeFlagDisabled_whenUseCaseCalled_thenResetConversationNotStarted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withCompileTimeFlagEnabled()
            .withRuntimeFlagDisabled()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenBothFlagsEnabled_whenUseCaseCalled_thenResetConversationStarted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withCompileTimeFlagEnabled()
            .withRuntimeFlagEnabled()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasInvoked()
    }

    @Test
    fun givenFeatureDisabled_whenUseCaseCalled_thenResetConversationNotStarted() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureDisabled()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenRepositorySuccess_whenUseCaseCalled_thenResetMLSConversationCalled() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        useCase(TEST_CONVERSATION_ID)

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasInvoked()
    }

    @OptIn(ConversationPersistenceApi::class)
    @Test
    fun givenResetReturnsMlsStaleMessage_whenUseCaseCalled_thenConversationIsRefetchedAndResetRetriedWithRemoteEpoch() = runTest {
        val remoteEpoch = 42UL

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .withResetMlsConversationResponses(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.InvalidRequestError(
                        ErrorResponse(409, "epoch is too old", "mls-stale-message")
                    )
                ).left(),
                Unit.right()
            )
            .withRemoteConversationResponse(
                TestConversation.CONVERSATION_RESPONSE.copy(
                    protocol = ConvProtocol.MLS,
                    groupId = TestConversation.GROUP_ID.value,
                    epoch = remoteEpoch
                )
            )
            .arrange()

        useCase(TEST_CONVERSATION_ID)

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(eq(TestConversation.GROUP_ID), eq(15UL))
        }.wasInvoked(exactly = 1)

        coVerify {
            arrangement.conversationRepository.fetchConversation(eq(TEST_CONVERSATION_ID))
        }.wasInvoked(exactly = 1)

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(eq(TestConversation.GROUP_ID), eq(remoteEpoch))
        }.wasInvoked(exactly = 1)
    }

    @OptIn(ConversationPersistenceApi::class)
    @Test
    fun givenResetKeepsReturningMlsStaleMessageAndRemoteGroupIdChanges_whenUseCaseCalled_thenRetryIsCancelledAndConversationIsSynced() = runTest {
        val refreshedEpoch = 42UL
        val updatedGroupId = "remote-group-id"

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .withResetMlsConversationResponses(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.InvalidRequestError(
                        ErrorResponse(409, "epoch is too old", "mls-stale-message")
                    )
                ).left(),
                NetworkFailure.ServerMiscommunication(
                    KaliumException.InvalidRequestError(
                        ErrorResponse(409, "epoch is too old", "mls-stale-message")
                    )
                ).left()
            )
            .withRemoteConversationResponses(
                TestConversation.CONVERSATION_RESPONSE.copy(
                    protocol = ConvProtocol.MLS,
                    groupId = TestConversation.GROUP_ID.value,
                    epoch = refreshedEpoch
                ),
                TestConversation.CONVERSATION_RESPONSE.copy(
                    protocol = ConvProtocol.MLS,
                    groupId = updatedGroupId,
                    epoch = refreshedEpoch
                )
            )
            .withConversations(
                TestConversation.MLS_CONVERSATION,
                TestConversation.MLS_CONVERSATION.copy(
                    protocol = Conversation.ProtocolInfo.MLS(
                        groupId = GroupID(updatedGroupId),
                        groupState = TestConversation.MLS_PROTOCOL_INFO.groupState,
                        epoch = refreshedEpoch,
                        keyingMaterialLastUpdate = TestConversation.MLS_PROTOCOL_INFO.keyingMaterialLastUpdate,
                        cipherSuite = TestConversation.MLS_PROTOCOL_INFO.cipherSuite
                    )
                )
            )
            .arrange()

        useCase(TEST_CONVERSATION_ID)

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(eq(TestConversation.GROUP_ID), eq(15UL))
        }.wasInvoked(exactly = 1)

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(eq(TestConversation.GROUP_ID), eq(refreshedEpoch))
        }.wasInvoked(exactly = 1)

        coVerify {
            arrangement.conversationRepository.fetchConversation(eq(TEST_CONVERSATION_ID))
        }.wasInvoked(exactly = 2)

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(eq(GroupID(updatedGroupId)), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.fetchConversationUseCase(
                transactionContext = any(),
                conversationId = eq(TEST_CONVERSATION_ID),
                reason = eq(ConversationSyncReason.ConversationReset)
            )
        }.wasInvoked(exactly = 1)

        coVerify {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), eq(GroupID(updatedGroupId)), any(), any(), any())
        }.wasInvoked(exactly = 1)
    }

    @Test
    fun whenUseCaseSuccess_thenLeaveConversationCalled() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        useCase(TEST_CONVERSATION_ID)

        coVerify {
            arrangement.mlsConversationRepository.leaveGroup(any(), any())
        }.wasInvoked()
    }

    @Test
    fun whenUseCaseInvoked_thenConversationFetchedAfterReset() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        useCase(TEST_CONVERSATION_ID)

        coVerify {
            arrangement.fetchConversationUseCase(
                conversationId = any(),
                transactionContext = any(),
                reason = eq(ConversationSyncReason.ConversationReset)
            )
        }.wasInvoked(exactly = 1)
    }

    @Test
    fun whenUseCaseInvoked_thenConversationEstablishedAfterReset() = runTest {

        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        useCase(TEST_CONVERSATION_ID)

        coVerify {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }.wasInvoked()
    }

    @Test
    fun givenMLSProtocol_whenUseCaseCalled_thenSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .withConversation(TestConversation.MLS_CONVERSATION)
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasInvoked()
    }

    @Test
    fun givenMixedProtocol_whenUseCaseCalled_thenSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .withConversation(TestConversation.MIXED_CONVERSATION)
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasInvoked()
    }

    @Test
    fun givenLeaveGroupFails_whenUseCaseCalled_thenStillSucceeds() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .withLeaveGroupFailing()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        coVerify {
            arrangement.mlsConversationRepository.leaveGroup(any(), any())
        }.wasInvoked()

        coVerify {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }.wasInvoked()
    }

    @Test
    fun givenLeaveGroupSucceeds_whenUseCaseCalled_thenSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withFeatureEnabled()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID).toEither()

        assertTrue(result.isRight())

        coVerify {
            arrangement.mlsConversationRepository.leaveGroup(any(), any())
        }.wasInvoked()

        coVerify {
            arrangement.mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
        }.wasInvoked()
    }

    @Test
    fun givenFederatedConversation_whenUseCaseCalled_thenResetConversationNotStarted() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withCompileTimeFlagEnabled()
            .withRuntimeFlagDisabled()
            .arrange()

        val result = useCase(TEST_CONVERSATION_ID.copy(domain = "domainFederated")).toEither()

        assertTrue(result.isRight())

        coVerify {
            arrangement.conversationRepository.resetMlsConversation(any(), any())
        }.wasNotInvoked()
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        private val TEST_USER_ID = UserId("testUser", "domain")

        val userConfig = mock(UserConfigRepository::class)
        val conversationRepository = mock(ConversationRepository::class)
        val mlsConversationRepository = mock(MLSConversationRepository::class)
        val fetchConversationUseCase = mock(FetchConversationUseCase::class)
        var kaliumConfigs = KaliumConfigs(isMlsResetEnabled = true)
        private var remoteConversationResponses: List<ConversationResponse> = listOf(TestConversation.CONVERSATION_RESPONSE.copy(
            protocol = ConvProtocol.MLS,
            groupId = TestConversation.GROUP_ID.value,
            epoch = 21UL
        ))
        private var resetConversationResults: List<Either<NetworkFailure, Unit>> = listOf(Unit.right())
        private var conversations: List<Conversation> = listOf(TestConversation.MLS_CONVERSATION)

        fun withCompileTimeFlagDisabled() = apply {
            kaliumConfigs = kaliumConfigs.copy(isMlsResetEnabled = false)
        }

        fun withCompileTimeFlagEnabled() = apply {
            kaliumConfigs = kaliumConfigs.copy(isMlsResetEnabled = true)
        }

        suspend fun withRuntimeFlagDisabled() = apply {
            coEvery { userConfig.isMlsConversationsResetEnabled() } returns false
        }

        suspend fun withRuntimeFlagEnabled() = apply {
            coEvery { userConfig.isMlsConversationsResetEnabled() } returns true
        }

        suspend fun withFeatureDisabled() = apply {
            withCompileTimeFlagEnabled()
            withRuntimeFlagDisabled()
        }

        suspend fun withFeatureEnabled() = apply {
            withCompileTimeFlagEnabled()
            withRuntimeFlagEnabled()
        }

        suspend fun withConversation(conversation: Conversation) = apply {
            this.conversations = listOf(conversation)
        }

        suspend fun withConversations(vararg conversations: Conversation) = apply {
            this.conversations = conversations.toList()
        }

        fun withRemoteConversationResponse(conversationResponse: ConversationResponse) = apply {
            remoteConversationResponses = listOf(conversationResponse)
        }

        fun withRemoteConversationResponses(vararg conversationResponses: ConversationResponse) = apply {
            remoteConversationResponses = conversationResponses.toList()
        }

        fun withResetMlsConversationResponses(vararg results: Either<NetworkFailure, Unit>) = apply {
            resetConversationResults = results.toList()
        }

        suspend fun withLeaveGroupFailing() = apply {
            coEvery {
                mlsConversationRepository.leaveGroup(any(), any())
            } returns CoreFailure.Unknown(RuntimeException("Leave group failed")).left()
        }

        @OptIn(ConversationPersistenceApi::class)
        suspend fun arrange(): Pair<Arrangement, ResetMLSConversationUseCaseImpl> {

            withMLSTransactionReturning(Either.Right(Unit))
            withTransactionReturning(Either.Right(Unit))


            coEvery {
                mlsContext.conversationEpoch(any())
            } returns 15UL

            coEvery {
                conversationRepository.getConversationById(any())
            }.also {
                if (conversations.size == 1) {
                    it returns conversations.single().right()
                } else {
                    it.thenReturnSequentially(*conversations.map { conversation -> conversation.right() }.toTypedArray())
                }
            }

            coEvery {
                conversationRepository.resetMlsConversation(any(), any())
            }.thenReturnSequentially(*resetConversationResults.toTypedArray())

            coEvery {
                conversationRepository.fetchConversation(any())
            }.also {
                if (remoteConversationResponses.size == 1) {
                    it returns remoteConversationResponses.single().right()
                } else {
                    it.thenReturnSequentially(*remoteConversationResponses.map { response -> response.right() }.toTypedArray())
                }
            }

            coEvery {
                mlsConversationRepository.leaveGroup(any(), any())
            } returns Unit.right()

            coEvery {
                mlsConversationRepository.establishMLSGroup(any(), any(), any(), any(), any())
            } returns MLSAdditionResult(emptySet(), emptySet()).right()

            coEvery {
                fetchConversationUseCase(any(), any(), reason = eq(ConversationSyncReason.ConversationReset))
            } returns Unit.right()

            coEvery {
                conversationRepository.getConversationMembers(any())
            } returns listOf(UserId("test", "test@user")).right()

            return this to ResetMLSConversationUseCaseImpl(
                selfUserId = TEST_USER_ID,
                userConfig = userConfig,
                transactionProvider = cryptoTransactionProvider,
                conversationRepository = conversationRepository,
                mlsConversationRepository = mlsConversationRepository,
                fetchConversationUseCase = fetchConversationUseCase,
                kaliumConfigs = kaliumConfigs,
            )
        }
    }
}
