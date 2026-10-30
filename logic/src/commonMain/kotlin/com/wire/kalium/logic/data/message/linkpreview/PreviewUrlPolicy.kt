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
package com.wire.kalium.logic.data.message.linkpreview

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.fullPath

/** Rejects unsafe URL forms before DNS or a connection, including literals that bypass DNS hooks. */
@Suppress("ReturnCount")
internal fun isAllowedPreviewUrl(url: Url): Boolean {
    if (url.protocol != URLProtocol.HTTPS || url.user != null || url.password != null) return false
    // The native transport writes an origin-form request target. Raw whitespace/control bytes
    // must never become request separators; percent-encoded bytes stay encoded and are safe.
    if (url.host.any(::isUnsafePreviewRequestCharacter) || url.fullPath.any(::isUnsafePreviewRequestCharacter)) return false

    val host = url.host.removeSurrounding("[", "]").trimEnd('.').lowercase()
    if (isLocalPreviewHost(host)) return false

    val isNumericHost = ':' in host || host.all { it in '0'..'9' || it == '.' }
    return !isNumericHost || isPublicPreviewAddress(host)
}

private fun isUnsafePreviewRequestCharacter(character: Char): Boolean = character <= ' ' || character == '\u007f'

private fun isLocalPreviewHost(host: String): Boolean {
    if (host.isEmpty() || host == "localhost") return true
    return host.endsWith(".localhost") || host.endsWith(".local")
}
