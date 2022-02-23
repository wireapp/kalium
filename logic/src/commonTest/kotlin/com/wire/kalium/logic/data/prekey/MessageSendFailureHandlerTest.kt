package com.wire.kalium.logic.data.prekey

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
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
        messageSendFailureHandler = MessageSendFailureHandler(userRepository, clientRepository)
        userOne = UserId("userId1", "anta.wire") to listOf(ClientId("clientId"), ClientId("secondClientId"))
        userTwo = UserId("userId2", "bella.wire") to listOf(ClientId("clientId2"), ClientId("secondClientId2"))
    }


    @Test
    fun given_missing_clients_when_handling_clientsHaveChanged_failure_then_users_that_control_these_clients_should_be_fetched() = runTest {
        val failureData = SendMessageFailure.ClientsHaveChanged(missingClientsOfUsers = mapOf(userOne, userTwo), mapOf(), mapOf())

        given(userRepository)
            .suspendFunction(userRepository::fetchUsersById)
            .whenInvokedWith(eq(failureData.missingClientsOfUsers.keys))
            .thenReturn(Either.Right(Unit))

        given(clientRepository)
            .suspendFunction(clientRepository::saveNewClients)
            .whenInvokedWith(any(), any())
            .thenReturn(Either.Right(Unit))

        messageSendFailureHandler.handleClientsHaveChangedFailure(failureData)

        verify(userRepository)
            .suspendFunction(userRepository::fetchUsersById)
            .with(eq(failureData.missingClientsOfUsers.keys))
            .wasInvoked(once)
    }


    @Test
    fun given_missing_contacts_and_clients_when_handling_clientsHaveChanged_failure_then_clients_should_be_added_to_contacts() = runTest {
        val failureData = SendMessageFailure.ClientsHaveChanged(missingClientsOfUsers = mapOf(userOne, userTwo), mapOf(), mapOf())

        given(userRepository)
            .suspendFunction(userRepository::fetchUsersById)
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
    fun given_repository_fails_to_fetch_contacts_when_handling_clientsHaveChanged_failure_then_failure_should_be_propagated() = runTest {
        val failure = CoreFailure.ServerMiscommunication
        given(userRepository)
            .suspendFunction(userRepository::fetchUsersById)
            .whenInvokedWith(any())
            .thenReturn(Either.Left(failure))
        val failureData = SendMessageFailure.ClientsHaveChanged(mapOf(), mapOf(), mapOf())

        val result = messageSendFailureHandler.handleClientsHaveChangedFailure(failureData)

        assertEquals(Either.Left(CoreFailure.ServerMiscommunication), result)
    }

    @Test
    fun given_repository_fails_to_add_clients_to_contacts_when_handling_clientsHaveChanged_failure_then_failure_should_be_propagated() =
        runTest {
            val failure = CoreFailure.ServerMiscommunication
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersById)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
            given(clientRepository)
                .suspendFunction(clientRepository::saveNewClients)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(failure))
            val failureData = SendMessageFailure.ClientsHaveChanged(mapOf(userOne), mapOf(), mapOf())

            val result = messageSendFailureHandler.handleClientsHaveChangedFailure(failureData)

            assertEquals(Either.Left(CoreFailure.ServerMiscommunication), result)
        }
}
