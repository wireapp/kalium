/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

@file:Suppress("StringTemplate")

package com.wire.kalium.cryptography

import com.benasher44.uuid.uuidFrom
import io.ktor.util.encodeBase64

typealias MLSGroupId = String

data class CryptoClientId(val value: String) {
    override fun toString() = value
}

data class PlainUserId(val value: String) {
    override fun toString() = value
}

typealias CryptoUserID = CryptoQualifiedID

data class CryptoQualifiedID(
    val value: String,
    val domain: String
) {
    override fun toString() = "$value@$domain"

    companion object {
        private const val QUALIFIED_ID_COMPONENT_COUNT = 2

        fun fromEncodedString(value: String): CryptoQualifiedID? {
            val components = value.split("@")
            if (components.size != QUALIFIED_ID_COMPONENT_COUNT) return null
            return CryptoQualifiedID(components[0], components[1])
        }
    }
}

data class CryptoQualifiedClientId(
    val value: String,
    val userId: CryptoQualifiedID
) {
    override fun toString() = "${userId.value}:${value}@${userId.domain}"

    companion object {
        private const val CLIENT_ID_COMPONENT_COUNT = 3

        fun fromEncodedString(value: String): CryptoQualifiedClientId? {
            val components = value.split(":", "@")
            if (components.size != CLIENT_ID_COMPONENT_COUNT) return null

            return CryptoQualifiedClientId(
                components[1],
                CryptoQualifiedID(components[0], components[2])
            )
        }
    }
}

data class WireIdentity(
    val clientId: String,
    val handle: String,
    val displayName: String,
    val domain: String,
    val certificate: String
)

@Suppress("MagicNumber")
data class E2EIQualifiedClientId(
    val value: String,
    val userId: CryptoQualifiedID
) {
    fun getEncodedUserID(): String{
        val sourceUUID = uuidFrom(userId.value)

        // Convert the UUID to bytes
        val uuidBytes = ByteArray(16)
        val mostSigBits = sourceUUID.mostSignificantBits
        val leastSigBits = sourceUUID.leastSignificantBits

        for (i in 0..7) {
            uuidBytes[i] = ((mostSigBits shr (56 - i * 8)) and 0xFF).toByte()
            uuidBytes[i + 8] = ((leastSigBits shr (56 - i * 8)) and 0xFF).toByte()
        }

        // Base64url encode the UUID bytes without padding
        return uuidBytes.encodeBase64().removeSuffix("==")
    }
    override fun toString(): String {
        return "${getEncodedUserID()}:${value}@${userId.domain}"
    }
}
