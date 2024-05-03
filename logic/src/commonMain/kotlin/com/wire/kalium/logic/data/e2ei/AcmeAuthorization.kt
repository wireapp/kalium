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
import kotlin.jvm.JvmInline

data class AcmeAuthorization(
    val nonce: Nonce,
    val location: String?,
    val response: ByteArray,
    val challengeType: AuthorizationChallengeType,
    val newAcmeAuthz: NewAcmeAuthz
)

@JvmInline
value class Nonce(val value: String)

data class AuthorizationResult(
    val oidcAuthorization: NewAcmeAuthz,
    val dpopAuthorization: NewAcmeAuthz,
    val nonce: Nonce
)

enum class AuthorizationChallengeType {
    /**
     * Data Protection on Demand
     */
    DPoP,
    /**
     * OpenID Connect
     */
    OIDC
}
