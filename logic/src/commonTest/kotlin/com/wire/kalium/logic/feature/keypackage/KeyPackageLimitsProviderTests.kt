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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class KeyPackageLimitsProviderTests {

    @Test
    fun givenKeyPackageCountIs50PercentBelowLimit_WhenCallNeedRefill_ReturnTrue() = runTest {
        val keyPackageCount = (Arrangement.KEY_PACKAGE_LIMIT * Arrangement.KEY_PACKAGE_THRESHOLD - 1).toInt()
        val (_, keyPackageLimitsProvide) = Arrangement()
            .arrange()
        val actual = keyPackageLimitsProvide.needsRefill(keyPackageCount)
        assertTrue { actual }
    }

    @Test
    fun givenKeyPackageCountIs50PercentBelowLimit_WhenCallRefillAmount_ReturnCorrectAmount() = runTest {
        val keyPackageCount = (Arrangement.KEY_PACKAGE_LIMIT * Arrangement.KEY_PACKAGE_THRESHOLD - 1).toInt()
        val (_, keyPackageLimitsProvide) = Arrangement()
            .arrange()
        val actual = keyPackageLimitsProvide.refillAmount()
        assertEquals(Arrangement.KEY_PACKAGE_LIMIT, actual)
    }

    @Test
    fun givenKeyPackageCount50PercentAboveLimit_WhenCallNeedRefill_ThenReturnFalse() = runTest {
        val keyPackageCount = (Arrangement.KEY_PACKAGE_LIMIT * Arrangement.KEY_PACKAGE_THRESHOLD).toInt()
        val (_, keyPackageLimitsProvide) = Arrangement()
            .arrange()
        val actual = keyPackageLimitsProvide.needsRefill(keyPackageCount)
        assertFalse { actual }
    }

    private class Arrangement {

        val kaliumConfigs = KaliumConfigs()

        fun arrange() = this to KeyPackageLimitsProviderImpl(
            kaliumConfigs
        )

        companion object {
            const val KEY_PACKAGE_LIMIT = 100
            const val KEY_PACKAGE_THRESHOLD = 0.5F
        }
    }
}
