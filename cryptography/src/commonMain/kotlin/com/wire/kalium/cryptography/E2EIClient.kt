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

@Suppress("FunctionParameterNaming", "LongParameterList")
interface E2EIClient {
    fun directoryResponse(directory: JsonRawData): AcmeDirectory
    fun newAccountRequest(
        directory: AcmeDirectory,
        previousNonce: String
    ): JsonRawData

    fun newOrderRequest(
        displayName: String,
        domain: String,
        clientId: String,
        handle: String,
        expiryDays: UInt,
        directory: AcmeDirectory,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData

    fun newOrderResponse(order: JsonRawData): NewAcmeOrder
    fun newAuthzRequest(
        url: String,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData

    fun newAuthzResponse(authz: JsonRawData): NewAcmeAuthz
    fun createDpopToken(
        accessTokenUrl: String,
        userId: String,
        clientId: ULong,
        domain: String,
        clientIdChallenge: AcmeChallenge,
        backendNonce: String,
        expiryDays: UInt
    ): String

    fun newDpopChallengeRequest(
        accessToken: String,
        dpopChallenge: AcmeChallenge,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData

    fun newOidcChallengeRequest(
        idToken: String,
        oidcChallenge: AcmeChallenge,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData

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
