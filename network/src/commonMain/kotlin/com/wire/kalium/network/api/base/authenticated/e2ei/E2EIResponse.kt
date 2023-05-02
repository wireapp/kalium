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
package com.wire.kalium.network.api.base.authenticated.e2ei

import io.ktor.utils.io.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class AcmeDirectoriesResponse(
    val newNonce: String,
    val newAccount: String,
    val newOrder: String,
    val revokeCert: String,
    val keyChange: String
)

@Serializable
data class AuthzDirectories @OptIn(ExperimentalSerializationApi::class) constructor(
    val issuer: String,
    @JsonNames("authorization_endpoint")
    val authorizationEndpoint: String,
    @JsonNames("token_endpoint")
    val tokenEndpoint: String,
    @JsonNames("jwks_uri")
    val jwksUri: String,
    @JsonNames("userinfo_endpoint")
    val userinfoEndpoint: String,
    @JsonNames("device_authorization_endpoint")
    val deviceAuthorizationEndpoint: String,
)


@Serializable
data class AcmeResponse(
    val nonce: String,
    val response: ByteArray
)

data class AccessTokenResponse(
    val expires_in:String,
    val token:String,
    val type:String
)
