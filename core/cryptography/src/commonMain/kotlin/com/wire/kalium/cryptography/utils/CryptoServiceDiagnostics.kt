/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.cryptography.utils

/**
 * How a randomness or key generation service resolved on the device the app is running on.
 *
 * On JVM and Android which implementation backs an algorithm is decided at runtime by walking the
 * installed security providers, so it varies per device, per OEM and per OS version.
 */
sealed interface CryptoServiceStatus {

    data class Resolved(
        val algorithm: String,
        val providerName: String,
        val providerVersion: String,
    ) : CryptoServiceStatus

    data class Failed(val reason: String) : CryptoServiceStatus
}

/**
 * A snapshot of the randomness and key generation services this module relies on.
 *
 * @param strongSecureRandom what backs the strongest available secure random source, or why it is missing.
 * @param aesKeyGenerator what backs the AES key generator used to generate asset keys, or why it is missing.
 * @param secureRandomAlgorithms every secure random algorithm the installed providers offer.
 * @param keyGeneratorAlgorithms every key generator algorithm the installed providers offer.
 */
data class CryptoServiceReport(
    val strongSecureRandom: CryptoServiceStatus,
    val aesKeyGenerator: CryptoServiceStatus,
    val secureRandomAlgorithms: List<String>,
    val keyGeneratorAlgorithms: List<String>,
)

/**
 * Resolves the crypto services this module depends on, so they can be inspected on a real device.
 *
 * Never throws: a missing algorithm is logged and reported as [CryptoServiceStatus.Failed], because this
 * is diagnostics and must not be able to take the caller down.
 *
 * Only JVM and Android expose a security provider registry. Other platforms report
 * [CryptoServiceStatus.Failed] with empty algorithm lists.
 */
expect fun cryptoServiceReport(): CryptoServiceReport
