/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.feature.message.MessageSendFailureHandlerImpl
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.persistence.dao.message.MessageEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MessageSendFailureHandlerTest {

    @Test
    fun givenMissingClients_whenHandlingClientsHaveChangedFailure_thenUsersThatControlTheseClientsShouldBeFetched() = runTest {
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .withFetchUsersByIdSuccess()
            .withStoreUserClientIdListSuccess()
            .arrange()
        val failureData = ProteusSendMessageFailure(mapOf(arrangement.userOne, arrangement.userTwo), mapOf(), mapOf(), null)

        messageSendFailureHandler.handleClientsHaveChangedFailure(failureData)

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchUsersByIds)
            .with(eq(failureData.missingClientsOfUsers.keys))
            .wasInvoked(once)
    }

    @Test
    fun givenMissingContactsAndClients_whenHandlingClientsHaveChangedFailureThenClientsShouldBeAddedToContacts() = runTest {
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .withFetchUsersByIdSuccess()
            .withStoreUserClientIdListSuccess()
            .arrange()
        val failureData = ProteusSendMessageFailure(mapOf(arrangement.userOne, arrangement.userTwo), mapOf(), mapOf(), null)

        messageSendFailureHandler.handleClientsHaveChangedFailure(failureData)

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::storeUserClientIdList)
            .with(eq(arrangement.userOne.first), eq(arrangement.userOne.second))
            .wasInvoked(once)

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::storeUserClientIdList)
            .with(eq(arrangement.userTwo.first), eq(arrangement.userTwo.second))
            .wasInvoked(once)
    }

    @Test
    fun givenRepositoryFailsToFetchContacts_whenHandlingClientsHaveChangedFailure_thenFailureShouldBePropagated() = runTest {
        val failure = NETWORK_ERROR
        val (_, messageSendFailureHandler) = Arrangement()
            .withFetchUsersByIdFailure(failure)
            .arrange()
        val failureData = ProteusSendMessageFailure(mapOf(), mapOf(), mapOf(), null)

        val result = messageSendFailureHandler.handleClientsHaveChangedFailure(failureData)
        result.shouldFail()
        assertEquals(Either.Left(failure), result)
    }

    @Test
    fun givenRepositoryFailsToAddClientsToContacts_whenHandlingClientsHaveChangedFailure_thenFailureShouldBePropagated() = runTest {
        val failure = StorageFailure.Generic(IOException())
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .withFetchUsersByIdSuccess()
            .withStoreUserClientIdListFailure(failure)
            .arrange()
        val failureData = ProteusSendMessageFailure(mapOf(arrangement.userOne), mapOf(), mapOf(), null)

        val result = messageSendFailureHandler.handleClientsHaveChangedFailure(failureData)
        result.shouldFail()
        assertEquals(Either.Left(failure), result)
    }

    @Test
    fun givenFailedDueToFederationContextAvailability_whenHandlingMessageSendFailure_thenUpdateMessageStatusToFailedRemotely() = runTest {
        val failure = NetworkFailure.FederatedBackendFailure("error")
        val (arrangement, messageSendFailureHandler) = Arrangement()
            .withUpdateMessageStatusSuccess()
            .arrange()
        messageSendFailureHandler.handleFailureAndUpdateMessageStatus(failure, arrangement.conversationId, arrangement.messageId, "text")
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateMessageStatus)
            .with(eq(MessageEntity.Status.FAILED_REMOTELY), any(), any())
            .wasInvoked(once)
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
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateMessageStatus)
            .with(eq(MessageEntity.Status.FAILED), any(), any())
            .wasInvoked(once)
        verify(arrangement.messageSendingScheduler)
            .function(arrangement.messageSendingScheduler::scheduleSendingOfPendingMessages)
            .wasNotInvoked()
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
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateMessageStatus)
            .with(any(), any(), any())
            .wasNotInvoked()
        verify(arrangement.messageSendingScheduler)
            .function(arrangement.messageSendingScheduler::scheduleSendingOfPendingMessages)
            .wasInvoked(once)
    }

    class Arrangement {

        @Mock
        internal val clientRepository = mock(classOf<ClientRepository>())
        @Mock
        internal val userRepository = mock(classOf<UserRepository>())
        @Mock
        internal val messageRepository = mock(classOf<MessageRepository>())
        @Mock
        val messageSendingScheduler = configure(mock(MessageSendingScheduler::class)) { stubsUnitByDefault = true }
        private val messageSendFailureHandler: MessageSendFailureHandler =
            MessageSendFailureHandlerImpl(userRepository, clientRepository, messageRepository, messageSendingScheduler)
        val userOne: Pair<UserId, List<ClientId>> =
            UserId("userId1", "anta.wire") to listOf(ClientId("clientId"), ClientId("secondClientId"))
        val userTwo: Pair<UserId, List<ClientId>> =
            UserId("userId2", "bella.wire") to listOf(ClientId("clientId2"), ClientId("secondClientId2"))
        val messageId = TestMessage.TEST_MESSAGE_ID
        val conversationId = TestConversation.ID

        fun arrange() = this to messageSendFailureHandler

        fun withFetchUsersByIdSuccess() = apply {
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersByIds)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }
        fun withFetchUsersByIdFailure(failure: CoreFailure) = apply {
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersByIds)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(failure))
        }
        fun withStoreUserClientIdListSuccess() = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::storeUserClientIdList)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }
        fun withStoreUserClientIdListFailure(failure: StorageFailure) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::storeUserClientIdList)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(failure))
        }
        fun withUpdateMessageStatusSuccess() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Either.Right(Unit))
        }
    }

    private companion object {
        val NETWORK_ERROR = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
    }
}
