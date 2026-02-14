/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.client

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.client.IsAllowedToUseAsyncNotificationsUseCaseImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsAllowedToUseAsyncNotificationsUseCaseTest {

    @Test
    fun givenAllowedByFeatureFlagAndBE_whenChecking_thenReturnTrue() = runTest {
        val (_, sut) = Arrangement()
            .withAsyncNotificationsEnabled(isEnabled = true)
            .arrange(isAllowedByApiVersion = true)

        val result = sut()

        assertTrue(result)
    }

    @Test
    fun givenAllowedByFeatureFlagButNotFromBE_whenChecking_thenReturnFalse() = runTest {
        val (_, sut) = Arrangement()
            .withAsyncNotificationsEnabled(isEnabled = true)
            .arrange(isAllowedByApiVersion = false)

        val result = sut()

        assertFalse(result)
    }

    @Test
    fun givenNOTAllowedByTeamFeatureFlag_whenChecking_thenReturnFalse() = runTest {
        val (_, sut) = Arrangement()
            .withAsyncNotificationsEnabled(isEnabled = false)
            .arrange(isAllowedByApiVersion = true)

        val result = sut()

        assertFalse(result)
    }

    private class Arrangement {

        private val userConfigRepository = mock<UserConfigRepository>()

        suspend fun withAsyncNotificationsEnabled(isEnabled: Boolean = false) = apply {
            everySuspend { userConfigRepository.isAsyncNotificationsEnabled() } returns isEnabled
        }

        fun arrange(isAllowedByApiVersion: Boolean) = this to IsAllowedToUseAsyncNotificationsUseCaseImpl(
            userConfigRepository = userConfigRepository,
            isAllowedByCurrentBackendVersionProvider = { isAllowedByApiVersion }
        )
    }
}
