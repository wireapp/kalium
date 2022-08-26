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
        val actual = keyPackageLimitsProvide.refillAmount(keyPackageCount)
        assertEquals(Arrangement.KEY_PACKAGE_LIMIT - keyPackageCount, actual)
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
