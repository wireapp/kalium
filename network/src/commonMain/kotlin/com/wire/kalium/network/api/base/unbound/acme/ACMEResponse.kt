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

@Suppress("EnforceSerializableFields")
@Serializable
data class ACMEResponse(
    val nonce: String,
    val location: String,
    val response: ByteArray
)

@Suppress("EnforceSerializableFields")
@Serializable
data class ChallengeResponse(
    val type: String,
    val url: String,
    val status: String,
    val token: String,
    val target: String,
    val nonce: String = ""
)

@Suppress("EnforceSerializableFields")
@Serializable
data class ACMEAuthorizationResponse(
    val nonce: String,
    val location: String?,
    val response: ByteArray,
    val challengeType: DtoAuthorizationChallengeType
)

@Suppress("EnforceSerializableFields")
@Serializable
data class AuthorizationResponse(
    val challenges: List<AuthorizationChallenge>,
)

@Suppress("EnforceSerializableFields")
@Serializable
data class AuthorizationChallenge(
    val type: DtoAuthorizationChallengeType,
)

enum class DtoAuthorizationChallengeType {
    @SerialName("wire-dpop-01")
    DPoP,

    @SerialName("wire-oidc-01")
    OIDC
}

@JvmInline
value class CertificateChain(val value: String)
