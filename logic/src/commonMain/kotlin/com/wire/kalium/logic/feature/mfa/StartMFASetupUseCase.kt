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
import com.wire.kalium.logic.data.mfa.DummyMFAData
import com.wire.kalium.logic.data.mfa.MFARepository
import com.wire.kalium.logic.data.mfa.PendingMFAEnrollment

/** Confirms the password and starts an enrollment for initial setup or an additional authenticator. */
public interface StartMFASetupUseCase {
    public suspend operator fun invoke(password: String): Either<MFAFailure, MFAEnrollment>
}

internal class StartMFASetupUseCaseImpl(private val repository: MFARepository) : StartMFASetupUseCase {
    override suspend fun invoke(password: String): Either<MFAFailure, MFAEnrollment> = repository.update {
        if (password.isBlank()) return@update Either.Left(MFAFailure.INVALID_PASSWORD)
        val enrollment = DummyMFAData.enrollment()
        enrollments[enrollment.id] = PendingMFAEnrollment()
        Either.Right(enrollment)
    }
}
