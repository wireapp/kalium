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
    var url: String
)

data class NewAcmeAuthz(
    var identifier: String,
    var wireHttpChallenge: AcmeChallenge?,
    var wireOidcChallenge: AcmeChallenge?
)

data class DpopTokenRequest(
    var accessTokenUrl: String,
    var backendNonce: String,
)

data class DpopChallengeRequest(
    val accessToken: String,
    val previousNonce: String
)

data class OidcChallengeRequest(
    val idToken: String,
    val previousNonce: String
)

@Suppress("TooManyFunctions")
interface E2EIClient {
    fun directoryResponse(directory: JsonRawData): AcmeDirectory
    fun newAccountRequest(previousNonce: String): JsonRawData
    fun newAccountResponse(account: JsonRawData)

    fun newOrderRequest(previousNonce: String): JsonRawData

    fun newOrderResponse(order: JsonRawData): NewAcmeOrder
    fun newAuthzRequest(
        url: String,
        previousNonce: String
    ): JsonRawData

    fun newAuthzResponse(authz: JsonRawData): NewAcmeAuthz

    fun createDpopToken(request: DpopTokenRequest): DpopToken

    fun newDpopChallengeRequest(request: DpopChallengeRequest): JsonRawData

    fun newOidcChallengeRequest(request: OidcChallengeRequest): JsonRawData

    fun newChallengeResponse(challenge: JsonRawData)
    fun checkOrderRequest(
        orderUrl: String,
        previousNonce: String
    ): JsonRawData

    fun checkOrderResponse(order: JsonRawData)
    fun finalizeRequest(
        previousNonce: String
    ): JsonRawData

    fun finalizeResponse(finalize: JsonRawData)
    fun certificateRequest(
        previousNonce: String
    ): JsonRawData
}
