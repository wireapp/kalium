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

typealias JsonRawData = ByteArray
typealias DpopToken = String

data class AcmeDirectory(
    var newNonce: String,
    var newAccount: String,
    var newOrder: String
)

data class NewAcmeOrder(
    var delegate: JsonRawData,
    var authorizations: List<String>
)

data class AcmeChallenge(
    var delegate: JsonRawData,
    var url: String,
    var target: String
)

data class NewAcmeAuthz(
    var identifier: String,
    var keyAuth: String?,
    var challenge: AcmeChallenge
)

@Suppress("TooManyFunctions")
interface E2EIClient {
    suspend fun directoryResponse(directory: JsonRawData): AcmeDirectory
    suspend fun getNewAccountRequest(previousNonce: String): JsonRawData
    suspend fun setAccountResponse(account: JsonRawData)
    suspend fun getNewOrderRequest(previousNonce: String): JsonRawData
    suspend fun setOrderResponse(order: JsonRawData): NewAcmeOrder
    suspend fun getNewAuthzRequest(url: String, previousNonce: String): JsonRawData
    suspend fun setAuthzResponse(authz: JsonRawData): NewAcmeAuthz
    suspend fun createDpopToken(backendNonce: String): DpopToken
    suspend fun getNewDpopChallengeRequest(accessToken: String, previousNonce: String): JsonRawData
    suspend fun getNewOidcChallengeRequest(idToken: String, refreshToken: String, previousNonce: String): JsonRawData
    suspend fun setOIDCChallengeResponse(coreCrypto: CoreCryptoCentral, challenge: JsonRawData)
    suspend fun setDPoPChallengeResponse(challenge: JsonRawData)
    suspend fun checkOrderRequest(orderUrl: String, previousNonce: String): JsonRawData
    suspend fun checkOrderResponse(order: JsonRawData): String
    suspend fun finalizeRequest(previousNonce: String): JsonRawData
    suspend fun finalizeResponse(finalize: JsonRawData): String
    suspend fun certificateRequest(previousNonce: String): JsonRawData
    suspend fun getOAuthRefreshToken(): String?
}
