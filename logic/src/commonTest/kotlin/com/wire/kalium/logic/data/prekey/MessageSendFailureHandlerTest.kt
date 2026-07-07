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

package com.wire.kalium.logic.data.prekey

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.client.ClientMapper
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationSyncReason
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.logic.feature.message.MessageSendFailureHandlerImpl
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.network.api.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.api.model.UserId as UserIdDTO
import com.wire.kalium.persistence.dao.message.MessageEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import okio.IOException

internal class MessageSendFailureHandlerTest {

    @Test
    fun givenMissingClients_whenHandlingClientsHaveChangedFailure_thenUsersThatControlTheseClientsShouldBeFetched() = runTest {
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .arrange {
                withFetchUsersByIdSuccess()
                withFetchOtherUserClients(Either.Right(emptyMap()))
                withStoreUserClientListAndRemoveRedundantClients(Either.Right(Unit))
                withTryMarkClientsAsValid(Either.Right(Unit))
            }
        val failureData =
            ProteusSendMessageFailure(mapOf(arrangement.userOne, arrangement.userTwo), mapOf(), mapOf(), null)

        messageSendFailureHandler.handleClientsHaveChangedFailure(arrangement.transactionContext, failureData, null)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersByIds(eq(failureData.missingClientsOfUsers.keys))
        }
    }

    @Test
    fun givenMissingClients_whenHandlingClientsHaveChangedFailure_thenSimpleClientsDataShouldBeFetchedAndAddedToContacts() = runTest {
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .arrange {
                withFetchUsersByIdSuccess()
                withFetchOtherUserClients(Either.Right(mapOf(userOneDTO, userTwoDTO)))
                withStoreUserClientListAndRemoveRedundantClients(Either.Right(Unit))
                withTryMarkClientsAsValid(Either.Right(Unit))
            }

        val failureData =
            ProteusSendMessageFailure(mapOf(arrangement.userOne, arrangement.userTwo), mapOf(), mapOf(), null)

        messageSendFailureHandler.handleClientsHaveChangedFailure(arrangement.transactionContext, failureData, null)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRemoteRepository.fetchOtherUserClients(eq(listOf(arrangement.userOne.first, arrangement.userTwo.first)))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.storeUserClientListAndRemoveRedundantClients(
                eq(arrangement.userOneInsertClientParams + arrangement.userTwoInsertClientParams)
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.tryMarkClientsAsValid(
                eq(mapOf(arrangement.userOne.first to arrangement.userOne.second, arrangement.userTwo.first to arrangement.userTwo.second))
            )
        }
    }

    @Test
    fun givenRepositoryFailsToFetchContacts_whenHandlingClientsHaveChangedFailure_thenFailureShouldBePropagated() = runTest {
        val failure = NETWORK_ERROR
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .withFetchUsersByIdFailure(failure)
            .arrange()
        val failureData = ProteusSendMessageFailure(mapOf(arrangement.userOne, arrangement.userTwo), mapOf(), mapOf(), null)

        val result = messageSendFailureHandler.handleClientsHaveChangedFailure(
            arrangement.transactionContext,
            failureData,
            null
        )
        result.shouldFail()
        assertEquals(Either.Left(failure), result)
    }

    @Test
    fun givenRepositoryFailsToFetchClients_whenHandlingClientsHaveChangedFailure_thenFailureShouldBePropagated() = runTest {
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .arrange {
                withFetchUsersByIdSuccess()
                withFetchOtherUserClients(Either.Left(failure))
            }
        val failureData = ProteusSendMessageFailure(mapOf(arrangement.userOne), mapOf(), mapOf(), null)

        val result = messageSendFailureHandler.handleClientsHaveChangedFailure(arrangement.transactionContext, failureData, null)
        result.shouldFail()
        assertEquals(Either.Left(failure), result)
    }

    @Test
    fun givenRepositoryFailsToAddClientsToContacts_whenHandlingClientsHaveChangedFailure_thenFailureShouldBePropagated() = runTest {
        val failure = StorageFailure.Generic(IOException())
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .arrange {
                withFetchUsersByIdSuccess()
                withFetchOtherUserClients(Either.Right(mapOf(userOneDTO)))
                withStoreUserClientListAndRemoveRedundantClients(Either.Left(failure))
            }
        val failureData = ProteusSendMessageFailure(mapOf(arrangement.userOne), mapOf(), mapOf(), null)

        val result = messageSendFailureHandler.handleClientsHaveChangedFailure(arrangement.transactionContext, failureData, null)
        result.shouldFail()
        assertEquals(Either.Left(failure), result)
    }

    @Test
    fun givenFailedDueToFederationContextAvailability_whenHandlingMessageSendFailure_thenUpdateMessageStatusToFailedRemotely() = runTest {
        val failure = NetworkFailure.FederatedBackendFailure.General("error")
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .withUpdateMessageStatusSuccess()
            .arrange()
        messageSendFailureHandler.handleFailureAndUpdateMessageStatus(failure, arrangement.conversationId, arrangement.messageId, "text")
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatus(eq(MessageEntity.Status.FAILED_REMOTELY), any(), any())
        }
    }

    @Test
    fun givenFailedDueToNoNetworkAndResendingSetToFalse_whenHandlingMessageSendFailure_thenUpdateMessageStatusToFailed() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .withUpdateMessageStatusSuccess()
            .arrange()
        messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
            failure = failure,
            conversationId = arrangement.conversationId,
            messageId = arrangement.messageId,
            messageType = "text",
            scheduleResendIfNoNetwork = false
        )
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatus(eq(MessageEntity.Status.FAILED), any(), any())
        }
        verify(VerifyMode.exactly(0)) {
            arrangement.messageSendingScheduler.scheduleSendingOfPendingMessages()
        }
    }

    @Test
    fun givenFailedDueToNoNetworkAndResendingSetToTrue_whenHandlingMessageSendFailure_thenScheduleResending() = runTest {
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .withUpdateMessageStatusSuccess()
            .arrange()
        messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
            failure = failure,
            conversationId = arrangement.conversationId,
            messageId = arrangement.messageId,
            messageType = "text",
            scheduleResendIfNoNetwork = true
        )
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.messageRepository.updateMessageStatus(any(), any(), any())
        }
        verify(VerifyMode.exactly(1)) {
            arrangement.messageSendingScheduler.scheduleSendingOfPendingMessages()
        }
    }

    @Test
    fun givenDeletedClientsError_whenAllUserClientsAreDeleted_thenClientsShouldBeRemovedAndUserIndoShouldBeFetched() = runTest {
        // 1. remove clients
        // 2. return the list of users that have no clients left
        // 3. fetch users by id

        val (arrangement, messageSendFailureHandler) = Arrangement()
            .arrange {
                withRemoveClientsAndReturnUsersWithNoClients(Either.Right(listOf(userOne.first)))
                withFetchUsersByIdSuccess()
                withFetchOtherUserClients(Either.Right(emptyMap()))
                withStoreUserClientListAndRemoveRedundantClients(Either.Right(Unit))
            }
        val failure = ProteusSendMessageFailure(
            missingClientsOfUsers = mapOf(),
            deletedClientsOfUsers = mapOf(arrangement.userOne.first to arrangement.userOne.second),
            redundantClientsOfUsers = mapOf(),
            failedClientsOfUsers = null
        )

        messageSendFailureHandler.handleClientsHaveChangedFailure(arrangement.transactionContext, failure, null)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.removeClientsAndReturnUsersWithNoClients(
                eq(mapOf(arrangement.userOne.first to arrangement.userOne.second))
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersByIds(eq(setOf(arrangement.userOne.first)))
        }
    }

    @Test
    fun givenDeletedClientsError_whenNotAllUserClientsAreDeleted_thenClientsShouldBeRemoved() = runTest {
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .arrange {
                withRemoveClientsAndReturnUsersWithNoClients(Either.Right(emptyList()))
                withFetchUsersByIdSuccess()
                withFetchOtherUserClients(Either.Right(emptyMap()))
                withStoreUserClientListAndRemoveRedundantClients(Either.Right(Unit))
            }

        val failure = ProteusSendMessageFailure(
            missingClientsOfUsers = mapOf(),
            deletedClientsOfUsers = mapOf(arrangement.userOne.first to arrangement.userOne.second),
            redundantClientsOfUsers = mapOf(),
            failedClientsOfUsers = null
        )

        messageSendFailureHandler.handleClientsHaveChangedFailure(arrangement.transactionContext, failure, null)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.removeClientsAndReturnUsersWithNoClients(
                eq(mapOf(arrangement.userOne.first to arrangement.userOne.second))
            )
        }

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.userRepository.fetchUsersByIds(eq(setOf(arrangement.userOne.first)))
        }
    }

    @Test
    fun givenDeletedAndMissingUsers_whenHandling_thenFetchUserInfoOneTimeForBoth() = runTest {
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .arrange {
                withRemoveClientsAndReturnUsersWithNoClients(Either.Right(listOf(userTwo.first)))
                withFetchUsersByIdSuccess()
                withFetchOtherUserClients(Either.Right(mapOf(userOneDTO)))
                withStoreUserClientListAndRemoveRedundantClients(Either.Right(Unit))
                withTryMarkClientsAsValid(Either.Right(Unit))
            }

        val failure = ProteusSendMessageFailure(
            missingClientsOfUsers = mapOf(arrangement.userOne.first to arrangement.userOne.second),
            deletedClientsOfUsers = mapOf(arrangement.userTwo.first to arrangement.userTwo.second),
            redundantClientsOfUsers = mapOf(),
            failedClientsOfUsers = null
        )

        messageSendFailureHandler.handleClientsHaveChangedFailure(arrangement.transactionContext, failure, null)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.removeClientsAndReturnUsersWithNoClients(
                eq(mapOf(arrangement.userTwo.first to arrangement.userTwo.second))
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRemoteRepository.fetchOtherUserClients(eq(listOf(arrangement.userOne.first)))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.storeUserClientListAndRemoveRedundantClients(eq(arrangement.userOneInsertClientParams))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersByIds(eq(setOf(arrangement.userOne.first, arrangement.userTwo.first)))
        }
    }

    @Test
    fun givenMissingClientsError_whenAConversationIdIsProvided_thenUpdateConversationInfo() = runTest {
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .arrange {
                withFetchOtherUserClients(Either.Right(emptyMap()))
                withStoreUserClientListAndRemoveRedundantClients(Either.Right(Unit))
                withTryMarkClientsAsValid(Either.Right(Unit))
                withFetchUsersByIdSuccess()
                withFetchConversationSucceeding()
            }
        val failureData = ProteusSendMessageFailure(mapOf(arrangement.userOne, arrangement.userTwo), mapOf(), mapOf(), null)

        messageSendFailureHandler.handleClientsHaveChangedFailure(arrangement.transactionContext, failureData, arrangement.conversationId)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersByIds(eq(failureData.missingClientsOfUsers.keys))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.fetchConversation(any(), any(), eq(ConversationSyncReason.Other))
        }
    }

    @Test
    fun givenMissingClientsError_whenNoConversationIdIsProvided_thenUpdateConversationInfo() = runTest {
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .arrange {
                withFetchOtherUserClients(Either.Right(emptyMap()))
                withStoreUserClientListAndRemoveRedundantClients(Either.Right(Unit))
                withTryMarkClientsAsValid(Either.Right(Unit))
                withFetchUsersByIdSuccess()
            }
        val failureData = ProteusSendMessageFailure(mapOf(arrangement.userOne, arrangement.userTwo), mapOf(), mapOf(), null)

        messageSendFailureHandler.handleClientsHaveChangedFailure(arrangement.transactionContext, failureData, null)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.fetchUsersByIds(eq(failureData.missingClientsOfUsers.keys))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.fetchConversation(any(), any(), any())
        }
    }

    @Test
    fun givenMissingClientsError_whenDeletedAndMissingAreEmpty_thenDoNotUpdateConversationInfo() = runTest {
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .arrange()

        val failureData = ProteusSendMessageFailure(
            missingClientsOfUsers = emptyMap(),
            deletedClientsOfUsers = mapOf(),
            redundantClientsOfUsers = mapOf(arrangement.userOne, arrangement.userTwo),
            failedClientsOfUsers = mapOf(arrangement.userOne, arrangement.userTwo)
        )

        messageSendFailureHandler.handleClientsHaveChangedFailure(arrangement.transactionContext, failureData, null)

        verifySuspend(VerifyMode.not) {
            arrangement.fetchConversation(any(), any(), any())
        }
    }

    class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val clientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)
        val fetchConversation = mock<FetchConversationUseCase>()
        internal val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        internal val messageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
        val messageSendingScheduler = mock<MessageSendingScheduler>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val clientRemoteRepository = mock<ClientRemoteRepository>(mode = MockMode.autoUnit)

        val clientMapper: ClientMapper = MapperProvider.clientMapper()

        val userOne: Pair<UserId, List<ClientId>> =
            UserId("userId1", "anta.wire") to listOf(ClientId("clientId"), ClientId("secondClientId"))
        val userTwo: Pair<UserId, List<ClientId>> =
            UserId("userId2", "bella.wire") to listOf(ClientId("clientId2"), ClientId("secondClientId2"))
        val userOneDTO: Pair<UserIdDTO, List<SimpleClientResponse>> =
            UserIdDTO("userId1", "anta.wire") to listOf(SimpleClientResponse("clientId"), SimpleClientResponse("secondClientId"))
        val userTwoDTO: Pair<UserIdDTO, List<SimpleClientResponse>> =
            UserIdDTO("userId2", "bella.wire") to listOf(SimpleClientResponse("clientId2"), SimpleClientResponse("secondClientId2"))
        val userOneInsertClientParams = clientMapper.toInsertClientParam(userOneDTO.second, userOneDTO.first)
        val userTwoInsertClientParams = clientMapper.toInsertClientParam(userTwoDTO.second, userTwoDTO.first)
        val messageId = TestMessage.TEST_MESSAGE_ID
        val conversationId = TestConversation.ID

        internal inline fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let {
            this to MessageSendFailureHandlerImpl(
                userRepository,
                clientRepository,
                clientRemoteRepository,
                messageRepository,
                messageSendingScheduler,
                fetchConversation,
                clientMapper,
            )
        }

        suspend fun withFetchUsersByIdSuccess() = apply {
            everySuspend {
                userRepository.fetchUsersByIds(any())
            }.returns(Either.Right(true))
        }

        suspend fun withFetchUsersByIdFailure(failure: CoreFailure) = apply {
            everySuspend {
                userRepository.fetchUsersByIds(any())
            }.returns(Either.Left(failure))
        }

        suspend fun withUpdateMessageStatusSuccess() = apply {
            everySuspend {
                messageRepository.updateMessageStatus(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withFetchOtherUserClients(result: Either<NetworkFailure, Map<UserIdDTO, List<SimpleClientResponse>>>) = apply {
            everySuspend {
                clientRemoteRepository.fetchOtherUserClients(any())
            }.returns(result)
        }

        suspend fun withStoreUserClientListAndRemoveRedundantClients(
            result: Either<StorageFailure, Unit>
        ) = apply {
            everySuspend {
                clientRepository.storeUserClientListAndRemoveRedundantClients(any())
            }.returns(result)
        }

        suspend fun withTryMarkClientsAsValid(
            result: Either<StorageFailure, Unit>
        ) = apply {
            everySuspend {
                clientRepository.tryMarkClientsAsValid(any())
            }.returns(result)
        }

        suspend fun withRemoveClientsAndReturnUsersWithNoClients(
            result: Either<StorageFailure, List<UserId>>
        ) = apply {
            everySuspend {
                clientRepository.removeClientsAndReturnUsersWithNoClients(any())
            }.returns(result)
        }

        suspend fun withFetchConversationSucceeding() = apply {
            everySuspend {
                fetchConversation(any(), any(), any())
            }.returns(Either.Right(Unit))
        }
    }

    private companion object {
        val NETWORK_ERROR = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
    }
}
