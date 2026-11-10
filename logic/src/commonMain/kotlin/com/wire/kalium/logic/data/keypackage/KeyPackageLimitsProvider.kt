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

import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.isUpcomingOrOngoing
import com.wire.kalium.logic.featureFlags.KaliumConfigs

internal data class KeyPackagePool(val targetSize: Int) {

    fun needsRefill(keyPackageCount: Int): Boolean = keyPackageCount < targetSize / 2

    fun missingKeyPackages(keyPackageCount: Int): Int = (targetSize - keyPackageCount).coerceAtLeast(0)
}

internal interface KeyPackageLimitsProvider {

    // Number of key packages generated and uploaded in one request.
    fun refillAmount(): Int

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
            // Grow the pool before the start date too, so clients are ready when conversations migrate.
            userConfigRepository.getMigrationConfiguration().getOrNull()?.isUpcomingOrOngoing() == true ->
                KEY_PACKAGE_LIMIT_MLS_MIGRATION

            else -> KEY_PACKAGE_LIMIT
        }
    )

    internal companion object {
        internal const val KEY_PACKAGE_LIMIT = 100
        internal const val KEY_PACKAGE_LIMIT_MLS_MIGRATION = 1000
        internal const val KEY_PACKAGE_LIMIT_LOW = 10
        internal const val KEY_PACKAGE_UPLOAD_BATCH_SIZE = 100
    }
}
