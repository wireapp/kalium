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
 * Which security provider serves one cryptographic lookup, read off the instance the platform returned.
 *
 * @param name what the lookup is for, e.g. `Asset cipher`.
 * @param lookup the lookup performed, as written in the source, e.g. `KeyGenerator.getInstance("AES")`.
 * @param algorithm the algorithm the returned instance reports, e.g. `AES/CBC/PKCS5PADDING`.
 */
data class CryptoServiceInfo(
    val name: String,
    val lookup: String,
    val algorithm: String,
    val providerName: String,
    val providerVersion: String,
)
