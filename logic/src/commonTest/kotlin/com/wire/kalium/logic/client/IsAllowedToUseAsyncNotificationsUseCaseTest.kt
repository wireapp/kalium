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

import com.wire.kalium.logic.feature.client.IsAllowedToUseAsyncNotificationsUseCaseImpl
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsAllowedToUseAsyncNotificationsUseCaseTest {

    @Test
    fun givenAllowedByFeatureFlagAndBE_whenChecking_thenReturnTrue() {
        val sut = IsAllowedToUseAsyncNotificationsUseCaseImpl(
            isAllowedByFeatureFlag = true,
            isAllowedByCurrentBackendVersionProvider = { true }
        )

        val result = sut()

        assertTrue(result)
    }

    @Test
    fun givenAllowedByFeatureFlagButNotFromBE_whenChecking_thenReturnFalse() {
        val sut = IsAllowedToUseAsyncNotificationsUseCaseImpl(
            isAllowedByFeatureFlag = true,
            isAllowedByCurrentBackendVersionProvider = { false }
        )

        val result = sut()

        assertFalse(result)
    }

    @Test
    fun givenNOTAllowedByFeatureFlag_whenChecking_thenReturnFalse() {
        val sut = IsAllowedToUseAsyncNotificationsUseCaseImpl(
            isAllowedByFeatureFlag = false,
            isAllowedByCurrentBackendVersionProvider = { true }
        )

        val result = sut()

        assertFalse(result)
    }
}
