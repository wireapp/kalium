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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.featureFlags.FeatureSupportImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class IsMeetingsEnabledUseCaseTest {

    private fun testMeetings(enabled: Boolean, supported: Boolean, expected: Boolean) = runTest {
        val (_, useCase) = Arrangement()
            .withMeetingsEnabled(enabled)
            .withMeetingsSupported(supported)
            .arrange()

        assertEquals(expected, useCase.invoke())
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
    fun guestLinkSharingRequiresMeetingsEnabledAndApiSupport() = runTest {
        for (meetingsEnabled in listOf(false, true)) {
            for (guestLinksSupported in listOf(false, true)) {
                val (_, useCase) = Arrangement()
                    .withMeetingsEnabled(meetingsEnabled)
                    .withGuestLinksSupported(guestLinksSupported)
                    .arrange()
                assertEquals(meetingsEnabled && guestLinksSupported, useCase.isGuestLinkSharingEnabled())
            }
        }
    }

    @Test
    fun guestLinkSharingRequiresApiV19WhileMeetingsRemainEnabledOnV17AndV18() = runTest {
        val repository: UserConfigRepository = mock()
        everySuspend { repository.isMeetingsEnabled() } returns true
        for (apiVersion in listOf(17, 18, 19)) {
            val useCase = IsMeetingsEnabledUseCaseImpl(repository, FeatureSupportImpl(apiVersion))
            assertEquals(true, useCase.invoke())
            assertEquals(apiVersion >= 19, useCase.isGuestLinkSharingEnabled())
        }
    }

    private class Arrangement {
        val userConfigRepository: UserConfigRepository = mock()
        val featureSupport: FeatureSupport = mock()

        fun withMeetingsEnabled(enabled: Boolean) = apply {
            everySuspend { userConfigRepository.isMeetingsEnabled() } returns enabled
        }
        fun withMeetingsSupported(supported: Boolean) = apply {
            everySuspend { featureSupport.isMeetingsSupported } returns supported
        }
        fun withGuestLinksSupported(supported: Boolean) = apply {
            everySuspend { featureSupport.isMeetingsGuestLinksSupported } returns supported
        }
        fun arrange() = this to IsMeetingsEnabledUseCaseImpl(userConfigRepository, featureSupport)
    }
}
