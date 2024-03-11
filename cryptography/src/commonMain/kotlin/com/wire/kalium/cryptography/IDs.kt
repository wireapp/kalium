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

@file:Suppress("StringTemplate")

package com.wire.kalium.cryptography

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
    val clientId: CryptoQualifiedClientId,
    val handle: String, // handle format is "{scheme}%40{handle}@{domain}", example: "wireapp://%40hans.wurst@elna.wire.link"
    val displayName: String,
    val domain: String,
    val certificate: String,
    val status: CryptoCertificateStatus,
    val thumbprint: String,
<<<<<<< HEAD
    val serialNumber: String
)
=======
    val serialNumber: String,
    val endTimestampSeconds: Long
) {
    val handleWithoutSchemeAtSignAndDomain: String
        get() = handle.substringAfter("://%40").removeSuffix("@$domain")
}
>>>>>>> d77079f86d (fix: handle with scheme and domain from corecrypto [WPB-7084] (#2624))

enum class CryptoCertificateStatus {
    VALID, EXPIRED, REVOKED;
}
