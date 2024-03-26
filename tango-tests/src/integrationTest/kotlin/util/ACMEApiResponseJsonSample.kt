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

import com.wire.kalium.network.api.base.unbound.acme.AcmeDirectoriesResponse
import com.wire.kalium.network.api.base.unbound.acme.ChallengeResponse
import util.ValidJsonProvider

object ACMEApiResponseJsonSample {

    const val ACME_BASE_URL = "https://balderdash.hogwash.work:9000"


    val ACME_DIRECTORIES_SAMPLE = AcmeDirectoriesResponse(
        newNonce = "$ACME_BASE_URL/acme/wire/new-nonce",
        newAccount = "$ACME_BASE_URL/acme/wire/new-account",
        newOrder = "$ACME_BASE_URL/acme/wire/new-order",
        revokeCert = "$ACME_BASE_URL/acme/wire/revoke-cert",
        keyChange = "$ACME_BASE_URL/acme/wire/key-change"
    )

    private val jsonProviderAcmeDirectories = { serializable: AcmeDirectoriesResponse ->
        """
        |{
        |  "newNonce": "${serializable.newNonce}",
        |  "newAccount": "${serializable.newAccount}",
        |  "newOrder": "${serializable.newOrder}"
        |  "revokeCert": "${serializable.revokeCert}"
        |  "keyChange": "${serializable.keyChange}"
        |}
        """.trimMargin()
    }

    val validAcmeDirectoriesResponse = ValidJsonProvider(
        ACME_DIRECTORIES_SAMPLE,
        jsonProviderAcmeDirectories
    )

    private val jsonProviderAcmeResponse = { serializable: AcmeDirectoriesResponse ->
        """
        |{
        |  "sampleBody": "sample",
        |}
        """.trimMargin()
    }

    val ACME_RESPONSE_SAMPLE = ValidJsonProvider(
        ACME_DIRECTORIES_SAMPLE,
        jsonProviderAcmeResponse
    )

    val ACME_CHALLENGE_RESPONSE_SAMPLE = ChallengeResponse(
        type = "wire-dpop-01",
        url = "https://example.com/acme/chall/prV_B7yEyA4",
        status = "valid",
        token = "LoqXcYV8q5ONbJQxbmR7SCTNo3tiAXDfowyjxAjEuX0",
        nonce = "random-nonce",
        target = "random-target"
    )

    private val jsonProviderAcmeChallenge = { serializable: ChallengeResponse ->
        """
        |{
        |  "type": "${serializable.type}",
        |  "url": "${serializable.url}",
        |  "status": "${serializable.status}",
        |  "token": "${serializable.token}"
        |  "nonce": "${serializable.nonce}"
        |}
        """.trimMargin()
    }

    val jsonProviderAcmeChallengeResponse = ValidJsonProvider(
        ACME_CHALLENGE_RESPONSE_SAMPLE,
        jsonProviderAcmeChallenge
    )


}
