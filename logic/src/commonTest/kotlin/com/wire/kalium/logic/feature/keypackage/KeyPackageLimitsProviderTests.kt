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
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.answering.returns
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import kotlin.time.Instant

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

    @Test
    fun givenActiveMigration_whenCheckingRuntimeLimit_thenThresholdIs500AndTargetIs1000() = runTest {
        val (_, provider) = Arrangement()
            .withMigration(MLSMigrationModel(null, null, Status.ENABLED))
            .arrange()

        assertTrue(provider.needsRefill(499))
        assertFalse(provider.needsRefill(500))
        assertEquals(501, provider.refillAmount(499))
    }

    @Test
    fun givenActiveMigration_whenForcingFullPool_thenAnyCountBelow1000NeedsRefill() = runTest {
        val (_, provider) = Arrangement()
            .withMigration(MLSMigrationModel(null, Instant.DISTANT_FUTURE, Status.ENABLED))
            .arrange()

        assertTrue(provider.needsRefill(999, forceFullPool = true))
        assertFalse(provider.needsRefill(1000, forceFullPool = true))
    }

    @Test
    fun givenDisabledOrEndedMigration_whenCheckingLimits_thenDefaultBehaviorIsUsed() = runTest {
        listOf(
            MLSMigrationModel(null, null, Status.DISABLED),
            MLSMigrationModel(null, Instant.DISTANT_PAST, Status.ENABLED),
        ).forEach { migration ->
            val (_, provider) = Arrangement().withMigration(migration).arrange()

            assertTrue(provider.needsRefill(49))
            assertFalse(provider.needsRefill(50))
            assertEquals(100, provider.refillAmount(49))
        }
    }

    private class Arrangement {

        val kaliumConfigs = KaliumConfigs()
        val userConfigRepository: UserConfigRepository = mock()
        private var migration: MLSMigrationModel? = null

        fun withMigration(value: MLSMigrationModel) = apply { migration = value }

        suspend fun arrange(): Pair<Arrangement, KeyPackageLimitsProviderImpl> {
            val migrationResult: Either<StorageFailure, MLSMigrationModel> = migration
                ?.let { Either.Right(it) }
                ?: Either.Left(StorageFailure.DataNotFound)
            everySuspend { userConfigRepository.getMigrationConfiguration() } returns migrationResult
            return this to KeyPackageLimitsProviderImpl(kaliumConfigs, userConfigRepository)
        }

        companion object {
            const val KEY_PACKAGE_LIMIT = 100
            const val KEY_PACKAGE_THRESHOLD = 0.5F
        }
    }
}
