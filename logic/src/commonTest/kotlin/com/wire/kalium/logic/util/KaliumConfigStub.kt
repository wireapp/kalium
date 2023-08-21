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
package com.wire.kalium.logic.util

import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.session.CertificatePinning

data class KaliumConfigStub(
    override val forceConstantBitrateCalls: Boolean = false,
    override val fileRestrictionState: BuildFileRestrictionState = BuildFileRestrictionState.NoRestriction,
    override var isMLSSupportEnabled: Boolean = true,
    // Disabling db-encryption will crash on android-api level below 30
    override val shouldEncryptData: Boolean = true,
    override val encryptProteusStorage: Boolean = false,
    override val lowerKeyPackageLimits: Boolean = false,
    override val lowerKeyingMaterialsUpdateThreshold: Boolean = false,
    override val developmentApiEnabled: Boolean = false,
    override val ignoreSSLCertificatesForUnboundCalls: Boolean = true,
    override val guestRoomLink: Boolean = true,
    override val selfDeletingMessages: Boolean = true,
    override val wipeOnCookieInvalid: Boolean = false,
    override val wipeOnDeviceRemoval: Boolean = false,
    override val wipeOnRootedDevice: Boolean = false,
    override val isWebSocketEnabledByDefault: Boolean = false,
    private val certificatePinning: CertificatePinning = emptyMap()
): KaliumConfigs() {
    override fun certPinningConfig(): CertificatePinning = certificatePinning
}
