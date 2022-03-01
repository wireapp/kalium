package com.wire.kalium.logic.feature.message

import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.UserId
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.prekey.ClientPreKeyInfo
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.QualifiedUserPreKeyInfo
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkExiption
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionEstablisherTest {

    @Mock
    private val proteusClient = configure(mock(ProteusClient::class)) { stubsUnitByDefault = true }

    @Mock
    private val preKeyRepository = configure(mock(PreKeyRepository::class)) { stubsUnitByDefault = true }

    private lateinit var sessionEstablisher: SessionEstablisher

    @BeforeTest
    fun setup() {
        sessionEstablisher = SessionEstablisherImpl(proteusClient, preKeyRepository)
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
                assertEquals(CoreFailure.Unknown(exception), it)
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
            .with(eq(CryptoSessionId(UserId(TEST_USER_ID_1.value), CryptoClientId(TEST_CLIENT_ID_1.value))))
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
            .then { Either.Right(listOf()) }

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))

        verify(preKeyRepository)
            .suspendFunction(preKeyRepository::preKeysOfClientsByQualifiedUsers)
            .with(eq(mapOf(TEST_RECIPIENT_1.member.id to TEST_RECIPIENT_1.clients)))
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
        val preKey = PreKeyCrypto(42, "encodedData")
        val clientPreKeyInfo = ClientPreKeyInfo(TEST_CLIENT_ID_1.value, preKey)
        val userPreKeysResult = listOf(QualifiedUserPreKeyInfo(TEST_USER_ID_1, listOf(clientPreKeyInfo)))

        given(preKeyRepository)
            .suspendFunction(preKeyRepository::preKeysOfClientsByQualifiedUsers)
            .whenInvokedWith(anything())
            .then { Either.Right(userPreKeysResult) }

        given(proteusClient)
            .suspendFunction(proteusClient::doesSessionExist)
            .whenInvokedWith(anything())
            .then { false }

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))

        val cryptoSessionId = CryptoSessionId(UserId(TEST_RECIPIENT_1.member.id.value), CryptoClientId(clientPreKeyInfo.clientId))
        verify(proteusClient)
            .suspendFunction(proteusClient::createSession)
            .with(eq(preKey), eq(cryptoSessionId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenCreatingSessionsSucceeds_whenPreparingSessions_thenItShouldSucceed() = runTest {
        val preKey = PreKeyCrypto(42, "encodedData")
        val clientPreKeyInfo = ClientPreKeyInfo(TEST_CLIENT_ID_1.value, preKey)
        val userPreKeysResult = listOf(QualifiedUserPreKeyInfo(TEST_USER_ID_1, listOf(clientPreKeyInfo)))

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

        val preKey = PreKeyCrypto(42, "encodedData")
        val clientPreKeyInfo = ClientPreKeyInfo(TEST_CLIENT_ID_1.value, preKey)
        val userPreKeysResult = listOf(QualifiedUserPreKeyInfo(TEST_USER_ID_1, listOf(clientPreKeyInfo)))

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
                assertEquals(CoreFailure.Unknown(exception), it)
            }
    }

    private companion object {
        val TEST_USER_ID_1 = TestUser.USER_ID
        val TEST_CLIENT_ID_1 = TestClient.CLIENT_ID
        val TEST_RECIPIENT_1 = Recipient(Member(TestUser.USER_ID), listOf(TestClient.CLIENT_ID))
        val NETWORK_ERROR = NetworkFailure.ServerMiscommunication(TestNetworkExiption.generic)
    }
}
