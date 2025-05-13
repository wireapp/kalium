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

package com.wire.kalium.cryptography

data class MlsDBSecret(
    @Deprecated("Use passphrase after migration") val value: String,
    val passphrase: ByteArray,
    val hasMigrated: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MlsDBSecret

        if (hasMigrated != other.hasMigrated) return false
        if (value != other.value) return false
        if (!passphrase.contentEquals(other.passphrase)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hasMigrated.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + passphrase.contentHashCode()
        return result
    }
}
