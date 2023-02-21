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
typealias AcmeAccount = ByteArray
typealias AcmeOrder = ByteArray

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

data class AcmeFinalize(
    var delegate: JsonRawData,
    var certificateUrl: String
)

data class AcmeOrderRequest(
    var displayName: String,
    var domain: String,
    var clientId: String,
    var handle: String,
    var expiryDays: UInt,
    var directory: AcmeDirectory,
    var account: AcmeAccount,
    var previousNonce: String
)

data class DpopTokenRequest(
    var accessTokenUrl: String,
    var userId: String,
    var clientId: ULong,
    var domain: String,
    var clientIdChallenge: AcmeChallenge,
    var backendNonce: String,
    var expiryDays: UInt
)

data class DpopChallengeRequest(
    val accessToken: String,
    val dpopChallenge: AcmeChallenge,
    val account: AcmeAccount,
    val previousNonce: String
)

data class OidcChallengeRequest(
    val idToken: String,
    val oidcChallenge: AcmeChallenge,
    val account: AcmeAccount,
    val previousNonce: String
)

@Suppress("TooManyFunctions")
interface E2EIClient {
    fun directoryResponse(directory: JsonRawData): AcmeDirectory
    fun newAccountRequest(
        directory: AcmeDirectory,
        previousNonce: String
    ): JsonRawData

    fun newOrderRequest(order: AcmeOrderRequest): JsonRawData

    fun newOrderResponse(order: JsonRawData): NewAcmeOrder
    fun newAuthzRequest(
        url: String,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData

    fun newAuthzResponse(authz: JsonRawData): NewAcmeAuthz

    fun createDpopToken(request: DpopTokenRequest): String

    fun newDpopChallengeRequest(request: DpopChallengeRequest): JsonRawData

    fun newOidcChallengeRequest(request: OidcChallengeRequest): JsonRawData

    fun newChallengeResponse(challenge: JsonRawData)
    fun checkOrderRequest(
        orderUrl: String,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData

    fun checkOrderResponse(order: JsonRawData): AcmeOrder
    fun finalizeRequest(
        order: AcmeOrder,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData

    fun finalizeResponse(finalize: JsonRawData): AcmeFinalize
    fun certificateRequest(
        finalize: AcmeFinalize,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData

    fun certificateResponse(certificateChain: String): List<String>
}
