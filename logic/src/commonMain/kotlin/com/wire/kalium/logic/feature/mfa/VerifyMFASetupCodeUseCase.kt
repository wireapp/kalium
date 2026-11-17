/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.mfa

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.mfa.MFARepository

/** Verifies an initial enrollment and returns recovery codes for temporary display by the caller. */
public interface VerifyMFASetupCodeUseCase {
    public suspend operator fun invoke(enrollmentId: String, code: String): Either<MFAFailure, List<String>>
}

internal class VerifyMFASetupCodeUseCaseImpl(private val repository: MFARepository) : VerifyMFASetupCodeUseCase {
    override suspend fun invoke(enrollmentId: String, code: String): Either<MFAFailure, List<String>> =
        repository.verifySetupCode(enrollmentId, code)
}
