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
@file:JvmName("CryptoServiceRecordingJvm")

package com.wire.kalium.cryptography.utils

import java.security.Provider

/**
 * Notes that [usage] was served by [provider], for the security providers debug screen.
 *
 * Call this straight after a JCA lookup, passing the instance's own algorithm and provider so that what is
 * reported is what the platform actually handed back.
 *
 * @param lookup the lookup performed, as written in the source, e.g. `KeyGenerator.getInstance("AES")`.
 */
fun recordCryptoService(usage: CryptoUsage, lookup: String, algorithm: String, provider: Provider) {
    CryptoServiceRegistry.record(
        usage = usage,
        record = CryptoServiceRecord(
            lookup = lookup,
            algorithm = algorithm,
            providerName = provider.name,
            providerVersion = provider.versionString(),
        ),
    )
}

/**
 * `Provider.getVersionStr()` needs API 28 and `Provider.getVersion()` is deprecated, so read the version
 * out of the provider's own property map, where it is registered under this key.
 */
internal fun Provider.versionString(): String = getProperty(PROVIDER_VERSION_PROPERTY).orEmpty()

private const val PROVIDER_VERSION_PROPERTY = "Provider.id version"
