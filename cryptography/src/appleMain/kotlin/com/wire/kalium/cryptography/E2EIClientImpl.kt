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
    override fun directoryResponse(directory: JsonRawData): AcmeDirectory {
        TODO("Not yet implemented")
    }

    override fun getNewAccountRequest(previousNonce: String): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun setAccountResponse(account: JsonRawData) {
        TODO("Not yet implemented")
    }

    override fun getNewOrderRequest(previousNonce: String): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun setOrderResponse(order: JsonRawData): NewAcmeOrder {
        TODO("Not yet implemented")
    }

    override fun getNewAuthzRequest(url: String, previousNonce: String): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun setAuthzResponse(authz: JsonRawData): NewAcmeAuthz {
        TODO("Not yet implemented")
    }

    override fun createDpopToken(backendNonce: String): DpopToken {
        TODO("Not yet implemented")
    }

    override fun getNewDpopChallengeRequest(accessToken: String, previousNonce: String): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun getNewOidcChallengeRequest(idToken: String, previousNonce: String): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun setChallengeResponse(challenge: JsonRawData) {
        TODO("Not yet implemented")
    }

    override fun checkOrderRequest(orderUrl: String, previousNonce: String): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun checkOrderResponse(order: JsonRawData): String {
        TODO("Not yet implemented")
    }

    override fun finalizeRequest(previousNonce: String): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun finalizeResponse(finalize: JsonRawData): String {
        TODO("Not yet implemented")
    }

    override fun certificateRequest(previousNonce: String): JsonRawData {
        TODO("Not yet implemented")
    }

}
