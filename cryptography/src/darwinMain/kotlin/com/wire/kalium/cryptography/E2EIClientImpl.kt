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

@Suppress("FunctionParameterNaming", "LongParameterList")
class E2EIClientImpl : E2EIClient {
    override fun directoryResponse(directory: JsonRawData): AcmeDirectory {
        TODO("Not yet implemented")
    }

    override fun newAccountRequest(
        directory: AcmeDirectory,
        previousNonce: String
    ): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun newOrderRequest(
        displayName: String,
        domain: String,
        clientId: String,
        handle: String,
        expiryDays: UInt,
        directory: AcmeDirectory,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun newOrderResponse(order: JsonRawData): NewAcmeOrder {
        TODO("Not yet implemented")
    }

    override fun newAuthzRequest(
        url: String, account: AcmeAccount,
        previousNonce: String
    ): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun newAuthzResponse(authz: JsonRawData): NewAcmeAuthz {
        TODO("Not yet implemented")
    }

    override fun createDpopToken(
        accessTokenUrl: String,
        userId: String,
        clientId: ULong,
        domain: String,
        clientIdChallenge: AcmeChallenge,
        backendNonce: String,
        expiryDays: UInt
    ): String {
        TODO("Not yet implemented")
    }

    override fun newDpopChallengeRequest(
        accessToken: String,
        dpopChallenge: AcmeChallenge,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun newOidcChallengeRequest(
        idToken: String,
        oidcChallenge: AcmeChallenge,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun newChallengeResponse(challenge: JsonRawData) {
        TODO("Not yet implemented")
    }

    override fun checkOrderRequest(
        orderUrl: String,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun checkOrderResponse(order: JsonRawData): AcmeOrder {
        TODO("Not yet implemented")
    }

    override fun finalizeRequest(
        order: AcmeOrder, account: AcmeAccount,
        previousNonce: String
    ): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun finalizeResponse(finalize: JsonRawData): AcmeFinalize {
        TODO("Not yet implemented")
    }

    override fun certificateRequest(
        finalize: AcmeFinalize,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData {
        TODO("Not yet implemented")
    }

    override fun certificateResponse(certificateChain: String): List<String> {
        TODO("Not yet implemented")
    }
}
