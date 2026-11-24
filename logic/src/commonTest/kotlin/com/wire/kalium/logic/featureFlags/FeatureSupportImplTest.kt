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

package com.wire.kalium.logic.featureFlags

import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureSupportImplTest {

    @Test
    fun givenMinimumSupportedApiVersionAndEnabledFlag_whenCheckingTargetedAndTransientMessagesSupport_thenReturnsTrue() {
        val featureSupport = FeatureSupportImpl(
            apiVersion = MLSMessageApi.MIN_API_VERSION_TARGETED_TRANSIENT_MESSAGES,
            kaliumConfigs = KaliumConfigs(targetedAndTransientMLSMessagesEnabled = true)
        )

        assertTrue(featureSupport.isTargetedAndTransientMessagesSupported)
    }

    @Test
    fun givenApiVersionBelowMinimumAndEnabledFlag_whenCheckingTargetedAndTransientMessagesSupport_thenReturnsFalse() {
        val featureSupport = FeatureSupportImpl(
            apiVersion = MLSMessageApi.MIN_API_VERSION_TARGETED_TRANSIENT_MESSAGES - 1,
            kaliumConfigs = KaliumConfigs(targetedAndTransientMLSMessagesEnabled = true)
        )

        assertFalse(featureSupport.isTargetedAndTransientMessagesSupported)
    }

    @Test
    fun givenSupportedApiVersionAndDisabledFlag_whenCheckingTargetedAndTransientMessagesSupport_thenReturnsFalse() {
        val featureSupport = FeatureSupportImpl(
            apiVersion = MLSMessageApi.MIN_API_VERSION_TARGETED_TRANSIENT_MESSAGES,
            kaliumConfigs = KaliumConfigs(targetedAndTransientMLSMessagesEnabled = false)
        )

        assertFalse(featureSupport.isTargetedAndTransientMessagesSupported)
    }
}
