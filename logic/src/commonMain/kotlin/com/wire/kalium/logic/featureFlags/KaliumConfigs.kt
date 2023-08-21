/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.featureFlags

import com.wire.kalium.network.session.CertificatePinning

abstract class KaliumConfigs(
    open val forceConstantBitrateCalls: Boolean = false,
    open val fileRestrictionState: BuildFileRestrictionState = BuildFileRestrictionState.NoRestriction,
    open var isMLSSupportEnabled: Boolean = true,
    // Disabling db-encryption will crash on android-api level below 30
    open val shouldEncryptData: Boolean = true,
    open val encryptProteusStorage: Boolean = false,
    open val lowerKeyPackageLimits: Boolean = false,
    open val lowerKeyingMaterialsUpdateThreshold: Boolean = false,
    open val developmentApiEnabled: Boolean = false,
    open val ignoreSSLCertificatesForUnboundCalls: Boolean = true,
    open val guestRoomLink: Boolean = true,
    open val selfDeletingMessages: Boolean = true,
    open val wipeOnCookieInvalid: Boolean = false,
    open val wipeOnDeviceRemoval: Boolean = false,
    open val wipeOnRootedDevice: Boolean = false,
    open val isWebSocketEnabledByDefault: Boolean = false
) {
    abstract fun certPinningConfig(): CertificatePinning
}

sealed interface BuildFileRestrictionState {
    object NoRestriction : BuildFileRestrictionState
    data class AllowSome(val allowedType: List<String>) : BuildFileRestrictionState
}
