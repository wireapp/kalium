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

internal interface KeyPackageLimitsProvider {

    fun needsRefill(keyPackageCount: Int): Boolean

    /**
     * Number of key packages generated and uploaded in one request.
     */
    fun refillAmount(): Int

    /**
     * Number of key packages missing from the target pool.
     */
    fun missingKeyPackages(keyPackageCount: Int): Int

}

internal class KeyPackageLimitsProviderImpl(
    private val kaliumConfigs: KaliumConfigs
) : KeyPackageLimitsProvider {

    private val targetPoolSize: Int
        get() = if (kaliumConfigs.lowerKeyPackageLimits) KEY_PACKAGE_LIMIT_LOW else KEY_PACKAGE_LIMIT

    override fun needsRefill(keyPackageCount: Int): Boolean =
        keyPackageCount < targetPoolSize * KEY_PACKAGE_THRESHOLD

    override fun refillAmount(): Int = minOf(targetPoolSize, KEY_PACKAGE_UPLOAD_BATCH_SIZE)

    override fun missingKeyPackages(keyPackageCount: Int): Int =
        (targetPoolSize - keyPackageCount).coerceAtLeast(0)

    internal companion object {
        internal const val KEY_PACKAGE_LIMIT = 1000
        internal const val KEY_PACKAGE_LIMIT_LOW = 10
        internal const val KEY_PACKAGE_UPLOAD_BATCH_SIZE = 100
        internal const val KEY_PACKAGE_THRESHOLD = 0.5F
    }
}
