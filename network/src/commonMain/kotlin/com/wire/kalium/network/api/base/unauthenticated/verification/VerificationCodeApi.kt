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
package com.wire.kalium.network.api.base.unauthenticated.verification

import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mockable

@Mockable
interface VerificationCodeApi {

    /**
     * A backend-facing action that requires a verification code
     */
    enum class ActionToBeVerified {
        LOGIN_OR_CLIENT_REGISTRATION,
        CREATE_SCIM_TOKEN,
        DELETE_TEAM
    }

    /**
     * Sends a verification code to an email.
     * This verification might be required by some endpoints.
     * @see ActionToBeVerified
     */
    suspend fun sendVerificationCode(
        email: String,
        action: ActionToBeVerified
    ): NetworkResponse<Unit>
}
