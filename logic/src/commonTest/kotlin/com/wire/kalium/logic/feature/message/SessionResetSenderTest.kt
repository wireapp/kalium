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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SessionResetSenderTest {

    @Mock
    private val slowSyncRepository: SlowSyncRepository = mock(SlowSyncRepository::class)

    @Mock
    private val provideClientId = mock(classOf<CurrentClientIdProvider>())

    @Mock
    private val messageSender = mock(classOf<MessageSender>())

    private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher

    private val completeStateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()

    lateinit var sessionResetSender: SessionResetSender

    @BeforeTest
    fun setup() {
        sessionResetSender = SessionResetSenderImpl(
            slowSyncRepository,
            TestClient.SELF_USER_ID,
            provideClientId,
            messageSender,
            testDispatchers
        )

        every {

            slowSyncRepository.slowSyncStatus

        }.returns(completeStateFlow)

    }

    @Test
    fun givenClientIdProvideAFailure_whenSendingSessionResetMessage_thenReturnFailure() = runTest(testDispatchers.io) {

        coEvery {
            provideClientId.invoke()
        }.returns(Either.Left(failure))

        val result = sessionResetSender(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        coVerify {
            provideClientId.invoke()
        }.wasInvoked(exactly = once)

        assertEquals(Either.Left(failure), result)

    }

    @Test
    fun givenMessageSenderFailure_whenSendingSessionResetMessage_thenReturnFailure() = runTest(testDispatchers.io) {

        coEvery {
            provideClientId.invoke()
        }.returns(Either.Right(TestClient.CLIENT_ID))

        coEvery {
            messageSender.sendMessage(any(), any())
        }.returns(Either.Left(failure))

        val result = sessionResetSender(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        coVerify {
            provideClientId.invoke()
        }.wasInvoked(exactly = once)

        coVerify {
            messageSender.sendMessage(any(), any())
        }.wasInvoked(exactly = once)

        assertEquals(Either.Left(failure), result)
    }

    @Test
    fun givenMessageSenderRanSuccessfully_whenSendingSessionResetMessage_thenReturnSuccess() = runTest(testDispatchers.io) {

        coEvery {
            provideClientId.invoke()
        }.returns(Either.Right(TestClient.CLIENT_ID))

        coEvery {
            messageSender.sendMessage(any(), any())
        }.returns(Either.Right(Unit))

        val result = sessionResetSender(TestClient.CONVERSATION_ID, TestClient.USER_ID, TestClient.CLIENT_ID)

        coVerify {
            provideClientId.invoke()
        }.wasInvoked(exactly = once)

        coVerify {
            messageSender.sendMessage(any(), any())
        }.wasInvoked(exactly = once)

        assertEquals(Either.Right(Unit), result)
    }

    companion object {
        val failure = CoreFailure.Unknown(null)
    }
}
