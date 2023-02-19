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
import com.wire.kalium.cryptography.MLSClientImpl.Companion.toUByteList
import sun.jvm.hotspot.oops.CellTypeState.value

@Suppress("FunctionParameterNaming", "LongParameterList")
@OptIn(ExperimentalUnsignedTypes::class)
class E2EIClientImpl constructor(
    private val wireE2eIdentity: WireE2eIdentity
) : E2EIClient {

    override fun directoryResponse(directory: JsonRawData): AcmeDirectory {
        return toAcmeDirectory(wireE2eIdentity.directoryResponse(toUByteList(directory)))
    }

    override fun newAccountRequest(
        directory: AcmeDirectory,
        previousNonce: String
    ): JsonRawData {
        return wireE2eIdentity.newAccountRequest(
            toAcmeDirectory(directory),
            previousNonce
        ).toUByteArray().asByteArray()
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
        return wireE2eIdentity.newOrderRequest(
            displayName,
            domain,
            clientId,
            handle,
            expiryDays,
            toAcmeDirectory(directory),
            toUByteList(account),
            previousNonce
        ).toUByteArray().asByteArray()
    }

    override fun newOrderResponse(order: JsonRawData): NewAcmeOrder {
        return toNewAcmeOrder(wireE2eIdentity.newOrderResponse(toUByteList(order)))
    }

    override fun newAuthzRequest(
        url: String,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData {
        return wireE2eIdentity.newAuthzRequest(
            url,
            toUByteList(account),
            previousNonce
        ).toUByteArray().asByteArray()
    }

    override fun newAuthzResponse(authz: JsonRawData): NewAcmeAuthz {
        return toNewAcmeAuthz(wireE2eIdentity.newAuthzResponse(toUByteList(authz)))
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
        return wireE2eIdentity.createDpopToken(
            accessTokenUrl,
            userId,
            clientId,
            domain,
            toAcmeChallenge(clientIdChallenge),
            backendNonce,
            expiryDays
        )
    }

    override fun newDpopChallengeRequest(
        accessToken: String,
        dpopChallenge: AcmeChallenge,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData {
        return wireE2eIdentity.newDpopChallengeRequest(
            accessToken,
            toAcmeChallenge(dpopChallenge),
            toUByteList(account), previousNonce
        ).toUByteArray().asByteArray()
    }

    override fun newOidcChallengeRequest(
        idToken: String,
        oidcChallenge: AcmeChallenge,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData {
        return wireE2eIdentity.newOidcChallengeRequest(
            idToken, toAcmeChallenge(oidcChallenge),
            toUByteList(account),
            previousNonce
        ).toUByteArray().asByteArray()
    }

    override fun newChallengeResponse(challenge: JsonRawData) {
        wireE2eIdentity.newChallengeResponse(toUByteList(challenge))
    }

    override fun checkOrderRequest(
        orderUrl: String,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData {
        return wireE2eIdentity.checkOrderRequest(
            orderUrl,
            toUByteList(account),
            previousNonce
        ).toUByteArray().asByteArray()
    }

    override fun checkOrderResponse(order: JsonRawData): AcmeOrder {
        return wireE2eIdentity.checkOrderResponse(
            toUByteList(order)
        ).toUByteArray().asByteArray()
    }

    override fun finalizeRequest(
        order: AcmeOrder,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData {
        return wireE2eIdentity.finalizeRequest(
            toUByteList(order),
            toUByteList(account),
            previousNonce
        ).toUByteArray().asByteArray()
    }

    override fun finalizeResponse(finalize: JsonRawData): AcmeFinalize {
        return toAcmeFinalize(wireE2eIdentity.finalizeResponse(toUByteList(finalize)))
    }

    override fun certificateRequest(
        finalize: AcmeFinalize,
        account: AcmeAccount,
        previousNonce: String
    ): JsonRawData {
        return wireE2eIdentity.certificateRequest(
            toAcmeFinalize(finalize),
            toUByteList(account),
            previousNonce
        ).toUByteArray().asByteArray()
    }

    override fun certificateResponse(certificateChain: String): List<String> {
        return wireE2eIdentity.certificateResponse(certificateChain)
    }

    companion object {
        fun toAcmeDirectory(value: com.wire.crypto.AcmeDirectory) = AcmeDirectory(
            value.newNonce, value.newAccount, value.newOrder
        )

        fun toAcmeDirectory(value: AcmeDirectory) = com.wire.crypto.AcmeDirectory(
            value.newNonce, value.newAccount, value.newOrder
        )

        fun toNewAcmeOrder(value: com.wire.crypto.NewAcmeOrder) = NewAcmeOrder(
            value.delegate.toUByteArray().asByteArray(),
            value.authorizations,
        )

        fun toAcmeChallenge(value: com.wire.crypto.AcmeChallenge) = AcmeChallenge(
            value.delegate.toUByteArray().asByteArray(), value.url
        )

        fun toAcmeChallenge(value: AcmeChallenge) = com.wire.crypto.AcmeChallenge(
            toUByteList(value.delegate), value.url
        )

        fun toNewAcmeAuthz(value: com.wire.crypto.NewAcmeAuthz) = NewAcmeAuthz(
            value.identifier,
            value.wireHttpChallenge?.let { toAcmeChallenge(it) },
            value.wireOidcChallenge?.let { toAcmeChallenge(it) },
        )

        fun toAcmeFinalize(value: com.wire.crypto.AcmeFinalize) = AcmeFinalize(
            value.delegate.toUByteArray().asByteArray(), value.certificateUrl
        )

        fun toAcmeFinalize(value: AcmeFinalize) = com.wire.crypto.AcmeFinalize(
            toUByteList(value.delegate), value.certificateUrl
        )
    }
}
