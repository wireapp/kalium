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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProviderImpl
import com.wire.kalium.logic.data.keypackage.KeyPackagePool
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class KeyPackageLimitsProviderTests {

    @Test
    fun givenNoMigrationConfiguration_whenGettingThePool_thenTargetIs100() = runTest {
        assertEquals(100, provider(migration = null).keyPackagePool().targetSize)
    }

    @Test
    fun givenDisabledMigration_whenGettingThePool_thenTargetIs100() = runTest {
        assertEquals(100, provider(MIGRATION.copy(status = Status.DISABLED)).keyPackagePool().targetSize)
    }

    @Test
    fun givenMigrationScheduledInTheFuture_whenGettingThePool_thenTargetIs1000() = runTest {
        val migration = MIGRATION.copy(startTime = Instant.DISTANT_FUTURE, endTime = Instant.DISTANT_FUTURE)

        assertEquals(1000, provider(migration).keyPackagePool().targetSize)
    }

    @Test
    fun givenOngoingMigration_whenGettingThePool_thenTargetIs1000() = runTest {
        assertEquals(1000, provider(MIGRATION.copy(startTime = Instant.DISTANT_PAST)).keyPackagePool().targetSize)
    }

    @Test
    fun givenEndedMigration_whenGettingThePool_thenTargetIs100() = runTest {
        val migration = MIGRATION.copy(startTime = Instant.DISTANT_PAST, endTime = Instant.DISTANT_PAST)

        assertEquals(100, provider(migration).keyPackagePool().targetSize)
    }

    @Test
    fun givenLowerLimits_whenGettingLimits_thenTheyStayAt10EvenDuringAMigration() = runTest {
        val provider = provider(MIGRATION, KaliumConfigs(lowerKeyPackageLimits = true))

        assertEquals(10, provider.keyPackagePool().targetSize)
        assertEquals(10, provider.refillAmount())
    }

    @Test
    fun whenGettingRefillAmount_thenReturnTheSizeOfOneUploadRequest() = runTest {
        assertEquals(100, provider(MIGRATION).refillAmount())
        assertEquals(100, provider(migration = null).refillAmount())
    }

    @Test
    fun givenAPool_whenCheckingNeedsRefill_thenRefillBelowHalfOfTheTarget() {
        val pool = KeyPackagePool(targetSize = 1000)

        assertTrue(pool.needsRefill(499))
        assertFalse(pool.needsRefill(500))
    }

    @Test
    fun givenAPool_whenGettingMissingKeyPackages_thenReturnTheDifferenceToTheTarget() {
        val pool = KeyPackagePool(targetSize = 1000)

        assertEquals(1000, pool.missingKeyPackages(0))
        assertEquals(501, pool.missingKeyPackages(499))
        assertEquals(0, pool.missingKeyPackages(1200))
    }

    private suspend fun provider(
        migration: MLSMigrationModel?,
        kaliumConfigs: KaliumConfigs = KaliumConfigs(),
    ): KeyPackageLimitsProviderImpl {
        val userConfigRepository = mock<UserConfigRepository>()
        everySuspend {
            userConfigRepository.getMigrationConfiguration()
        } returns (migration?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound))
        return KeyPackageLimitsProviderImpl(kaliumConfigs, userConfigRepository)
    }

    private companion object {
        val MIGRATION = MLSMigrationModel(startTime = null, endTime = null, status = Status.ENABLED)
    }
}
