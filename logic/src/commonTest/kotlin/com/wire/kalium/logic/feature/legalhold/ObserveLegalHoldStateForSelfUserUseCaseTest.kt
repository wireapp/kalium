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
package com.wire.kalium.logic.feature.legalhold

import com.wire.kalium.logic.framework.TestUser
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveLegalHoldStateForSelfUserUseCaseTest {

    private fun testLegalHoldStateForSelfUser(
        givenLegalHoldState: LegalHoldState,
        givenLegalHoldRequestState: ObserveLegalHoldRequestUseCase.Result,
        expected: LegalHoldStateForSelfUser
    ) = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withLegalHoldState(givenLegalHoldState)
            .withLegalHoldRequestState(givenLegalHoldRequestState)
            .arrange()
        // when
        val result = useCase()
        // then
        assertEquals(expected, result.first())
        verify(arrangement.observeLegalHoldStateForUser)
            .suspendFunction(arrangement.observeLegalHoldStateForUser::invoke)
            .with(eq(TestUser.SELF.id))
            .wasInvoked(once)
        verify(arrangement.observeLegalHoldRequestUseCase)
            .function(arrangement.observeLegalHoldRequestUseCase::invoke)
            .wasInvoked(once)
    }

    @Test
    fun givenLegalHoldEnabled_whenStartingObservingForSelfUser_thenEmitEnabled() =
        testLegalHoldStateForSelfUser(
            givenLegalHoldState = LegalHoldState.Enabled,
            givenLegalHoldRequestState = ObserveLegalHoldRequestUseCase.Result.NoLegalHoldRequest,
            expected = LegalHoldStateForSelfUser.Enabled
        )

    @Test
    fun givenLegalHoldRequestAvailable_whenStartingObservingForSelfUser_thenEmitRequestPending() =
        testLegalHoldStateForSelfUser(
            givenLegalHoldState = LegalHoldState.Disabled,
            givenLegalHoldRequestState = ObserveLegalHoldRequestUseCase.Result.LegalHoldRequestAvailable("fingerprint".toByteArray()),
            expected = LegalHoldStateForSelfUser.PendingRequest
        )

    @Test
    fun givenLegalHoldDisabledAndNoRequestPending_whenStartingObservingForSelfUser_thenEmitDisabled() =
        testLegalHoldStateForSelfUser(
            givenLegalHoldState = LegalHoldState.Disabled,
            givenLegalHoldRequestState = ObserveLegalHoldRequestUseCase.Result.NoLegalHoldRequest,
            expected = LegalHoldStateForSelfUser.Disabled
        )

    class Arrangement {

        @Mock
        val observeLegalHoldStateForUser = mock(ObserveLegalHoldStateForUserUseCase::class)

        @Mock
        val observeLegalHoldRequestUseCase = mock(ObserveLegalHoldRequestUseCase::class)

        private val observeLegalHoldForSelfUser: ObserveLegalHoldStateForSelfUserUseCase =
            ObserveLegalHoldStateForSelfUserUseCaseImpl(
                selfUserId = TestUser.SELF.id,
                observeLegalHoldStateForUser = observeLegalHoldStateForUser,
                observeLegalHoldRequestUseCase = observeLegalHoldRequestUseCase,
            )

        fun arrange() = this to observeLegalHoldForSelfUser

        fun withLegalHoldState(result: LegalHoldState) = apply {
            given(observeLegalHoldStateForUser)
                .suspendFunction(observeLegalHoldStateForUser::invoke)
                .whenInvokedWith(eq(TestUser.SELF.id))
                .then { flowOf(result) }
        }

        fun withLegalHoldRequestState(result: ObserveLegalHoldRequestUseCase.Result) = apply {
            given(observeLegalHoldRequestUseCase)
                .function(observeLegalHoldRequestUseCase::invoke)
                .whenInvoked()
                .then { flowOf(result) }
        }
    }
}
