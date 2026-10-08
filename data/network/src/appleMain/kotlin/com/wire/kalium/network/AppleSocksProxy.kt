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

package com.wire.kalium.network

import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.kCFStreamPropertySOCKSPassword
import platform.CoreFoundation.kCFStreamPropertySOCKSUser
import platform.CoreFoundation.kCFStreamPropertySOCKSVersion
import platform.CoreFoundation.kCFStreamSocketSOCKSVersion5
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSURLSessionConfiguration

/** Adds session-local authentication to the SOCKS routing installed by Ktor before this callback. */
@OptIn(ExperimentalForeignApi::class)
internal fun NSURLSessionConfiguration.configureSocksProxyAuthentication(
    credentials: ProxyCredentialsDTO?,
) {
    connectionProxyDictionary = connectionProxyDictionary.orEmpty().toMutableMap().apply {
        put(
            CFBridgingRelease(CFRetain(kCFStreamPropertySOCKSVersion)),
            CFBridgingRelease(CFRetain(kCFStreamSocketSOCKSVersion5))
        )
        if (credentials != null) {
            credentials.username?.let { put(CFBridgingRelease(CFRetain(kCFStreamPropertySOCKSUser)), it) }
            credentials.password?.let { put(CFBridgingRelease(CFRetain(kCFStreamPropertySOCKSPassword)), it) }
        }
    }
    // Do not recover another account's cached credentials when the supplied values are rejected.
    URLCredentialStorage = null
}
