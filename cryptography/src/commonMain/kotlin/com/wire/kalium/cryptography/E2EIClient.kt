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
    var wireOidcChallenge: AcmeChallenge?,
    var wireDpopChallenge: AcmeChallenge?
)

@Suppress("TooManyFunctions")
interface E2EIClient {
    fun directoryResponse(directory: JsonRawData): AcmeDirectory
    fun getNewAccountRequest(previousNonce: String): JsonRawData
    fun setAccountResponse(account: JsonRawData)
    fun getNewOrderRequest(previousNonce: String): JsonRawData
    fun setOrderResponse(order: JsonRawData): NewAcmeOrder
    fun getNewAuthzRequest(url: String, previousNonce: String): JsonRawData
    fun setAuthzResponse(authz: JsonRawData): NewAcmeAuthz
    fun createDpopToken(backendNonce: String): DpopToken
    fun getNewDpopChallengeRequest(accessToken: String, previousNonce: String): JsonRawData
    fun getNewOidcChallengeRequest(idToken: String, previousNonce: String): JsonRawData
    fun setChallengeResponse(challenge: JsonRawData)
    fun checkOrderRequest(orderUrl: String, previousNonce: String): JsonRawData
    fun checkOrderResponse(order: JsonRawData): String
    fun finalizeRequest(previousNonce: String): JsonRawData
    fun finalizeResponse(finalize: JsonRawData): String
    fun certificateRequest(previousNonce: String): JsonRawData
}
