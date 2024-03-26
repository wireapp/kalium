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

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.decodeURLPart
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom

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
    val handle: Handle,
    val displayName: String,
    val domain: String,
    val certificate: String,
    val status: CryptoCertificateStatus,
    val thumbprint: String,
    val serialNumber: String,
    val endTimestampSeconds: Long
) {
    constructor(
        clientId: CryptoQualifiedClientId,
        handle: String,
        displayName: String,
        domain: String,
        certificate: String,
        status: CryptoCertificateStatus,
        thumbprint: String,
        serialNumber: String,
        endTimestampSeconds: Long
    ) : this(
        clientId = clientId,
        handle = Handle.fromString(handle, domain),
        displayName = displayName,
        domain = domain,
        certificate = certificate,
        status = status,
        thumbprint = thumbprint,
        serialNumber = serialNumber,
        endTimestampSeconds = endTimestampSeconds
    )

    // WireIdentity handle format is "{scheme}%40{username}@{domain}"
    // Example: wireapp://%40hans.wurst@elna.wire.link
    data class Handle(val scheme: String, val handle: String, val domain: String) {
        companion object {
            fun fromString(rawValue: String, domain: String): Handle = URLBuilder(
                protocol = URLProtocol("", 0), // need to overwrite the protocol, otherwise it will use default HTTP
            ).takeFrom(rawValue).let {
                val handleWithOptionalAtSignAndDomain = when {
                    it.user != null && it.user!!.isNotBlank() -> it.user!!
                    it.encodedPath.isNotBlank() -> it.encodedPath.decodeURLPart()
                    else -> it.host.decodeURLPart()
                }
                Handle(
                    scheme = it.protocol.name,
                    handle = handleWithOptionalAtSignAndDomain.removeSuffix(domain).trim('@'),
                    domain = domain
                )
            }
        }
    }
}

enum class CryptoCertificateStatus {
    VALID, EXPIRED, REVOKED;
}
