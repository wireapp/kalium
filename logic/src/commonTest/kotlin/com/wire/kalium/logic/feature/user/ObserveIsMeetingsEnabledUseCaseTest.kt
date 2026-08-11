/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.user

import app.cash.turbine.test
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class ObserveIsMeetingsEnabledUseCaseTest {

    private fun testMeetings(enabled: Boolean, supported: Boolean, expected: Boolean) = runTest {
        val (_, useCase) = Arrangement()
            .withMeetingsEnabledFlow(flowOf(enabled))
            .withMeetingsSupported(supported)
            .arrange()

        useCase.invoke().test {
            assertEquals(expected, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenMeetingsEnabledAndSupported_whenInvoked_thenReturnsTrue() =
        testMeetings(enabled = true, supported = true, expected = true)

    @Test
    fun givenMeetingsEnabledAndNotSupported_whenInvoked_thenReturnsFalse() =
        testMeetings(enabled = true, supported = false, expected = false)

    @Test
    fun givenMeetingsNotEnabledAndSupported_whenInvoked_thenReturnsFalse() =
        testMeetings(enabled = false, supported = true, expected = false)

    @Test
    fun givenMeetingsNotEnabledAndNotSupported_whenInvoked_thenReturnsFalse() =
        testMeetings(enabled = false, supported = false, expected = false)

    @Test
    fun givenMeetingsNotEnabledAndSupported_andChangesToEnabled_whenInvoked_thenReturnsFalseAndThenTrue() = runTest {
        val meetingsEnabledFlow = MutableStateFlow(false)
        val (_, useCase) = Arrangement()
            .withMeetingsEnabledFlow(meetingsEnabledFlow)
            .withMeetingsSupported(true)
            .arrange()

        useCase.invoke().test {
            assertEquals(false, awaitItem())

            meetingsEnabledFlow.value = true

            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class Arrangement {
        val userConfigRepository: UserConfigRepository = mock()
        val featureSupport: FeatureSupport = mock()

        fun withMeetingsEnabledFlow(enabled: Flow<Boolean>) = apply {
            every { userConfigRepository.observeIsMeetingsEnabled() } returns enabled
        }

        fun withMeetingsSupported(supported: Boolean) = apply {
            every { featureSupport.isMeetingsSupported } returns supported
        }

        fun arrange() = this to ObserveIsMeetingsEnabledUseCaseImpl(userConfigRepository, featureSupport)
    }
}
