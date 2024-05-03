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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.user.MarkEnablingE2EIAsNotifiedUseCase
import com.wire.kalium.logic.feature.user.MarkEnablingE2EIAsNotifiedUseCaseImpl
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class MarkEnablingE2EIAsNotifiedUseCaseTest {

    @Test
    fun whenMarkAsNotifiedIsCalledWithMoreThen1Day_thenSnoozeIsCalledWith1day() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        useCase(2.days)

        coVerify {
            arrangement.userConfigRepository.snoozeE2EINotification(eq(1.days))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun whenMarkAsNotifiedIsCalledWithMoreThen4Hours_thenSnoozeIsCalledWith4Hours() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        useCase(5.hours)

        coVerify {
            arrangement.userConfigRepository.snoozeE2EINotification(eq(4.hours))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun whenMarkAsNotifiedIsCalledWithMoreThen1Hour_thenSnoozeIsCalledWith1Hour() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        useCase(2.hours)

        coVerify {
            arrangement.userConfigRepository.snoozeE2EINotification(eq(1.hours))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun whenMarkAsNotifiedIsCalledWithMoreThen15Minutes_thenSnoozeIsCalledWith15Minutes() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        useCase(16.minutes)

        coVerify {
            arrangement.userConfigRepository.snoozeE2EINotification(eq(15.minutes))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun whenMarkAsNotifiedIsCalledWithLessThen15Minutes_thenSnoozeIsCalledWith5Minutes() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        useCase(14.minutes)

        coVerify {
            arrangement.userConfigRepository.snoozeE2EINotification(eq(5.minutes))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        init {
            every {
                userConfigRepository.snoozeE2EINotification(any<Duration>())
            }.returns(Either.Right(Unit))
        }

        private var markMLSE2EIEnableChangeAsNotified: MarkEnablingE2EIAsNotifiedUseCase =
            MarkEnablingE2EIAsNotifiedUseCaseImpl(userConfigRepository)

        fun arrange() = this to markMLSE2EIEnableChangeAsNotified
    }
}
