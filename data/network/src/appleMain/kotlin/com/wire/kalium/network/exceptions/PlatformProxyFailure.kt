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

package com.wire.kalium.network.exceptions

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSPOSIXErrorDomain
import platform.posix.EAUTH

/** CFNetwork reports a rejected SOCKS5 username/password as POSIX EAUTH, not as an HTTP 401. */
actual fun Throwable.isPlatformProxyFailure(): Boolean {
    val error = (this as? DarwinHttpRequestException)?.origin ?: return false
    return error.domain == NSPOSIXErrorDomain && error.code == EAUTH.toLong()
}
