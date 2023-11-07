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

package com.wire.kalium.logic.feature.sessionreset

import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.feature.message.SessionResetSender
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class ResetSessionUseCaseTest {

    @Mock
    val proteusClientProvider = mock(classOf<ProteusClientProvider>())

    @Mock
    val sessionResetSender = mock(classOf<SessionResetSender>())

    @Mock
    val messageRepository = mock(classOf<MessageRepository>())

    @Mock
    val proteusClient = mock(classOf<ProteusClient>())

    @Mock
    val idMapper = mock(classOf<IdMapper>())

    private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher

    lateinit var resetSessionUseCase: ResetSessionUseCase

    @BeforeTest
    fun setup() {
        resetSessionUseCase = ResetSessionUseCaseImpl(
            proteusClientProvider,
            sessionResetSender,
            messageRepository,
            idMapper,
            testDispatchers
        )
    }

    @Test
    fun givenProteusProviderReturningFailure_whenResettingSession_ThenReturnFailure() = runTest(testDispatchers.io) {
        given(proteusClientProvider)
            .suspendFunction(proteusClientProvider::getOrError)
            .whenInvoked()
            .thenReturn(Either.Left(failure))

        val result = resetSessionUseCase(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        verify(proteusClientProvider)
            .suspendFunction(proteusClientProvider::getOrError)
            .wasInvoked(exactly = once)
        assertEquals(ResetSessionResult.Failure(failure), result)
    }

    @Test
    fun givenAnErrorWhenSendingSessionReset_whenResettingSession_ThenReturnFailure() = runTest(testDispatchers.io) {
        given(proteusClientProvider)
            .suspendFunction(proteusClientProvider::getOrError)
            .whenInvoked()
            .thenReturn(Either.Right(proteusClient))

        given(idMapper)
            .function(idMapper::toCryptoQualifiedIDId)
            .whenInvokedWith(eq(TestClient.USER_ID))
            .thenReturn(CRYPTO_USER_ID)

        given(sessionResetSender)
            .suspendFunction(sessionResetSender::invoke)
            .whenInvokedWith(eq(TestClient.CONVERSATION_ID), eq(TestClient.USER_ID), eq(TestClient.CLIENT_ID))
            .thenReturn(Either.Left(failure))

        val result = resetSessionUseCase(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        verify(idMapper)
            .function(idMapper::toCryptoQualifiedIDId)
            .with(anything())
            .wasInvoked(exactly = once)

        verify(proteusClient)
            .suspendFunction(proteusClient::deleteSession)
            .with(anything())
            .wasInvoked(exactly = once)

        verify(sessionResetSender)
            .function(sessionResetSender::invoke)
            .with(eq(TestClient.CONVERSATION_ID), eq(TestClient.USER_ID), eq(TestClient.CLIENT_ID))
            .wasInvoked(exactly = once)

        assertEquals(ResetSessionResult.Failure(failure), result)
    }

    @Test
    fun givenMarkingDecryptionFailureAsResolvedFailed_whenResettingSession_ThenReturnFailure() = runTest(testDispatchers.io) {
        given(proteusClientProvider)
            .suspendFunction(proteusClientProvider::getOrError)
            .whenInvoked()
            .thenReturn(Either.Right(proteusClient))

        given(idMapper)
            .function(idMapper::toCryptoQualifiedIDId)
            .whenInvokedWith(eq(TestClient.USER_ID))
            .thenReturn(CRYPTO_USER_ID)

        given(sessionResetSender)
            .suspendFunction(sessionResetSender::invoke)
            .whenInvokedWith(eq(TestClient.CONVERSATION_ID), eq(TestClient.USER_ID), eq(TestClient.CLIENT_ID))
            .thenReturn(Either.Right(Unit))

        given(messageRepository)
            .suspendFunction(messageRepository::markMessagesAsDecryptionResolved)
            .whenInvokedWith(eq(TestClient.CONVERSATION_ID), eq(TestClient.USER_ID), eq(TestClient.CLIENT_ID))
            .thenReturn(Either.Left(failure))

        val result = resetSessionUseCase(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        verify(idMapper)
            .function(idMapper::toCryptoQualifiedIDId)
            .with(anything())
            .wasInvoked(exactly = once)

        verify(proteusClient)
            .suspendFunction(proteusClient::deleteSession)
            .with(anything())
            .wasInvoked(exactly = once)

        verify(messageRepository)
            .function(messageRepository::markMessagesAsDecryptionResolved)
            .with(eq(TestClient.CONVERSATION_ID), eq(TestClient.USER_ID), eq(TestClient.CLIENT_ID))
            .wasInvoked(exactly = once)

        assertEquals(ResetSessionResult.Failure(failure), result)
    }

    @Test
    fun givenResetSessionCalled_whenRunningSuccessfully_thenReturnSuccessResult() = runTest(testDispatchers.io) {
        given(proteusClientProvider)
            .suspendFunction(proteusClientProvider::getOrError)
            .whenInvoked()
            .thenReturn(Either.Right(proteusClient))

        given(idMapper)
            .function(idMapper::toCryptoQualifiedIDId)
            .whenInvokedWith(eq(TestClient.USER_ID))
            .thenReturn(CRYPTO_USER_ID)

        given(sessionResetSender)
            .suspendFunction(sessionResetSender::invoke)
            .whenInvokedWith(eq(TestClient.CONVERSATION_ID), eq(TestClient.USER_ID), eq(TestClient.CLIENT_ID))
            .thenReturn(Either.Right(Unit))

        given(messageRepository)
            .suspendFunction(messageRepository::markMessagesAsDecryptionResolved)
            .whenInvokedWith(eq(TestClient.CONVERSATION_ID), eq(TestClient.USER_ID), eq(TestClient.CLIENT_ID))
            .thenReturn(Either.Right(Unit))

        val result = resetSessionUseCase(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        verify(idMapper)
            .function(idMapper::toCryptoQualifiedIDId)
            .with(anything())
            .wasInvoked(exactly = once)

        verify(proteusClient)
            .suspendFunction(proteusClient::deleteSession)
            .with(anything())
            .wasInvoked(exactly = once)

        verify(messageRepository)
            .function(messageRepository::markMessagesAsDecryptionResolved)
            .with(eq(TestClient.CONVERSATION_ID), eq(TestClient.USER_ID), eq(TestClient.CLIENT_ID))
            .wasInvoked(exactly = once)

        assertEquals(ResetSessionResult.Success, result)

    }

    companion object {
        val CRYPTO_USER_ID = CryptoUserID("client-id", "domain")
        val failure = CoreFailure.Unknown(null)
    }

}
