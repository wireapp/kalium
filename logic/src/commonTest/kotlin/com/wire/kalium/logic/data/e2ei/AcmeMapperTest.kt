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
package com.wire.kalium.logic.data.e2ei

import com.wire.kalium.cryptography.AcmeChallenge
import com.wire.kalium.cryptography.NewAcmeAuthz
import com.wire.kalium.network.api.base.unbound.acme.ACMEAuthorizationResponse
import com.wire.kalium.network.api.base.unbound.acme.DtoAuthorizationChallengeType
import kotlin.test.Test
import kotlin.test.assertEquals

class AcmeMapperTest {
    @Test
    fun givenACMEAuthorizationResponse_whenMappingToModel_thenShouldBeMappedCorrectly() {
        val (arrangement, mapper) = Arrangement().arrange()
        val expected = AcmeAuthorization(
            nonce = arrangement.RANDOM_NONCE,
            location = arrangement.RANDOM_URL,
            response = arrangement.RANDOM_BYTE_ARRAY,
            challengeType = AuthorizationChallengeType.DPoP,
            newAcmeAuthz = arrangement.ACME_AUTHZ
        )
        val actual = mapper.fromDto(arrangement.ACME_AUTHORIZATION_RESPONSE, arrangement.ACME_AUTHZ)
        assertEquals(expected, actual)
    }

    @Test
    fun giveDtoChallengeType_whenMappingToModel_thenShouldBeMappedCorrectly() {
        val (_, mapper) = Arrangement().arrange()
        val expected = AuthorizationChallengeType.DPoP
        val actual = mapper.fromDto(DtoAuthorizationChallengeType.DPoP)
        assertEquals(expected, actual)
    }

    private class Arrangement {
        val RANDOM_NONCE = Nonce("xxxxx")
        val RANDOM_URL = "https://random.rn"
        val RANDOM_BYTE_ARRAY = "random-value".encodeToByteArray()
        val ACME_CHALLENGE = AcmeChallenge(
            delegate = RANDOM_BYTE_ARRAY,
            url = RANDOM_URL,
            target = RANDOM_URL
        )
        val ACME_AUTHZ = NewAcmeAuthz(
            identifier = "identifier",
            keyAuth = "keyauth",
            challenge = ACME_CHALLENGE
        )
        val ACME_AUTHORIZATION_RESPONSE = ACMEAuthorizationResponse(
            nonce = RANDOM_NONCE.value,
            location = RANDOM_URL,
            response = RANDOM_BYTE_ARRAY,
            challengeType = DtoAuthorizationChallengeType.DPoP
        )

        val mapper: AcmeMapper = AcmeMapperImpl()

        fun arrange() = this to mapper
    }
}
