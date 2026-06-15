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

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.AF_UNSPEC
import platform.posix.NI_MAXHOST
import platform.posix.NI_NUMERICHOST
import platform.posix.addrinfo
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.getnameinfo
import platform.posix.memset

internal actual suspend fun resolvePreviewHostAddresses(host: String): List<String> = memScoped {
    val result = alloc<CPointerVar<addrinfo>>()
    val hints = alloc<addrinfo>()
    memset(hints.ptr, 0, sizeOf<addrinfo>().toULong())
    hints.ai_family = AF_UNSPEC

    if (getaddrinfo(host, null, hints.ptr, result.ptr) != 0) {
        return@memScoped emptyList()
    }

    val firstResult: CPointer<addrinfo> = result.value ?: return@memScoped emptyList()
    val addresses = mutableSetOf<String>()
    try {
        var current: CPointer<addrinfo>? = firstResult
        while (current != null) {
            val currentValue = current.pointed
            val hostBuffer = allocArray<ByteVar>(NI_MAXHOST)
            if (getnameinfo(
                    currentValue.ai_addr,
                    currentValue.ai_addrlen,
                    hostBuffer,
                    NI_MAXHOST.toUInt(),
                    null,
                    0u,
                    NI_NUMERICHOST
                ) == 0
            ) {
                addresses += hostBuffer.toKString().substringBefore('%')
            }
            current = currentValue.ai_next
        }
    } finally {
        freeaddrinfo(firstResult)
    }

    addresses.toList()
}
