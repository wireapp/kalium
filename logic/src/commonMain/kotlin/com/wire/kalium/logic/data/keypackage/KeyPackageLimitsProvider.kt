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

interface KeyPackageLimitsProvider {

    fun needsRefill(keyPackageCount: Int): Boolean

    fun refillAmount(): Int

}

class KeyPackageLimitsProviderImpl(
    private val kaliumConfigs: KaliumConfigs
) : KeyPackageLimitsProvider {

    private val keyPackageUploadLimit: Int
        get() = if (kaliumConfigs.lowerKeyPackageLimits) KEY_PACKAGE_LIMIT_LOW else KEY_PACKAGE_LIMIT

    private val keyPackageUploadThreshold: Float
        get() = KEY_PACKAGE_THRESHOLD

    override fun needsRefill(keyPackageCount: Int): Boolean {
        return keyPackageCount < (keyPackageUploadLimit * keyPackageUploadThreshold)
    }

    override fun refillAmount() = keyPackageUploadLimit

    companion object {
        internal const val KEY_PACKAGE_LIMIT = 100
        internal const val KEY_PACKAGE_LIMIT_LOW = 10
        internal const val KEY_PACKAGE_THRESHOLD = 0.5F
    }
}
