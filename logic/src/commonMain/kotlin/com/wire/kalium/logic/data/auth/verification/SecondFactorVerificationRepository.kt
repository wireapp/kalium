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
package com.wire.kalium.logic.data.auth.verification

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.unauthenticated.VerificationCodeApi

interface SecondFactorVerificationRepository {

    suspend fun requestVerificationCode(
        email: String,
        verifiableAction: VerifiableAction,
    ): Either<NetworkFailure, Unit>

}

internal class SecondFactorVerificationRepositoryImpl(
    private val verificationCodeApi: VerificationCodeApi
) : SecondFactorVerificationRepository {

    override suspend fun requestVerificationCode(
        email: String,
        verifiableAction: VerifiableAction,
    ): Either<NetworkFailure, Unit> = wrapApiRequest {
        val action = when (verifiableAction) {
            VerifiableAction.LOGIN_OR_CLIENT_REGISTRATION -> VerificationCodeApi.ActionToBeVerified.LOGIN_OR_CLIENT_REGISTRATION
            VerifiableAction.CREATE_SCIM_TOKEN -> VerificationCodeApi.ActionToBeVerified.CREATE_SCIM_TOKEN
            VerifiableAction.DELETE_TEAM -> VerificationCodeApi.ActionToBeVerified.DELETE_TEAM
        }
        verificationCodeApi.sendVerificationCode(email, action)
    }

}
