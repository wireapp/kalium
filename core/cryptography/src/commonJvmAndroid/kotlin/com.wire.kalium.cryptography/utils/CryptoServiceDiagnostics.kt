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
@file:JvmName("CryptoServiceDiagnosticsJvm")

package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.kaliumLogger
import java.security.NoSuchAlgorithmException
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import javax.crypto.KeyGenerator

actual fun cryptoServiceReport(): CryptoServiceReport = CryptoServiceReport(
    strongSecureRandom = strongSecureRandomStatus(),
    aesKeyGenerator = aesKeyGeneratorStatus(),
    secureRandomAlgorithms = Security.getAlgorithms(SECURE_RANDOM_SERVICE).sorted(),
    keyGeneratorAlgorithms = Security.getAlgorithms(KEY_GENERATOR_SERVICE).sorted(),
)

/**
 * [SecureRandom.getInstanceStrong] resolves against the `securerandom.strongAlgorithms` security
 * property, and throws when that property names no algorithm any installed provider implements.
 */
private fun strongSecureRandomStatus(): CryptoServiceStatus =
    try {
        SecureRandom.getInstanceStrong().let { secureRandom ->
            CryptoServiceStatus.Resolved(
                algorithm = secureRandom.algorithm,
                providerName = secureRandom.provider.name,
                providerVersion = secureRandom.provider.versionString(),
            )
        }
    } catch (e: NoSuchAlgorithmException) {
        kaliumLogger.e("No strong SecureRandom algorithm is available on this device: $e")
        CryptoServiceStatus.Failed(e.failureReason())
    }

private fun aesKeyGeneratorStatus(): CryptoServiceStatus =
    try {
        KeyGenerator.getInstance(AES_KEY_ALGORITHM).let { keyGenerator ->
            CryptoServiceStatus.Resolved(
                algorithm = keyGenerator.algorithm,
                providerName = keyGenerator.provider.name,
                providerVersion = keyGenerator.provider.versionString(),
            )
        }
    } catch (e: NoSuchAlgorithmException) {
        kaliumLogger.e("No $AES_KEY_ALGORITHM KeyGenerator is available on this device: $e")
        CryptoServiceStatus.Failed(e.failureReason())
    }

private fun NoSuchAlgorithmException.failureReason(): String = message ?: this::class.java.simpleName

/**
 * `Provider.getVersionStr()` needs API 28 and `Provider.getVersion()` is deprecated, so read the version
 * out of the provider's own property map, where it is registered under this key.
 */
private fun Provider.versionString(): String = getProperty(PROVIDER_VERSION_PROPERTY).orEmpty()

private const val PROVIDER_VERSION_PROPERTY = "Provider.id version"
private const val SECURE_RANDOM_SERVICE = "SecureRandom"
private const val KEY_GENERATOR_SERVICE = "KeyGenerator"

/** Kept in sync with the `KEY_ALGORITHM` that [AESEncrypt.generateRandomAES256Key] generates keys with. */
private const val AES_KEY_ALGORITHM = "AES"