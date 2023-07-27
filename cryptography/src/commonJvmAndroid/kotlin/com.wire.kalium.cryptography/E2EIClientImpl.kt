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

import com.wire.crypto.WireE2eIdentity
import com.wire.kalium.cryptography.MLSClientImpl.Companion.toByteArray
import com.wire.kalium.cryptography.MLSClientImpl.Companion.toUByteList

@Suppress("TooManyFunctions")
@OptIn(ExperimentalUnsignedTypes::class)
class E2EIClientImpl(
    val wireE2eIdentity: WireE2eIdentity
) : E2EIClient {

    private val defaultDPoPTokenExpiry: UInt = 30U

    override suspend fun directoryResponse(directory: JsonRawData) =
        toAcmeDirectory(wireE2eIdentity.directoryResponse(toUByteList(directory)))

    override suspend fun getNewAccountRequest(previousNonce: String) =
        toByteArray(wireE2eIdentity.newAccountRequest(previousNonce))

    override suspend fun setAccountResponse(account: JsonRawData) =
        wireE2eIdentity.newAccountResponse(toUByteList(account))

    override suspend fun getNewOrderRequest(previousNonce: String) =
        toByteArray(wireE2eIdentity.newOrderRequest(previousNonce))

    override suspend fun setOrderResponse(order: JsonRawData) =
        toNewAcmeOrder(wireE2eIdentity.newOrderResponse(toUByteList(order)))

    override suspend fun getNewAuthzRequest(url: String, previousNonce: String) =
        toByteArray(wireE2eIdentity.newAuthzRequest(url, previousNonce))

    override suspend fun setAuthzResponse(authz: JsonRawData) =
        toNewAcmeAuthz(wireE2eIdentity.newAuthzResponse(toUByteList(authz)))

    override suspend fun createDpopToken(backendNonce: String) =
        wireE2eIdentity.createDpopToken(expirySecs = defaultDPoPTokenExpiry, backendNonce)

    override suspend fun getNewDpopChallengeRequest(accessToken: String, previousNonce: String) =
        toByteArray(wireE2eIdentity.newDpopChallengeRequest(accessToken, previousNonce))

    override suspend fun getNewOidcChallengeRequest(idToken: String, previousNonce: String) =
        toByteArray(wireE2eIdentity.newOidcChallengeRequest(idToken, previousNonce))

    override suspend fun setChallengeResponse(challenge: JsonRawData) =
        wireE2eIdentity.newChallengeResponse(toUByteList(challenge))

    override suspend fun checkOrderRequest(orderUrl: String, previousNonce: String) =
        toByteArray(wireE2eIdentity.checkOrderRequest(orderUrl, previousNonce))

    override suspend fun checkOrderResponse(order: JsonRawData) =
        wireE2eIdentity.checkOrderResponse(toUByteList(order))

    override suspend fun finalizeRequest(previousNonce: String) =
        toByteArray(wireE2eIdentity.finalizeRequest(previousNonce))

    override suspend fun finalizeResponse(finalize: JsonRawData) =
        wireE2eIdentity.finalizeResponse(toUByteList(finalize))

    override suspend fun certificateRequest(previousNonce: String) =
        toByteArray(wireE2eIdentity.certificateRequest(previousNonce))

    companion object {
        fun toAcmeDirectory(value: com.wire.crypto.AcmeDirectory) = AcmeDirectory(
            value.newNonce, value.newAccount, value.newOrder
        )

        fun toNewAcmeOrder(value: com.wire.crypto.NewAcmeOrder) = NewAcmeOrder(
            value.delegate.toUByteArray().asByteArray(),
            value.authorizations
        )

        private fun toAcmeChallenge(value: com.wire.crypto.AcmeChallenge) = AcmeChallenge(
            value.delegate.toUByteArray().asByteArray(), value.url
        )

        fun toNewAcmeAuthz(value: com.wire.crypto.NewAcmeAuthz) = NewAcmeAuthz(
            value.identifier,
            value.wireOidcChallenge?.let { toAcmeChallenge(it) },
            value.wireDpopChallenge?.let { toAcmeChallenge(it) },
        )
    }
}
