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

import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.featureConfig.isMigrationActive

internal interface KeyPackageLimitsProvider {

    suspend fun needsRefill(keyPackageCount: Int, forceFullPool: Boolean = false): Boolean

    fun refillAmount(): Int

    suspend fun refillAmount(keyPackageCount: Int): Int

    suspend fun usesExtendedPool(): Boolean

}

internal class KeyPackageLimitsProviderImpl(
    private val kaliumConfigs: KaliumConfigs,
    private val userConfigRepository: UserConfigRepository,
) : KeyPackageLimitsProvider {

    private val keyPackageUploadLimit: Int
        get() = if (kaliumConfigs.lowerKeyPackageLimits) KEY_PACKAGE_LIMIT_LOW else KEY_PACKAGE_LIMIT

    private val keyPackageUploadThreshold: Float
        get() = KEY_PACKAGE_THRESHOLD

    override suspend fun needsRefill(keyPackageCount: Int, forceFullPool: Boolean): Boolean =
        if (usesExtendedPool()) {
            if (forceFullPool) {
                keyPackageCount < KEY_PACKAGE_LIMIT_MLS_MIGRATION
            } else {
                keyPackageCount < (KEY_PACKAGE_LIMIT_MLS_MIGRATION * KEY_PACKAGE_THRESHOLD)
            }
        } else {
            keyPackageCount < (keyPackageUploadLimit * keyPackageUploadThreshold)
        }

    override fun refillAmount() = keyPackageUploadLimit

    override suspend fun refillAmount(keyPackageCount: Int): Int =
        if (usesExtendedPool()) {
            (KEY_PACKAGE_LIMIT_MLS_MIGRATION - keyPackageCount).coerceAtLeast(0)
        } else {
            keyPackageUploadLimit
        }

    override suspend fun usesExtendedPool(): Boolean =
        userConfigRepository.getMigrationConfiguration().getOrNull()?.isMigrationActive() ?: false

    internal companion object {
        internal const val KEY_PACKAGE_LIMIT = 100
        internal const val KEY_PACKAGE_LIMIT_MLS_MIGRATION = 1000
        internal const val KEY_PACKAGE_LIMIT_LOW = 10
        internal const val KEY_PACKAGE_THRESHOLD = 0.5F
    }
}
