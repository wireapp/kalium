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

@Suppress("TooManyFunctions")
class E2EIClientImpl : E2EIClient {
    override fun directoryResponse(directory: ByteArray): AcmeDirectory {
        TODO("Not yet implemented")
    }

    override fun newAccountRequest(previousNonce: String): ByteArray {
        TODO("Not yet implemented")
    }

    override fun newAccountResponse(account: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun newOrderRequest(previousNonce: String):  ByteArray {
        TODO("Not yet implemented")
    }

    override fun newOrderResponse(order: ByteArray): NewAcmeOrder {
        TODO("Not yet implemented")
    }

    override fun newAuthzRequest(url: String, previousNonce: String): ByteArray {
        TODO("Not yet implemented")
    }

    override fun newAuthzResponse(authz: ByteArray): NewAcmeAuthz {
        TODO("Not yet implemented")
    }

    override fun createDpopToken(accessTokenUrl: String,
                                 backendNonce: String): DpopToken {
        TODO("Not yet implemented")
    }

    override fun newDpopChallengeRequest(accessToken: String, previousNonce: String): ByteArray {
        TODO("Not yet implemented")
    }

    override fun newOidcChallengeRequest(
        idToken: String,
        previousNonce: String
    ): ByteArray {
        TODO("Not yet implemented")
    }

    override fun newChallengeResponse(challenge: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun checkOrderRequest(orderUrl: String, previousNonce: String): ByteArray {
        TODO("Not yet implemented")
    }

    override fun checkOrderResponse(order: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun finalizeRequest(previousNonce: String): ByteArray {
        TODO("Not yet implemented")
    }

    override fun finalizeResponse(finalize: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun certificateRequest(previousNonce: String): ByteArray {
        TODO("Not yet implemented")
    }

}
