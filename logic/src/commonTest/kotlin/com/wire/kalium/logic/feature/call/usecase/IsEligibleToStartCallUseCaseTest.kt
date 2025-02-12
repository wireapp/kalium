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

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IsEligibleToStartCallUseCaseTest {

    @Mock
    val userConfigRepository = mock(UserConfigRepository::class)

    @Mock
    val callRepository = mock(CallRepository::class)

    private lateinit var isEligibleToStartCall: IsEligibleToStartCallUseCase

    @BeforeTest
    fun setUp() {
        isEligibleToStartCall = IsEligibleToStartCallUseCaseImpl(
            userConfigRepository = userConfigRepository,
            callRepository = callRepository,
            dispatcher = TestKaliumDispatcher
        )
    }

    @Test
    fun givenAnStorageErrorOccurred_whenVerifyingIfUserIsEligibleToStartGroupCallWithNoEstablishedCall_thenReturnUnavailable() =
        runTest(TestKaliumDispatcher.main) {
            // given
            coEvery {
                callRepository.establishedCallConversationId()
            }.returns(null)

            every {
                userConfigRepository.isConferenceCallingEnabled()
            }.returns(Either.Left(StorageFailure.Generic(Throwable("error"))))

            // when
            val result = isEligibleToStartCall(
                conversationId,
                Conversation.Type.GROUP
            )

            // then
            assertEquals(result, ConferenceCallingResult.Disabled.Unavailable)
        }

    @Test
    fun givenAnStorageErrorOccurred_whenVerifyingIfUserIsEligibleToStartOneOnOneCallWithNoEstablishedCall_thenReturnEnabled() =
        runTest(TestKaliumDispatcher.main) {
            // given
            coEvery {
                callRepository.establishedCallConversationId()
            }.returns(null)

            every {
                userConfigRepository.isConferenceCallingEnabled()
            }.returns(Either.Left(StorageFailure.Generic(Throwable("error"))))

            // when
            val result = isEligibleToStartCall(
                conversationId,
                Conversation.Type.ONE_ON_ONE
            )

            // then
            assertEquals(result, ConferenceCallingResult.Enabled)
        }

    @Test
    fun givenAnStorageErrorOccurred_whenVerifyingIfUserIsEligibleToStartGroupCallWithOtherEstablishedCall_thenReturnUnavailable() =
        runTest(TestKaliumDispatcher.main) {
            // given
            coEvery {
                callRepository.establishedCallConversationId()
            }.returns(establishedCallConversationId)

            every {
                userConfigRepository.isConferenceCallingEnabled()
            }.returns(Either.Left(StorageFailure.Generic(Throwable("error"))))

            // when
            val result = isEligibleToStartCall(
                conversationId,
                Conversation.Type.GROUP
            )

            // then
            assertEquals(result, ConferenceCallingResult.Disabled.Unavailable)
        }

    @Test
    fun givenUserIsEligibleToStartCall_whenVerifyingIfUserIsEligibleToStartGroupCallWithOtherEstablishedCall_thenReturnOngoingCall() =
        runTest(TestKaliumDispatcher.main) {
            // given
            coEvery {
                callRepository.establishedCallConversationId()
            }.returns(establishedCallConversationId)

            every {
                userConfigRepository.isConferenceCallingEnabled()
            }.returns(Either.Right(true))

            // when
            val result = isEligibleToStartCall(
                conversationId,
                Conversation.Type.GROUP
            )

            // then
            assertEquals(result, ConferenceCallingResult.Disabled.OngoingCall)
        }

    @Test
    fun givenUserIsEligibleToStartCall_whenVerifyingIfUserIsEligibleToStartGroupCallWithSameEstablishedCall_thenReturnEstablished() =
        runTest(TestKaliumDispatcher.main) {
            // given
            coEvery {
                callRepository.establishedCallConversationId()
            }.returns(conversationId)

            every {
                userConfigRepository.isConferenceCallingEnabled()
            }.returns(Either.Right(true))

            // when
            val result = isEligibleToStartCall(
                conversationId,
                Conversation.Type.GROUP
            )

            // then
            assertEquals(result, ConferenceCallingResult.Disabled.Established)
        }

    private companion object {
        val conversationId = ConversationId(
            value = "convValue",
            domain = "convDomain"
        )
        val establishedCallConversationId = ConversationId(
            value = "establishedValue",
            domain = "establishedDomain"
        )
    }
}
