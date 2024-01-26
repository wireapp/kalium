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

import com.wire.kalium.cryptography.NewAcmeAuthz
import com.wire.kalium.network.api.base.unbound.acme.ACMEAuthorizationResponse

class AcmeMapper {
    fun fromDto(dto: ACMEAuthorizationResponse, newAcmeAuthz: NewAcmeAuthz) = AcmeAuthorization(
        nonce = Nonce(dto.nonce),
        location = dto.location,
        response = dto.response,
        challengeType = fromDto(dto.challengeType),
        newAcmeAuthz
    )

    private fun fromDto(dto: com.wire.kalium.network.api.base.unbound.acme.AuthorizationChallengeType): AuthorizationChallengeType = when (dto) {
        com.wire.kalium.network.api.base.unbound.acme.AuthorizationChallengeType.DPoP -> AuthorizationChallengeType.DPoP
        com.wire.kalium.network.api.base.unbound.acme.AuthorizationChallengeType.OIDC -> AuthorizationChallengeType.OIDC
    }
}
