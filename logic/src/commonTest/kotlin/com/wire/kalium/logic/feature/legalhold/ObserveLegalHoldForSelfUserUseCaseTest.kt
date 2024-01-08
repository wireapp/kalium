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

class ObserveLegalHoldForSelfUserUseCaseTest {

    @Test
    fun givenLegalHoldObserverForUserReturnsEnabled_whenStartingObservingForSelfUser_thenEmitEnabled() =
        runTest {
            val (arrangement, useCase) = Arrangement()
                .withLegalHoldEnabledState()
                .arrange()

            val result = useCase()

            assertEquals(LegalHoldState.Enabled, result.first())
            verify(arrangement.observeLegalHoldStateForUser)
                .suspendFunction(arrangement.observeLegalHoldStateForUser::invoke)
                .with(eq(TestUser.SELF.id))
                .wasInvoked(once)
        }

    @Test
    fun givenLegalHoldObserverForUserReturnsDisabled_whenStartingObservingForSelfUser_thenEmitDisabled() =
        runTest {
            val (arrangement, useCase) = Arrangement()
                .withLegalHoldDisabledState()
                .arrange()

            val result = useCase()

            assertEquals(LegalHoldState.Disabled, result.first())
            verify(arrangement.observeLegalHoldStateForUser)
                .suspendFunction(arrangement.observeLegalHoldStateForUser::invoke)
                .with(eq(TestUser.SELF.id))
                .wasInvoked(once)
        }

    private class Arrangement {

        @Mock
        val observeLegalHoldStateForUser = mock(ObserveLegalHoldStateForUserUseCase::class)

        val observeLegalHoldForSelfUser: ObserveLegalHoldForSelfUserUseCase =
            ObserveLegalHoldForSelfUserUseCaseImpl(
                selfUserId = TestUser.SELF.id,
                observeLegalHoldStateForUser = observeLegalHoldStateForUser
            )

        fun arrange() = this to observeLegalHoldForSelfUser

        fun withLegalHoldEnabledState() = apply {
            given(observeLegalHoldStateForUser)
                .suspendFunction(observeLegalHoldStateForUser::invoke)
                .whenInvokedWith(eq(TestUser.SELF.id))
                .then {
                    flowOf(LegalHoldState.Enabled)
                }
        }

        fun withLegalHoldDisabledState() = apply {
            given(observeLegalHoldStateForUser)
                .suspendFunction(observeLegalHoldStateForUser::invoke)
                .whenInvokedWith(eq(TestUser.SELF.id))
                .then {
                    flowOf(LegalHoldState.Disabled)
                }
        }
    }
}
