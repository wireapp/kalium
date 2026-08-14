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

import com.wire.kalium.cryptography.utils.CryptoServiceInfo
import com.wire.kalium.cryptography.utils.cryptoServiceInfo

internal actual class SecureRandom actual constructor() {

    private val random get() = java.security.SecureRandom.getInstanceStrong()

    actual fun nextBytes(length: Int): ByteArray = ByteArray(length).apply {
        random.nextBytes(this)
    }

    actual fun nextInt(bound: Int): Int = random.nextInt(bound)

    actual fun serviceInfo(): CryptoServiceInfo? =
        cryptoServiceInfo("Database secret / random password", "SecureRandom.getInstanceStrong()") {
            random.run { algorithm to provider }
        }
}
