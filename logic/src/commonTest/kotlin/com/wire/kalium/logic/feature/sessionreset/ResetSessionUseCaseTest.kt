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

package com.wire.kalium.logic.feature.sessionreset

import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.message.SessionResetSender
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ResetSessionUseCaseTest {

    @Mock
    val proteusClientProvider = mock(ProteusClientProvider::class)

    @Mock
    val sessionResetSender = mock(SessionResetSender::class)

    @Mock
    private val messageRepository = mock(MessageRepository::class)

    @Mock
    val proteusClient = mock(ProteusClient::class)

    @Mock
    val idMapper = mock(IdMapper::class)

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
        coEvery {
            proteusClientProvider.getOrError()
        }.returns(Either.Left(failure))

        val result = resetSessionUseCase(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        coVerify {
            proteusClientProvider.getOrError()
        }.wasInvoked(exactly = once)
        assertEquals(ResetSessionResult.Failure(failure), result)
    }

    @Test
    fun givenAnErrorWhenSendingSessionReset_whenResettingSession_ThenReturnFailure() = runTest(testDispatchers.io) {
        coEvery {
            proteusClientProvider.getOrError()
        }.returns(Either.Right(proteusClient))

        every {

            idMapper.toCryptoQualifiedIDId(eq(TestClient.USER_ID))

        }.returns(CRYPTO_USER_ID)

        coEvery {
            sessionResetSender.invoke(eq(TestClient.CONVERSATION_ID), eq(TestClient.USER_ID), eq(TestClient.CLIENT_ID))
        }.returns(Either.Left(failure))

        val result = resetSessionUseCase(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        verify {
            idMapper.toCryptoQualifiedIDId(any())
        }.wasInvoked(exactly = once)

        coVerify {
            proteusClient.deleteSession(any())
        }.wasInvoked(exactly = once)

        coVerify {
            sessionResetSender.invoke(eq(TestClient.CONVERSATION_ID), eq(TestClient.USER_ID), eq(TestClient.CLIENT_ID))
        }.wasInvoked(exactly = once)

        assertEquals(ResetSessionResult.Failure(failure), result)
    }

    @Test
    fun givenMarkingDecryptionFailureAsResolvedFailed_whenResettingSession_ThenReturnFailure() = runTest(testDispatchers.io) {
        coEvery {
            proteusClientProvider.getOrError()
        }.returns(Either.Right(proteusClient))

        every {

            idMapper.toCryptoQualifiedIDId(eq(TestClient.USER_ID))

        }.returns(CRYPTO_USER_ID)

        coEvery {
            sessionResetSender.invoke(eq(TestClient.CONVERSATION_ID), eq(TestClient.USER_ID), eq(TestClient.CLIENT_ID))
        }.returns(Either.Right(Unit))

        coEvery {
            messageRepository.markMessagesAsDecryptionResolved(
                eq(TestClient.CONVERSATION_ID),
                eq(TestClient.USER_ID),
                eq(TestClient.CLIENT_ID)
            )
        }.returns(Either.Left(failure))

        val result = resetSessionUseCase(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        verify {
            idMapper.toCryptoQualifiedIDId(any())
        }.wasInvoked(exactly = once)

        coVerify {
            proteusClient.deleteSession(any())
        }.wasInvoked(exactly = once)

        coVerify {
            messageRepository.markMessagesAsDecryptionResolved(
                eq(TestClient.CONVERSATION_ID),
                eq(TestClient.USER_ID),
                eq(TestClient.CLIENT_ID)
            )
        }.wasInvoked(exactly = once)

        assertEquals(ResetSessionResult.Failure(failure), result)
    }

    @Test
    fun givenResetSessionCalled_whenRunningSuccessfully_thenReturnSuccessResult() = runTest(testDispatchers.io) {
        coEvery {
            proteusClientProvider.getOrError()
        }.returns(Either.Right(proteusClient))

        every {

            idMapper.toCryptoQualifiedIDId(eq(TestClient.USER_ID))

        }.returns(CRYPTO_USER_ID)

        coEvery {
            sessionResetSender.invoke(eq(TestClient.CONVERSATION_ID), eq(TestClient.USER_ID), eq(TestClient.CLIENT_ID))
        }.returns(Either.Right(Unit))

        coEvery {
            messageRepository.markMessagesAsDecryptionResolved(
                eq(TestClient.CONVERSATION_ID),
                eq(TestClient.USER_ID),
                eq(TestClient.CLIENT_ID)
            )
        }.returns(Either.Right(Unit))

        val result = resetSessionUseCase(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        verify {
            idMapper.toCryptoQualifiedIDId(any())
        }.wasInvoked(exactly = once)

        coVerify {
            proteusClient.deleteSession(any())
        }.wasInvoked(exactly = once)

        coVerify {
            messageRepository.markMessagesAsDecryptionResolved(
                eq(TestClient.CONVERSATION_ID),
                eq(TestClient.USER_ID),
                eq(TestClient.CLIENT_ID)
            )
        }.wasInvoked(exactly = once)

        assertEquals(ResetSessionResult.Success, result)

    }

    companion object {
        val CRYPTO_USER_ID = CryptoUserID("client-id", "domain")
        val failure = CoreFailure.Unknown(null)
    }

}
