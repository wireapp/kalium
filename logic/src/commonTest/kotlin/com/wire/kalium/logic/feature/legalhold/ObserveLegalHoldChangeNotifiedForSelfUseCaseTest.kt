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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveLegalHoldChangeNotifiedForSelfUseCaseTest {

    private fun testResult(
        givenLegalHoldStateResult: LegalHoldState,
        givenIsNotifiedResult: Either<StorageFailure, Boolean>,
        expected: ObserveLegalHoldChangeNotifiedForSelfUseCase.Result,
    ) = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withLegalHoldChangeNotified(givenIsNotifiedResult)
            .withLegalHoldEnabledState(givenLegalHoldStateResult)
            .arrange()
        // when
        val result = useCase()
        // then
        assertEquals(expected, result.first())
    }
    @Test
    fun givenStorageError_whenObserving_thenEmitFailure() =
        StorageFailure.Generic(IOException()).let { failure ->
            testResult(
                givenLegalHoldStateResult = LegalHoldState.Enabled,
                givenIsNotifiedResult = Either.Left(failure),
                expected = ObserveLegalHoldChangeNotifiedForSelfUseCase.Result.Failure(failure)
            )
        }
    @Test
    fun givenLegalHoldForSelfEnabledAndNotYetNotified_whenObserving_thenEmitShouldNotifyWithLegalHoldEnabledState() = testResult(
        givenLegalHoldStateResult = LegalHoldState.Enabled,
        givenIsNotifiedResult = Either.Right(false),
        expected = ObserveLegalHoldChangeNotifiedForSelfUseCase.Result.ShouldNotify(LegalHoldState.Enabled)
    )
    @Test
    fun givenLegalHoldForSelfEnabledAndAlreadyNotified_whenObserving_thenEmitAlreadyNotified() = testResult(
        givenLegalHoldStateResult = LegalHoldState.Enabled,
        givenIsNotifiedResult = Either.Right(true),
        expected = ObserveLegalHoldChangeNotifiedForSelfUseCase.Result.AlreadyNotified
    )
    @Test
    fun givenLegalHoldForSelfDisabledAndNotYetNotified_whenObserving_thenEmitShouldNotifyWithLegalHoldDisabledState() = testResult(
        givenLegalHoldStateResult = LegalHoldState.Disabled,
        givenIsNotifiedResult = Either.Right(false),
        expected = ObserveLegalHoldChangeNotifiedForSelfUseCase.Result.ShouldNotify(LegalHoldState.Disabled)
    )
    @Test
    fun givenLegalHoldForSelfDisabledAndAlreadyNotified_whenObserving_thenEmitAlreadyNotified() = testResult(
        givenLegalHoldStateResult = LegalHoldState.Disabled,
        givenIsNotifiedResult = Either.Right(true),
        expected = ObserveLegalHoldChangeNotifiedForSelfUseCase.Result.AlreadyNotified
    )

    private class Arrangement {
        val selfUserId = TestUser.SELF.id
        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)
        @Mock
        val observeLegalHoldForUser = mock(ObserveLegalHoldStateForUserUseCase::class)
        val useCase: ObserveLegalHoldChangeNotifiedForSelfUseCase =
            ObserveLegalHoldChangeNotifiedForSelfUseCaseImpl(selfUserId, userConfigRepository, observeLegalHoldForUser)

        fun arrange() = this to useCase
        fun withLegalHoldEnabledState(result: LegalHoldState) = apply {
            given(observeLegalHoldForUser)
                .suspendFunction(observeLegalHoldForUser::invoke)
                .whenInvokedWith(any())
                .then { flowOf(result) }
        }
        fun withLegalHoldChangeNotified(result: Either<StorageFailure, Boolean>) = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::observeLegalHoldChangeNotified)
                .whenInvoked()
                .then { flowOf(result) }
        }
    }
}
