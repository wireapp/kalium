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

import app.cash.turbine.test
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class HasConferenceCallingActivatedUseCaseTest {

    @Test
    fun givenOnlyDefaultConferenceCallingValue_whenNewValueIsNotPresent_thenDoNotReturnAnything() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withDefaultValue(listOf(false))
            .arrange()

        // when then
        useCase().test {
            awaitComplete()
        }
    }

    @Test
    fun givenDefaultConferenceCallingValueIsTrue_whenNewValueIsAlsoTrue_thenDoNotReturnAnything() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withDefaultValue(listOf(true, true))
            .arrange()

        // when then
        useCase().test {
            awaitComplete()
        }
    }

    @Test
    fun givenDefaultConferenceCallingValueIsTrue_whenNewValueIsFalse_thenDoNotReturnAnything() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withDefaultValue(listOf(true, false))
            .arrange()

        // when then
        useCase().test {
            awaitComplete()
        }
    }

    @Test
    fun givenDefaultConferenceCallingValueIsFalse_whenNewValueIsTrue_thenReturnResult() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withDefaultValue(listOf(false, true))
            .arrange()

        // when then
        useCase().test {
            assertTrue(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenDefaultConferenceCallingValueIsFalse_whenTwoNewValuesOfTrue_thenReturnOnlyOneResult() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withDefaultValue(listOf(false, true, true))
            .arrange()

        // when then
        useCase().test {
            assertTrue(awaitItem())
            awaitComplete()
        }
    }

    private class Arrangement {
        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        fun withDefaultValue(values: List<Boolean>) = apply {
            every {
                userConfigRepository.isConferenceCallingEnabledFlow()
            }.returns(values.map { Either.Right(it) }.asFlow())
        }

        fun arrange(): Pair<Arrangement, HasConferenceCallingActivatedUseCase> =
            this to HasConferenceCallingActivatedUseCaseImpl(userConfigRepository)
    }
}
