package com.wire.kalium.logic.data.prekey

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.feature.message.MessageSendFailureHandlerImpl
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldFail
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageSendFailureHandlerTest {

    @Mock
    val clientRepository = mock(classOf<ClientRepository>())

    @Mock
    val userRepository = mock(classOf<UserRepository>())

    private lateinit var messageSendFailureHandler: MessageSendFailureHandler

    private lateinit var userOne: Pair<UserId, List<ClientId>>
    private lateinit var userTwo: Pair<UserId, List<ClientId>>

    @BeforeTest
    fun setup() {
        messageSendFailureHandler = MessageSendFailureHandlerImpl(userRepository, clientRepository)
        userOne = UserId("userId1", "anta.wire") to listOf(ClientId("clientId"), ClientId("secondClientId"))
        userTwo = UserId("userId2", "bella.wire") to listOf(ClientId("clientId2"), ClientId("secondClientId2"))
    }


    @Test
    fun givenMissingClients_whenHandlingClientsHaveChangedFailure_thenUsersThatControlTheseClientsShouldBeFetched() = runTest {
        val failureData = ProteusSendMessageFailure(missingClientsOfUsers = mapOf(userOne, userTwo), mapOf(), mapOf())

        given(userRepository)
            .suspendFunction(userRepository::fetchUsersByIds)
            .whenInvokedWith(eq(failureData.missingClientsOfUsers.keys))
            .thenReturn(Either.Right(Unit))

        given(clientRepository)
            .suspendFunction(clientRepository::saveNewClients)
            .whenInvokedWith(any(), any())
            .thenReturn(Either.Right(Unit))

        messageSendFailureHandler.handleClientsHaveChangedFailure(failureData)

        verify(userRepository)
            .suspendFunction(userRepository::fetchUsersByIds)
            .with(eq(failureData.missingClientsOfUsers.keys))
            .wasInvoked(once)
    }


    @Test
    fun givenMissingContactsAndClients_whenHandlingClientsHaveChangedFailureThenClientsShouldBeAddedToContacts() = runTest {
        val failureData = ProteusSendMessageFailure(missingClientsOfUsers = mapOf(userOne, userTwo), mapOf(), mapOf())

        given(userRepository)
            .suspendFunction(userRepository::fetchUsersByIds)
            .whenInvokedWith(eq(failureData.missingClientsOfUsers.keys))
            .thenReturn(Either.Right(Unit))

        given(clientRepository)
            .suspendFunction(clientRepository::saveNewClients)
            .whenInvokedWith(any(), any())
            .thenReturn(Either.Right(Unit))

        messageSendFailureHandler.handleClientsHaveChangedFailure(failureData)

        verify(clientRepository)
            .suspendFunction(clientRepository::saveNewClients)
            .with(eq(userOne.first), eq(userOne.second))
            .wasInvoked(once)

        verify(clientRepository)
            .suspendFunction(clientRepository::saveNewClients)
            .with(eq(userTwo.first), eq(userTwo.second))
            .wasInvoked(once)
    }


    @Test
    fun givenRepositoryFailsToFetchContacts_whenHandlingClientsHaveChangedFailure_thenFailureShouldBePropagated() = runTest {
        val failure = NETWORK_ERROR
        given(userRepository)
            .suspendFunction(userRepository::fetchUsersByIds)
            .whenInvokedWith(any())
            .thenReturn(Either.Left(failure))
        val failureData = ProteusSendMessageFailure(mapOf(), mapOf(), mapOf())

        val result = messageSendFailureHandler.handleClientsHaveChangedFailure(failureData)
        result.shouldFail()
        assertEquals(Either.Left(failure), result)
    }

    @Test
    fun givenRepositoryFailsToAddClientsToContacts_whenHandlingClientsHaveChangedFailure_thenFailureShouldBePropagated() = runTest {
        val failure = NETWORK_ERROR
        given(userRepository)
            .suspendFunction(userRepository::fetchUsersByIds)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))
        given(clientRepository)
            .suspendFunction(clientRepository::saveNewClients)
            .whenInvokedWith(any(), any())
            .thenReturn(Either.Left(failure))
        val failureData = ProteusSendMessageFailure(mapOf(userOne), mapOf(), mapOf())

        val result = messageSendFailureHandler.handleClientsHaveChangedFailure(failureData)
        result.shouldFail()
        assertEquals(Either.Left(failure), result)
    }
    private companion object {
        val NETWORK_ERROR = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
    }
}
