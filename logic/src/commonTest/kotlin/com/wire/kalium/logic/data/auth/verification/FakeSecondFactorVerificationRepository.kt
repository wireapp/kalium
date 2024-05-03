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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either

class FakeSecondFactorVerificationRepository(
    private val requestVerificationCodeResponse: Either<NetworkFailure, Unit> = Either.Right(Unit)
) : SecondFactorVerificationRepository {

    private val verificationCodes: MutableMap<String, String> = mutableMapOf()
    override suspend fun requestVerificationCode(email: String, verifiableAction: VerifiableAction): Either<NetworkFailure, Unit> =
        requestVerificationCodeResponse

    override fun storeVerificationCode(email: String, verificationCode: String) {
        verificationCodes[email] = verificationCode
    }

    override fun getStoredVerificationCode(email: String): String? = verificationCodes[email]

    override suspend fun clearStoredVerificationCode(email: String) {
        verificationCodes.remove(email)
    }
}
