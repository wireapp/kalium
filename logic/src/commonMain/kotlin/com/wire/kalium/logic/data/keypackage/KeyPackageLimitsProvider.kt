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

package com.wire.kalium.logic.data.keypackage

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.mlsmigration.isUpcomingOrOngoing
import com.wire.kalium.logic.featureFlags.KaliumConfigs

/**
 * How many key packages to keep on the backend, and when to refill them.
 */
internal data class KeyPackagePool(val targetSize: Int) {

    fun needsRefill(keyPackageCount: Int): Boolean = keyPackageCount < targetSize * REFILL_THRESHOLD

    fun missingKeyPackages(keyPackageCount: Int): Int = (targetSize - keyPackageCount).coerceAtLeast(0)

    private companion object {
        const val REFILL_THRESHOLD = 0.5F
    }
}

internal interface KeyPackageLimitsProvider {

    /**
     * Number of key packages generated and uploaded in one request.
     */
    fun refillAmount(): Int

    /**
     * The pool to keep on the backend. It's larger while an MLS migration is enabled and hasn't ended,
     * including before its start date, so clients are ready before conversations start migrating.
     */
    suspend fun keyPackagePool(): KeyPackagePool

}

internal class KeyPackageLimitsProviderImpl(
    private val kaliumConfigs: KaliumConfigs,
    private val userConfigRepository: UserConfigRepository,
) : KeyPackageLimitsProvider {

    override fun refillAmount(): Int =
        if (kaliumConfigs.lowerKeyPackageLimits) KEY_PACKAGE_LIMIT_LOW else KEY_PACKAGE_UPLOAD_BATCH_SIZE

    override suspend fun keyPackagePool(): KeyPackagePool = KeyPackagePool(
        targetSize = when {
            kaliumConfigs.lowerKeyPackageLimits -> KEY_PACKAGE_LIMIT_LOW
            isMLSMigrationUpcomingOrOngoing() -> KEY_PACKAGE_LIMIT_MLS_MIGRATION
            else -> KEY_PACKAGE_LIMIT
        }
    )

    private suspend fun isMLSMigrationUpcomingOrOngoing(): Boolean =
        userConfigRepository.getMigrationConfiguration().fold({ false }, { it.isUpcomingOrOngoing() })

    internal companion object {
        internal const val KEY_PACKAGE_LIMIT = 100
        internal const val KEY_PACKAGE_LIMIT_MLS_MIGRATION = 1000
        internal const val KEY_PACKAGE_LIMIT_LOW = 10
        internal const val KEY_PACKAGE_UPLOAD_BATCH_SIZE = 100
    }
}
