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
class E2EIClientImpl constructor(
    private val wireE2eIdentity: WireE2eIdentity
) : E2EIClient {

    override fun directoryResponse(directory: ByteArray): AcmeDirectory {
        return toAcmeDirectory(wireE2eIdentity.directoryResponse(toUByteList(directory)))
    }

    override fun newAccountRequest(
        previousNonce: String
    ): ByteArray {
        return toByteArray(wireE2eIdentity.newAccountRequest(previousNonce))
    }

    override fun newAccountResponse(
        account: ByteArray
    ) {
        wireE2eIdentity.newAccountResponse(toUByteList(account))
    }

    override fun newOrderRequest(previousNonce: String): ByteArray {
        return toByteArray(wireE2eIdentity.newOrderRequest(previousNonce))
    }

    override fun newOrderResponse(order: ByteArray): NewAcmeOrder {
        return toNewAcmeOrder(wireE2eIdentity.newOrderResponse(toUByteList(order)))
    }

    override fun newAuthzRequest(
        url: String,
        previousNonce: String
    ): ByteArray {
        return toByteArray(
            wireE2eIdentity.newAuthzRequest(
                url,
                previousNonce
            )
        )
    }

    override fun newAuthzResponse(authz: ByteArray): NewAcmeAuthz {
        return toNewAcmeAuthz(wireE2eIdentity.newAuthzResponse(toUByteList(authz)))
    }

    override fun createDpopToken(
        accessTokenUrl: String,
        backendNonce: String
    ): DpopToken {
        return wireE2eIdentity.createDpopToken(
            accessTokenUrl,
            expirySecs = 30U,
            backendNonce
        )

    }

    override fun newDpopChallengeRequest(accessToken: String, previousNonce: String): ByteArray {
        return toByteArray(
            wireE2eIdentity.newDpopChallengeRequest(
                accessToken,
                previousNonce
            )
        )
    }

    override fun newOidcChallengeRequest(
        idToken: String,
        previousNonce: String
    ): ByteArray {
        return toByteArray(
            wireE2eIdentity.newOidcChallengeRequest(
                idToken,
                previousNonce
            )
        )
    }

    override fun newChallengeResponse(challenge: ByteArray) {
        wireE2eIdentity.newChallengeResponse(toUByteList(challenge))
    }

    override fun checkOrderRequest(
        orderUrl: String,
        previousNonce: String
    ): ByteArray {
        return toByteArray(wireE2eIdentity.checkOrderRequest(
            orderUrl,
            previousNonce
        ))
    }

    override fun checkOrderResponse(order: ByteArray) {
        wireE2eIdentity.checkOrderResponse(
            toUByteList(order)
        )
    }

    override fun finalizeRequest(
        previousNonce: String
    ): ByteArray {
        return toByteArray( wireE2eIdentity.finalizeRequest(
            previousNonce
        ))
    }

    override fun finalizeResponse(finalize: ByteArray) {
        wireE2eIdentity.finalizeResponse(toUByteList(finalize))
    }

    override fun certificateRequest(
        previousNonce: String
    ): ByteArray {
        return toByteArray(wireE2eIdentity.certificateRequest(
            previousNonce
        ))
    }

    companion object {
        fun toAcmeDirectory(value: com.wire.crypto.AcmeDirectory) = AcmeDirectory(
            value.newNonce, value.newAccount, value.newOrder
        )

        fun toNewAcmeOrder(value: com.wire.crypto.NewAcmeOrder) = NewAcmeOrder(
            value.delegate.toUByteArray().asByteArray(),
            value.authorizations,
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
