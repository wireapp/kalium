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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.createSessions
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.feature.ProteusClientProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import io.mockative.Mock
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SessionEstablisherTest {

    @Mock
    private val proteusClient = configure(mock(ProteusClient::class)) { stubsUnitByDefault = true }

    @Mock
    private val proteusClientProvider = mock(ProteusClientProvider::class)

    @Mock
    private val preKeyRepository = configure(mock(PreKeyRepository::class)) { stubsUnitByDefault = true }

    @Mock
    private val clientDAO: ClientDAO = configure(mock(ClientDAO::class)) { stubsUnitByDefault = true }

    private lateinit var sessionEstablisher: SessionEstablisher

    @BeforeTest
    fun setup() {
        sessionEstablisher = SessionEstablisherImpl(proteusClientProvider, preKeyRepository, clientDAO)

        given(proteusClientProvider)
            .suspendFunction(proteusClientProvider::getOrError)
            .whenInvoked()
            .thenReturn(Either.Right(proteusClient))
    }

    @Test
    fun givenAllSessionsAreEstablishedAlready_whenPreparingSessions_thenItShouldSucceed() = runTest {
        given(proteusClient)
            .suspendFunction(proteusClient::doesSessionExist)
            .whenInvokedWith(anything())
            .then { true }

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))
            .shouldSucceed()
    }

    @Test
    fun givenProteusClientThrowsWhenCheckingSession_whenPreparingSessions_thenItShouldFail() = runTest {
        val exception = ProteusException("PANIC!!!11!eleven!", ProteusException.Code.PANIC)
        given(proteusClient)
            .suspendFunction(proteusClient::doesSessionExist)
            .whenInvokedWith(anything())
            .thenThrow(exception)

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))
            .shouldFail {
                assertIs<ProteusFailure>(it)
                assertEquals(exception, it.proteusException)
            }
    }

    @Test
    fun givenAllSessionsAreEstablishedAlready_whenPreparingSessions_thenPreKeyRepositoryShouldNotBeCalled() = runTest {
        given(proteusClient)
            .suspendFunction(proteusClient::doesSessionExist)
            .whenInvokedWith(anything())
            .then { true }

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))

        verify(preKeyRepository)
            .suspendFunction(preKeyRepository::preKeysOfClientsByQualifiedUsers)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenARecipient_whenPreparingSessions_thenProteusClientShouldCheckIfSessionExists() = runTest {
        given(proteusClient)
            .suspendFunction(proteusClient::doesSessionExist)
            .whenInvokedWith(anything())
            .then { true }

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))

        verify(proteusClient)
            .suspendFunction(proteusClient::doesSessionExist)
            .with(eq(CryptoSessionId(CryptoUserID(TEST_USER_ID_1.value, TEST_USER_ID_1.domain), CryptoClientId(TEST_CLIENT_ID_1.value))))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenASessionIsNotEstablished_whenPreparingSessions_thenPreKeysShouldBeFetched() = runTest {
        given(proteusClient)
            .suspendFunction(proteusClient::doesSessionExist)
            .whenInvokedWith(anything())
            .then { false }

        given(preKeyRepository)
            .suspendFunction(preKeyRepository::preKeysOfClientsByQualifiedUsers)
            .whenInvokedWith(anything())
            .then { Either.Right(mapOf()) }

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))

        verify(preKeyRepository)
            .suspendFunction(preKeyRepository::preKeysOfClientsByQualifiedUsers)
            .with(eq(mapOf(TEST_RECIPIENT_1.id to TEST_RECIPIENT_1.clients)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenFetchingPreKeysFails_whenPreparingSessions_thenFailureShouldBePropagated() = runTest {
        val failure = NETWORK_ERROR
        given(preKeyRepository)
            .suspendFunction(preKeyRepository::preKeysOfClientsByQualifiedUsers)
            .whenInvokedWith(anything())
            .then { Either.Left(failure) }

        given(proteusClient)
            .suspendFunction(proteusClient::doesSessionExist)
            .whenInvokedWith(anything())
            .then { false }

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))
            .shouldFail {
                assertEquals(failure, it)
            }
    }

    @Test
    fun givenFetchingPreKeysSucceeds_whenPreparingSessions_thenProteusClientShouldCreateSessions() = runTest {
        val preKey = PreKeyDTO(42, "encodedData")
        val prekeyCrypto = PreKeyCrypto(preKey.id, preKey.key)
        val userPreKeysResult = mapOf(TEST_USER_ID_1.domain to mapOf(TEST_USER_ID_1.value to mapOf(TEST_CLIENT_ID_1.value to preKey)))
        val expected: Map<String, Map<String, Map<String, PreKeyCrypto>>> =
            mapOf(TEST_USER_ID_1.domain to mapOf(TEST_USER_ID_1.value to mapOf(TEST_CLIENT_ID_1.value to prekeyCrypto)))
        given(preKeyRepository)
            .suspendFunction(preKeyRepository::preKeysOfClientsByQualifiedUsers)
            .whenInvokedWith(anything())
            .then { Either.Right(userPreKeysResult) }

        given(proteusClient)
            .suspendFunction(proteusClient::doesSessionExist)
            .whenInvokedWith(anything())
            .then { false }

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))

        verify(proteusClient)
            .coroutine { proteusClient.createSessions(expected) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenFetchingPreKeysWithNullClients_whenPreparingSessions_thenTryToInvalidateINvalidSessions() = runTest {
        val preKey = PreKeyDTO(42, "encodedData")
        val prekeyCrypto = PreKeyCrypto(preKey.id, preKey.key)
        val userPreKeysResult = mapOf(
            TEST_USER_ID_1.domain to mapOf(
                TEST_USER_ID_1.value to mapOf(
                    TEST_CLIENT_ID_1.value to preKey,
                    "invalidClient" to null,

                    )
            )
        )
        val expectedValid: Map<String, Map<String, Map<String, PreKeyCrypto>>> =
            mapOf(TEST_USER_ID_1.domain to mapOf(TEST_USER_ID_1.value to mapOf(TEST_CLIENT_ID_1.value to prekeyCrypto)))

        val expectedInvalid: List<Pair<UserIDEntity, List<String>>> =
            listOf(UserIDEntity(TEST_USER_ID_1.value, TEST_USER_ID_1.domain) to listOf("invalidClient"))
        given(preKeyRepository)
            .suspendFunction(preKeyRepository::preKeysOfClientsByQualifiedUsers)
            .whenInvokedWith(anything())
            .then { Either.Right(userPreKeysResult) }

        given(proteusClient)
            .suspendFunction(proteusClient::doesSessionExist)
            .whenInvokedWith(anything())
            .then { false }

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))

        verify(proteusClient)
            .coroutine { proteusClient.createSessions(expectedValid) }
            .wasInvoked(exactly = once)
        verify(clientDAO)
            .coroutine { clientDAO.tryMarkInvalid(expectedInvalid) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenCreatingSessionsSucceeds_whenPreparingSessions_thenItShouldSucceed() = runTest {
        val preKey = PreKeyDTO(42, "encodedData")
        val userPreKeysResult = mapOf(TEST_USER_ID_1.domain to mapOf(TEST_USER_ID_1.value to mapOf(TEST_CLIENT_ID_1.value to preKey)))

        given(preKeyRepository)
            .suspendFunction(preKeyRepository::preKeysOfClientsByQualifiedUsers)
            .whenInvokedWith(anything())
            .then { Either.Right(userPreKeysResult) }

        given(proteusClient)
            .suspendFunction(proteusClient::doesSessionExist)
            .whenInvokedWith(anything())
            .then { false }

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))
            .shouldSucceed()
    }

    @Test
    fun givenCreatingSessionsThrows_whenPreparingSessions_thenItShouldFail() = runTest {
        val exception = ProteusException("PANIC!!!11!eleven!", ProteusException.Code.PANIC)
        given(proteusClient)
            .suspendFunction(proteusClient::createSession)
            .whenInvokedWith(anything(), anything())
            .thenThrow(exception)

        val preKey = PreKeyDTO(42, "encodedData")
        val userPreKeysResult = mapOf(TEST_USER_ID_1.domain to mapOf(TEST_USER_ID_1.value to mapOf(TEST_CLIENT_ID_1.value to preKey)))

        given(preKeyRepository)
            .suspendFunction(preKeyRepository::preKeysOfClientsByQualifiedUsers)
            .whenInvokedWith(anything())
            .then { Either.Right(userPreKeysResult) }

        given(proteusClient)
            .suspendFunction(proteusClient::doesSessionExist)
            .whenInvokedWith(anything())
            .then { false }

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))
            .shouldFail {
                assertIs<ProteusFailure>(it)
                assertEquals(exception, it.proteusException)
            }
    }

    private companion object {
        val TEST_USER_ID_1 = TestUser.USER_ID
        val TEST_CLIENT_ID_1 = TestClient.CLIENT_ID
        val TEST_RECIPIENT_1 = Recipient(TestUser.USER_ID, listOf(TestClient.CLIENT_ID))
        val NETWORK_ERROR = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
    }
}
