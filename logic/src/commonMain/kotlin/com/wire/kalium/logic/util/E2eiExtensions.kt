/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

/**
 * This extension function is used to format the serial number of the certificate.
 * output will be in the format of 2 bytes separated by a colon.
 * e.g. 01:02:03:04:05:06:07:08:09:0A:0B:0C:0D:0E:0F:10
 */
fun String.serialNumber() = this.chunked(CHUNK_SIZE)
    .joinToString(SEPARATOR)
    .uppercase()

private const val CHUNK_SIZE = 2
private const val SEPARATOR = ":"
