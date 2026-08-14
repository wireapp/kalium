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
@file:JvmName("CryptoServicesJvm")

package com.wire.kalium.cryptography.utils

import java.security.Provider

actual fun cryptoServices(): List<CryptoServiceInfo> = assetCryptoServices()

/**
 * Performs [resolve] and reads the algorithm and provider off the instance it returned, so what is reported
 * is what the platform actually handed back.
 *
 * Null when the lookup fails: a debug screen must not bring down the caller over a missing algorithm.
 *
 * @param name what the lookup is for, e.g. `Asset cipher`.
 * @param lookup the lookup performed, as written in the source. Interpolate the same constants [resolve]
 * uses, so this cannot describe a lookup the call sites do not make.
 * @param resolve the JCA lookup, returning its result's `algorithm` and `provider`.
 */
fun cryptoServiceInfo(name: String, lookup: String, resolve: () -> Pair<String, Provider>): CryptoServiceInfo? =
    runCatching(resolve).getOrNull()?.let { (algorithm, provider) ->
        CryptoServiceInfo(
            name = name,
            lookup = lookup,
            algorithm = algorithm,
            providerName = provider.name,
            providerVersion = provider.versionString(),
        )
    }

/**
 * `Provider.getVersionStr()` needs API 28 and `Provider.getVersion()` is deprecated, so read the version
 * out of the provider's own property map, where it is registered under this key.
 */
private fun Provider.versionString(): String = getProperty(PROVIDER_VERSION_PROPERTY).orEmpty()

private const val PROVIDER_VERSION_PROPERTY = "Provider.id version"
