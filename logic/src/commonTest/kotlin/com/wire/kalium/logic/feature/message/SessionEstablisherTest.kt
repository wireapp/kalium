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
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.UsersWithoutSessions
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.message.SessionEstablisher
import com.wire.kalium.logic.data.message.SessionEstablisherImpl
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.prekey.PreKeyDTO
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SessionEstablisherTest {

    @Test
    fun givenAllSessionsAreEstablishedAlready_whenPreparingSessions_thenItShouldSucceed() = runTest {
        val (_, sessionEstablisher) = Arrangement()
            .withDoesSessionExist(true)
            .arrange()

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))
            .shouldSucceed()
    }

    @Test
    fun givenProteusClientThrowsWhenCheckingSession_whenPreparingSessions_thenItShouldFail() = runTest {
        val exception = ProteusException("PANIC!!!11!eleven!", ProteusException.Code.PANIC)

        val (_, sessionEstablisher) = Arrangement()
            .withDoesSessionExistThrows(exception)
            .arrange()

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))
            .shouldFail {
                assertIs<ProteusFailure>(it)
                assertEquals(exception, it.proteusException)
            }
    }

    @Test
    fun givenAllSessionsAreEstablishedAlready_whenPreparingSessions_thenPreKeyRepositoryShouldNotBeCalled() = runTest {
        val (arrangement, sessionEstablisher) = Arrangement()
            .withDoesSessionExist(true)
            .arrange()

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))

        verify(arrangement.preKeyRepository)
            .suspendFunction(arrangement.preKeyRepository::establishSessions)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenARecipient_whenPreparingSessions_thenProteusClientShouldCheckIfSessionExists() = runTest {
        val (arrangement, sessionEstablisher) = Arrangement()
            .withDoesSessionExist(true)
            .arrange()

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))

        verify(arrangement.proteusClient)
            .suspendFunction(arrangement.proteusClient::doesSessionExist)
            .with(eq(CryptoSessionId(CryptoUserID(TEST_USER_ID_1.value, TEST_USER_ID_1.domain), CryptoClientId(TEST_CLIENT_ID_1.value))))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenCreatingSessionsSucceeds_whenPreparingSessions_thenItShouldSucceed() = runTest {
        val preKey = PreKeyDTO(42, "encodedData")
        val userPreKeysResult = mapOf(TEST_USER_ID_1.domain to mapOf(TEST_USER_ID_1.value to mapOf(TEST_CLIENT_ID_1.value to preKey)))

        val (_, sessionEstablisher) = Arrangement()
            .withDoesSessionExist(false)
            .withEstablishSessions(Either.Right(UsersWithoutSessions.EMPTY))
            .arrange()

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(listOf(TEST_RECIPIENT_1))
            .shouldSucceed()
    }

    private companion object {
        val TEST_USER_ID_1 = TestUser.USER_ID
        val TEST_CLIENT_ID_1 = TestClient.CLIENT_ID
        val TEST_RECIPIENT_1 = Recipient(TestUser.USER_ID, listOf(TestClient.CLIENT_ID))
    }

    private class Arrangement {
        @Mock
        val proteusClient = configure(mock(ProteusClient::class)) { stubsUnitByDefault = true }

        @Mock
        val proteusClientProvider = mock(ProteusClientProvider::class)

        @Mock
        val preKeyRepository = configure(mock(PreKeyRepository::class)) { stubsUnitByDefault = true }

        private val sessionEstablisher: SessionEstablisher =
            SessionEstablisherImpl(proteusClientProvider, preKeyRepository)

        init {
            given(proteusClientProvider)
                .suspendFunction(proteusClientProvider::getOrError)
                .whenInvoked()
                .thenReturn(Either.Right(proteusClient))
        }

        fun withCreateSession(throwable: Throwable?) = apply {
            if (throwable == null) {
                given(proteusClient)
                    .suspendFunction(proteusClient::createSession)
                    .whenInvokedWith(anything(), anything())
                    .thenReturn(Unit)
            } else {
                given(proteusClient)
                    .suspendFunction(proteusClient::createSession)
                    .whenInvokedWith(anything(), anything())
                    .thenThrow(throwable)
            }

        }

        fun withEstablishSessions(result: Either<CoreFailure, UsersWithoutSessions>) = apply {
            given(preKeyRepository)
                .suspendFunction(preKeyRepository::establishSessions)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withDoesSessionExist(result: Boolean) = apply {
            given(proteusClient)
                .suspendFunction(proteusClient::doesSessionExist)
                .whenInvokedWith(anything())
                .then { result }
        }

        fun withDoesSessionExistThrows(throwable: Throwable) = apply {
            given(proteusClient)
                .suspendFunction(proteusClient::doesSessionExist)
                .whenInvokedWith(anything())
                .thenThrow(throwable)
        }


        fun arrange() = this to sessionEstablisher
    }
}
