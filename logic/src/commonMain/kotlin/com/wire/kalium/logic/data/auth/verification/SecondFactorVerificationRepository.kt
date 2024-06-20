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
package com.wire.kalium.logic.data.auth.verification

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.unauthenticated.verification.VerificationCodeApi

interface SecondFactorVerificationRepository {

    suspend fun requestVerificationCode(
        email: String,
        verifiableAction: VerifiableAction,
    ): Either<NetworkFailure, Unit>

    /**
     * Stores the verification code in the local storage for the given email address.
     */
    fun storeVerificationCode(
        email: String,
        verificationCode: String
    )

    /**
     * @return the stored verification code for the given email address, or null if there is no stored code.
     * @see storeVerificationCode
     */
    fun getStoredVerificationCode(
        email: String
    ): String?

    /**
     * Clears the stored verification code for the given email address.
     * @see storeVerificationCode
     * @see getStoredVerificationCode
     */
    suspend fun clearStoredVerificationCode(
        email: String
    )
}

private typealias Email = String

internal class SecondFactorVerificationRepositoryImpl(
    private val verificationCodeApi: VerificationCodeApi
) : SecondFactorVerificationRepository {

    private val verificationCodeStorage = ConcurrentMutableMap<Email, String>()

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

    override fun storeVerificationCode(email: String, verificationCode: String) {
        verificationCodeStorage[email.lowercase()] = verificationCode
    }

    override fun getStoredVerificationCode(email: String): String? {
        return verificationCodeStorage[email.lowercase()]
    }

    override suspend fun clearStoredVerificationCode(email: String) {
        verificationCodeStorage.remove(email.lowercase())
    }
}
