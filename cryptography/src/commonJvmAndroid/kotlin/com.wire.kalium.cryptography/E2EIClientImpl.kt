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

import com.wire.crypto.E2eiEnrollment

@Suppress("TooManyFunctions")
@OptIn(ExperimentalUnsignedTypes::class)
class E2EIClientImpl(
    val wireE2eIdentity: E2eiEnrollment
) : E2EIClient {

    private val defaultDPoPTokenExpiry: UInt = 30U

    override suspend fun directoryResponse(directory: JsonRawData) =
        toAcmeDirectory(wireE2eIdentity.directoryResponse(directory))

    override suspend fun getNewAccountRequest(previousNonce: String) =
        wireE2eIdentity.newAccountRequest(previousNonce)

    override suspend fun setAccountResponse(account: JsonRawData) =
        wireE2eIdentity.newAccountResponse(account)

    override suspend fun getNewOrderRequest(previousNonce: String) =
        wireE2eIdentity.newOrderRequest(previousNonce)

    override suspend fun setOrderResponse(order: JsonRawData) =
        toNewAcmeOrder(wireE2eIdentity.newOrderResponse(order))

    override suspend fun getNewAuthzRequest(url: String, previousNonce: String) =
        wireE2eIdentity.newAuthzRequest(url, previousNonce)

    override suspend fun setAuthzResponse(authz: JsonRawData) =
        toNewAcmeAuthz(wireE2eIdentity.newAuthzResponse(authz))

    override suspend fun createDpopToken(backendNonce: String) =
        wireE2eIdentity.createDpopToken(expirySecs = defaultDPoPTokenExpiry, backendNonce)

    override suspend fun getNewDpopChallengeRequest(accessToken: String, previousNonce: String) =
        wireE2eIdentity.newDpopChallengeRequest(accessToken, previousNonce)

    override suspend fun getNewOidcChallengeRequest(idToken: String, refreshToken: String, previousNonce: String) =
        wireE2eIdentity.newOidcChallengeRequest(idToken, refreshToken, previousNonce)

    override suspend fun setOIDCChallengeResponse(coreCrypto: CoreCryptoCentral, challenge: JsonRawData) =
        wireE2eIdentity.newOidcChallengeResponse((coreCrypto as CoreCryptoCentralImpl).getCoreCrypto(), challenge)

    override suspend fun setDPoPChallengeResponse(challenge: JsonRawData) {
        wireE2eIdentity.newDpopChallengeResponse(challenge)
    }

    override suspend fun checkOrderRequest(orderUrl: String, previousNonce: String) =
        wireE2eIdentity.checkOrderRequest(orderUrl, previousNonce)

    override suspend fun checkOrderResponse(order: JsonRawData) =
        wireE2eIdentity.checkOrderResponse(order)

    override suspend fun finalizeRequest(previousNonce: String) =
        wireE2eIdentity.finalizeRequest(previousNonce)

    override suspend fun finalizeResponse(finalize: JsonRawData) =
        wireE2eIdentity.finalizeResponse(finalize)

    override suspend fun certificateRequest(previousNonce: String) =
        wireE2eIdentity.certificateRequest(previousNonce)

    @Suppress("TooGenericExceptionCaught")
    override suspend fun getOAuthRefreshToken() = try {
        wireE2eIdentity.getRefreshToken()
    } catch (e: Exception) {
        null
    }

    companion object {
        fun toAcmeDirectory(value: com.wire.crypto.AcmeDirectory) = AcmeDirectory(
            value.newNonce, value.newAccount, value.newOrder
        )

        fun toNewAcmeOrder(value: com.wire.crypto.NewAcmeOrder) = NewAcmeOrder(
            value.delegate.toUByteArray().asByteArray(),
            value.authorizations
        )

        private fun toAcmeChallenge(value: com.wire.crypto.AcmeChallenge) = AcmeChallenge(
            value.delegate.toUByteArray().asByteArray(),
            value.url,
            value.target
        )

        fun toNewAcmeAuthz(value: com.wire.crypto.NewAcmeAuthz) = NewAcmeAuthz(
            value.identifier,
            keyAuth = value.keyauth,
            toAcmeChallenge(value.challenge)
        )
    }
}
