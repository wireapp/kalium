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
package com.wire.kalium.network.api.base.unbound.acme

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Suppress("EnforceSerializableFields")
@Serializable
data class AcmeDirectoriesResponse(
    val newNonce: String,
    val newAccount: String,
    val newOrder: String,
    val revokeCert: String,
    val keyChange: String
)

@Serializable
data class ACMEResponse(
    @SerialName("nonce") val nonce: String,
    @SerialName("location") val location: String,
    @SerialName("response") val response: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ACMEResponse

        if (nonce != other.nonce) return false
        if (location != other.location) return false
        if (!response.contentEquals(other.response)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nonce.hashCode()
        result = 31 * result + location.hashCode()
        result = 31 * result + response.contentHashCode()
        return result
    }
}

@Suppress("EnforceSerializableFields")
@Serializable
data class ChallengeResponse(
    @SerialName("type") val type: String,
    @SerialName("url") val url: String,
    @SerialName("status") val status: String,
    @SerialName("token") val token: String,
    @SerialName("target") val target: String,
    // it is ok to have this default value here since in the request we are
    // parsing the request and extracting it form the headers and if it is missing form headers
    // then and error is returned
    val nonce: String = ""
)

@Suppress("EnforceSerializableFields")
@Serializable
data class ACMEAuthorizationResponse(
    @SerialName("nonce") val nonce: String,
    @SerialName("location") val location: String?,
    @SerialName("response") val response: ByteArray,
    @SerialName("challengeType") val challengeType: DtoAuthorizationChallengeType
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ACMEAuthorizationResponse

        if (nonce != other.nonce) return false
        if (location != other.location) return false
        if (!response.contentEquals(other.response)) return false
        if (challengeType != other.challengeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nonce.hashCode()
        result = 31 * result + (location?.hashCode() ?: 0)
        result = 31 * result + response.contentHashCode()
        result = 31 * result + challengeType.hashCode()
        return result
    }
}

@Suppress("EnforceSerializableFields")
@Serializable
data class AuthorizationResponse(
    @SerialName("challenges") val challenges: List<AuthorizationChallenge>,
)

@Suppress("EnforceSerializableFields")
@Serializable
data class AuthorizationChallenge(
    @SerialName("type") val type: DtoAuthorizationChallengeType,
)

@Serializable
enum class DtoAuthorizationChallengeType {
    @SerialName("wire-dpop-01")
    DPoP,

    @SerialName("wire-oidc-01")
    OIDC
}

@JvmInline
value class CertificateChain(val value: String)
