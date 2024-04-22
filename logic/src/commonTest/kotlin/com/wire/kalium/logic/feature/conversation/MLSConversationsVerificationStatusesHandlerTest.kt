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

import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.e2ei.MLSConversationsVerificationStatusesHandlerImpl
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.MLSConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.MLSConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.PersistMessageUseCaseArrangementImpl
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.EpochChangesDataEntity
import com.wire.kalium.persistence.dao.conversation.NameAndHandleEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MLSConversationsVerificationStatusesHandlerTest {

    @Test
    fun givenNotVerifiedConversation_whenNotVerifiedStatusComes_thenNothingChanged() = runTest {
        val conversationDetails = TestConversationDetails.CONVERSATION_GROUP.copy(conversation = TestConversation.MLS_CONVERSATION)
        val (arrangement, handler) = arrange {
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withIsGroupVerified(E2EIConversationState.NOT_VERIFIED)
            withConversationDetailsByMLSGroupId(Either.Right(conversationDetails))
        }

        handler()
        advanceUntilIdle()

        coVerify {
            arrangement.conversationRepository.updateMlsVerificationStatus(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.persistMessageUseCase.invoke(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.conversationRepository.setDegradedConversationNotifiedFlag(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenVerifiedConversation_whenNotVerifiedStatusComes_thenStatusSetToDegradedAndSystemMessageAdded() = runTest {
        val conversationDetails = TestConversationDetails.CONVERSATION_GROUP.copy(
            conversation = TestConversation.MLS_CONVERSATION.copy(
                mlsVerificationStatus = Conversation.VerificationStatus.VERIFIED
            )
        )
        val (arrangement, handler) = arrange {
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withIsGroupVerified(E2EIConversationState.NOT_VERIFIED)
            withConversationDetailsByMLSGroupId(Either.Right(conversationDetails))
        }

        handler()
        advanceUntilIdle()

        coVerify {
            arrangement.conversationRepository.updateMlsVerificationStatus(
                verificationStatus = eq(Conversation.VerificationStatus.DEGRADED),
                conversationID = eq(conversationDetails.conversation.id)
            )
        }.wasInvoked(once)

        coVerify {
            arrangement.persistMessageUseCase.invoke(any<Message.System>())
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationRepository.setDegradedConversationNotifiedFlag(any(), eq(false))
        }.wasInvoked(once)
    }

    @Test
    fun givenDegradedConversation_whenNotVerifiedStatusComes_thenNothingChanged() = runTest {
        val conversationDetails = TestConversationDetails.CONVERSATION_GROUP.copy(
            conversation = TestConversation.MLS_CONVERSATION
                .copy(mlsVerificationStatus = Conversation.VerificationStatus.DEGRADED)
        )
        val (arrangement, handler) = arrange {
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withIsGroupVerified(E2EIConversationState.NOT_VERIFIED)
            withConversationDetailsByMLSGroupId(Either.Right(conversationDetails))
        }

        handler()
        advanceUntilIdle()

        coVerify {
            arrangement.conversationRepository.updateMlsVerificationStatus(any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.persistMessageUseCase.invoke(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.conversationRepository.setDegradedConversationNotifiedFlag(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenDegradedConversation_whenVerifiedStatusComes_thenStatusUpdated() = runTest {

        val user1 = UserIDEntity("user1", "domain1") to NameAndHandleEntity("name1", "device1")
        val user2 = UserIDEntity("user2", "domain2") to NameAndHandleEntity("name2", "device2")

        val epochChangedData = EpochChangesDataEntity(
            conversationId = TestConversation.CONVERSATION.id.toDao(),
            members = mapOf(user1, user2),
            mlsVerificationStatus = ConversationEntity.VerificationStatus.DEGRADED
        )

        val ccMembersIdentity: Map<UserId, List<WireIdentity>> = mapOf(
            user1.first.toModel() to listOf(
                WireIdentity(
                    CryptoQualifiedClientId(
                        value = "client_user_1",
                        userId = user1.first.toModel().toCrypto()
                    ),
                    handle = user1.second.handle!!,
                    displayName = user1.second.name!!,
                    certificate = "cert1",
                    domain = "domain1",
                    serialNumber = "serial1",
                    status = CryptoCertificateStatus.VALID,
                    thumbprint = "thumbprint1",
                    endTimestampSeconds = 1899105093
                )
            ),
            user2.first.toModel() to listOf(
                WireIdentity(
                    CryptoQualifiedClientId(
                        value = "client_user_2",
                        userId = user2.first.toModel().toCrypto()
                    ),
                    handle = user2.second.handle!!,
                    displayName = user2.second.name!!,
                    certificate = "cert2",
                    domain = "domain2",
                    serialNumber = "serial2",
                    status = CryptoCertificateStatus.VALID,
                    thumbprint = "thumbprint2",
                    endTimestampSeconds = 1899105093
                )
            )
        )
        val (arrangement, handler) = arrange {
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withIsGroupVerified(E2EIConversationState.VERIFIED)
            withSelectGroupStatusMembersNamesAndHandles(Either.Right(epochChangedData))
            withGetMembersIdentities(Either.Right(ccMembersIdentity))
        }

        handler()
        advanceUntilIdle()

        coVerify {
            arrangement.conversationRepository.updateMlsVerificationStatus(
                eq(Conversation.VerificationStatus.VERIFIED),
                eq(epochChangedData.conversationId.toModel())
            )
        }.wasInvoked(once)

        coVerify {
            arrangement.persistMessageUseCase.invoke(any<Message.System>())
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationRepository.setDegradedConversationNotifiedFlag(any(), eq(true))
        }.wasInvoked(once)
    }

    @Test
    fun givenVerifiedConversation_whenVerifiedStatusComesAndUserNamesDivergeFromCC_thenStatusUpdatedToDegraded() = runTest {

        val user1 = UserIDEntity("user1", "domain1") to NameAndHandleEntity("name1", "device1")
        val user2 = UserIDEntity("user2", "domain2") to NameAndHandleEntity("name2", "device2")

        val epochChangedData = EpochChangesDataEntity(
            conversationId = TestConversation.CONVERSATION.id.toDao(),
            members = mapOf(user1, user2),
            mlsVerificationStatus = ConversationEntity.VerificationStatus.VERIFIED
        )

        val ccMembersIdentity: Map<UserId, List<WireIdentity>> = mapOf(
            user1.first.toModel() to listOf(
                WireIdentity(
                    CryptoQualifiedClientId(
                        value = "client_user_1",
                        userId = user1.first.toModel().toCrypto()
                    ),
                    handle = user1.second.handle!! + "1", // this user name changed
                    displayName = user1.second.name!! + "1",
                    certificate = "cert1",
                    domain = "domain1",
                    serialNumber = "serial1",
                    status = CryptoCertificateStatus.VALID,
                    thumbprint = "thumbprint1",
                    endTimestampSeconds = 1899105093
                )
            ),
            user2.first.toModel() to listOf(
                WireIdentity(
                    CryptoQualifiedClientId(
                        value = "client_user_2",
                        userId = user2.first.toModel().toCrypto()
                    ),
                    handle = user2.second.handle!!,
                    displayName = user2.second.name!!,
                    certificate = "cert2",
                    domain = "domain2",
                    serialNumber = "serial2",
                    status = CryptoCertificateStatus.VALID,
                    thumbprint = "thumbprint2",
                    endTimestampSeconds = 1899105093
                )
            )
        )
        val (arrangement, handler) = arrange {
            withObserveEpochChanges(flowOf(TestConversation.GROUP_ID))
            withIsGroupVerified(E2EIConversationState.VERIFIED)
            withSelectGroupStatusMembersNamesAndHandles(Either.Right(epochChangedData))
            withGetMembersIdentities(Either.Right(ccMembersIdentity))
        }

        handler()
        advanceUntilIdle()

        coVerify {
            arrangement.conversationRepository.updateMlsVerificationStatus(
                eq(Conversation.VerificationStatus.DEGRADED),
                eq(epochChangedData.conversationId.toModel())
            )
        }.wasInvoked(once)

        coVerify {
            arrangement.persistMessageUseCase.invoke(any<Message.System>())
        }.wasInvoked(once)

        coVerify {
            arrangement.conversationRepository.setDegradedConversationNotifiedFlag(any(), eq(false))
        }.wasInvoked(once)
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        PersistMessageUseCaseArrangement by PersistMessageUseCaseArrangementImpl(),
        MLSConversationRepositoryArrangement by MLSConversationRepositoryArrangementImpl(),
        UserRepositoryArrangement by UserRepositoryArrangementImpl() {

        @Mock
        val mlsClientProvider = mock(MLSClientProvider::class)

        @Mock
        val mlsClient = mock(MLSClient::class)

        @Mock
        val mlsConversationRepository = mock(MLSConversationRepository::class)

        suspend fun withIsGroupVerified(result: E2EIConversationState) {
            coEvery {
                mlsClient.isGroupVerified(any())
            }.returns(result)
        }

        suspend fun withGetMembersIdentities(result: Either<CoreFailure, Map<UserId, List<WireIdentity>>>) {
            coEvery {
                mlsConversationRepository.getMembersIdentities(any(), any())
            }.returns(result)
        }

        suspend inline fun arrange() = run {
            withUpdateVerificationStatus(Either.Right(Unit))
            withPersistingMessage(Either.Right(Unit))
            withSetDegradedConversationNotifiedFlag(Either.Right(Unit))
            coEvery {
                mlsClientProvider.getMLSClient(any())
            }.returns(Either.Right(mlsClient))
            block()
            this to MLSConversationsVerificationStatusesHandlerImpl(
                conversationRepository = conversationRepository,
                persistMessage = persistMessageUseCase,
                epochChangesObserver = epochChangesObserver,
                selfUserId = TestUser.USER_ID,
                kaliumLogger = kaliumLogger,
                userRepository = userRepository,
                mlsClientProvider = mlsClientProvider,
                mlsConversationRepository = mlsConversationRepository
            )
        }
    }
}
