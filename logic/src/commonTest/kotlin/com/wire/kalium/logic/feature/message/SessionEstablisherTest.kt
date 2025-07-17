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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoClientId
import com.wire.kalium.cryptography.CryptoSessionId
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.message.SessionEstablisher
import com.wire.kalium.logic.data.message.SessionEstablisherImpl
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.prekey.UsersWithoutSessions
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.prekey.PreKeyDTO
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SessionEstablisherTest {

    @Test
    fun givenAllSessionsAreEstablishedAlready_whenPreparingSessions_thenItShouldSucceed() = runTest {
        val (arrangement, sessionEstablisher) = Arrangement()
            .withDoesSessionExist(true)
            .arrange {}

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(arrangement.proteusContext, listOf(TEST_RECIPIENT_1))
            .shouldSucceed()
    }

    @Test
    fun givenProteusClientThrowsWhenCheckingSession_whenPreparingSessions_thenItShouldFail() = runTest {
        val exception = ProteusException("PANIC!!!11!eleven!", ProteusException.Code.PANIC, 15)

        val (arrangement, sessionEstablisher) = Arrangement()
            .withDoesSessionExistThrows(exception)
            .arrange {}

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(arrangement.proteusContext, listOf(TEST_RECIPIENT_1))
            .shouldFail {
                assertIs<ProteusFailure>(it)
                assertEquals(exception, it.proteusException)
            }
    }

    @Test
    fun givenAllSessionsAreEstablishedAlready_whenPreparingSessions_thenPreKeyRepositoryShouldNotBeCalled() = runTest {
        val (arrangement, sessionEstablisher) = Arrangement()
            .withDoesSessionExist(true)
            .arrange {}

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(arrangement.proteusContext, listOf(TEST_RECIPIENT_1))

        coVerify {
            arrangement.preKeyRepository.establishSessions(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenARecipient_whenPreparingSessions_thenProteusClientShouldCheckIfSessionExists() = runTest {
        val (arrangement, sessionEstablisher) = Arrangement()
            .withDoesSessionExist(true)
            .arrange {}

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(arrangement.proteusContext, listOf(TEST_RECIPIENT_1))

        coVerify {
            arrangement.proteusContext.doesSessionExist(
                eq(
                    CryptoSessionId(
                        CryptoUserID(TEST_USER_ID_1.value, TEST_USER_ID_1.domain),
                        CryptoClientId(TEST_CLIENT_ID_1.value)
                    )
                )
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCreatingSessionsSucceeds_whenPreparingSessions_thenItShouldSucceed() = runTest {
        val preKey = PreKeyDTO(42, "encodedData")
        val userPreKeysResult = mapOf(TEST_USER_ID_1.domain to mapOf(TEST_USER_ID_1.value to mapOf(TEST_CLIENT_ID_1.value to preKey)))

        val (arrangement, sessionEstablisher) = Arrangement()
            .withDoesSessionExist(false)
            .withEstablishSessions(Either.Right(UsersWithoutSessions.EMPTY))
            .arrange {}

        sessionEstablisher.prepareRecipientsForNewOutgoingMessage(arrangement.proteusContext, listOf(TEST_RECIPIENT_1))
            .shouldSucceed()
    }

    private companion object {
        val TEST_USER_ID_1 = TestUser.USER_ID
        val TEST_CLIENT_ID_1 = TestClient.CLIENT_ID
        val TEST_RECIPIENT_1 = Recipient(TestUser.USER_ID, listOf(TestClient.CLIENT_ID))
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val preKeyRepository = mock(PreKeyRepository::class)

        private val sessionEstablisher: SessionEstablisher =
            SessionEstablisherImpl(preKeyRepository)

        suspend fun withEstablishSessions(result: Either<CoreFailure, UsersWithoutSessions>) = apply {
            coEvery {
                preKeyRepository.establishSessions(any(), any())
            }.returns(result)
        }

        suspend fun withDoesSessionExist(result: Boolean) = apply {
            coEvery {
                proteusContext.doesSessionExist(any())
            }.returns(result)
        }

        suspend fun withDoesSessionExistThrows(throwable: Throwable) = apply {
            coEvery {
                proteusContext.doesSessionExist(any())
            }.throws(throwable)
        }

        fun arrange(block: suspend Arrangement.() -> Unit) = let {
            runBlocking { block() }
            this to sessionEstablisher
        }
    }
}
