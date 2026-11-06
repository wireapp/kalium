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

package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProviderImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyPackageLimitsProviderTests {

    private val provider = KeyPackageLimitsProviderImpl(KaliumConfigs())
    private val lowLimitsProvider = KeyPackageLimitsProviderImpl(KaliumConfigs(lowerKeyPackageLimits = true))

    @Test
    fun givenCountBelowHalfOfTheTarget_whenCheckingNeedsRefill_thenReturnTrue() {
        assertTrue(provider.needsRefill(499))
    }

    @Test
    fun givenCountAtHalfOfTheTarget_whenCheckingNeedsRefill_thenReturnFalse() {
        assertFalse(provider.needsRefill(500))
    }

    @Test
    fun givenCount_whenGettingMissingKeyPackages_thenReturnDifferenceToTheTarget() {
        assertEquals(1000, provider.missingKeyPackages(0))
        assertEquals(501, provider.missingKeyPackages(499))
        assertEquals(0, provider.missingKeyPackages(1200))
    }

    @Test
    fun whenGettingRefillAmount_thenReturnTheSizeOfOneUploadRequest() {
        assertEquals(100, provider.refillAmount())
    }

    @Test
    fun givenLowerLimits_whenCheckingLimits_thenTargetIs10() {
        assertTrue(lowLimitsProvider.needsRefill(4))
        assertFalse(lowLimitsProvider.needsRefill(5))
        assertEquals(10, lowLimitsProvider.refillAmount())
        assertEquals(6, lowLimitsProvider.missingKeyPackages(4))
    }
}
