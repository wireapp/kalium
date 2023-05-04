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

typealias DpopToken = String

data class AcmeDirectory(
    var newNonce: String,
    var newAccount: String,
    var newOrder: String
)

data class NewAcmeOrder(
    var delegate: ByteArray,
    var authorizations: List<String>
)

data class AcmeChallenge(
    var delegate: ByteArray,
    var url: String
)

data class NewAcmeAuthz(
    var identifier: String,
    var wireOidcChallenge: AcmeChallenge?,
    var wireDpopChallenge: AcmeChallenge?
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
    fun directoryResponse(directory: ByteArray): AcmeDirectory
    fun newAccountRequest(previousNonce: String): ByteArray
    fun newAccountResponse(account: ByteArray)

    fun newOrderRequest(previousNonce: String): ByteArray

    fun newOrderResponse(order: ByteArray): NewAcmeOrder
    fun newAuthzRequest(
        url: String,
        previousNonce: String
    ): ByteArray

    fun newAuthzResponse(authz: ByteArray): NewAcmeAuthz

    fun createDpopToken(
        accessTokenUrl: String,
        backendNonce: String
    ): DpopToken

    fun newDpopChallengeRequest(accessToken: String, previousNonce: String): ByteArray

    fun newOidcChallengeRequest(
        idToken: String,
        previousNonce: String
    ): ByteArray

    fun newChallengeResponse(challenge: ByteArray)
    fun checkOrderRequest(
        orderUrl: String,
        previousNonce: String
    ): ByteArray

    fun checkOrderResponse(order: ByteArray)
    fun finalizeRequest(
        previousNonce: String
    ): ByteArray

    fun finalizeResponse(finalize: ByteArray)
    fun certificateRequest(
        previousNonce: String
    ): ByteArray
}
