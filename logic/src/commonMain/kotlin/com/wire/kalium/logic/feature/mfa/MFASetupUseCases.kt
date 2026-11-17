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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

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

/** Verifies an initial enrollment and returns recovery codes for temporary display by the caller. */
public interface VerifyMFASetupCodeUseCase {
    public suspend operator fun invoke(enrollmentId: String, code: String): Either<MFAFailure, List<String>>
}

internal class VerifyMFASetupCodeUseCaseImpl(private val repository: MFARepository) : VerifyMFASetupCodeUseCase {
    override suspend fun invoke(enrollmentId: String, code: String): Either<MFAFailure, List<String>> = repository.update {
        val pending = enrollments[enrollmentId] ?: return@update Either.Left(MFAFailure.INVALID_ENROLLMENT)
        if (code != DummyMFAData.TOTP_CODE) return@update Either.Left(MFAFailure.INVALID_CODE)
        val codes = DummyMFAData.initialRecoveryCodes
        enrollments[enrollmentId] = pending.copy(isVerified = true, recoveryCodesCount = codes.size)
        Either.Right(codes)
    }
}

/** Enables MFA using a verified initial enrollment and publishes the account's new overview. */
public interface CompleteMFASetupUseCase {
    public suspend operator fun invoke(enrollmentId: String, name: String): Either<MFAFailure, Unit>
}

internal class CompleteMFASetupUseCaseImpl(private val repository: MFARepository) : CompleteMFASetupUseCase {
    override suspend fun invoke(enrollmentId: String, name: String): Either<MFAFailure, Unit> = repository.update {
        val existing = overview as? MFAOverview.Enabled
        if (existing?.authenticatorApps?.any { it.id == enrollmentId } == true) return@update Either.Right(Unit)
        if (existing != null) return@update Either.Left(MFAFailure.MFA_ALREADY_ENABLED)
        val pending = enrollments[enrollmentId]
        if (pending == null || !pending.isVerified || pending.recoveryCodesCount == 0) {
            return@update Either.Left(MFAFailure.INVALID_ENROLLMENT)
        }
        if (name.isBlank()) return@update Either.Left(MFAFailure.INVALID_NAME)
        val app = AuthenticatorApp(enrollmentId, name.trim(), Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date)
        overview = MFAOverview.Enabled(listOf(app), RecoveryCodesStatus(pending.recoveryCodesCount, pending.recoveryCodesCount))
        enrollments.remove(enrollmentId)
        Either.Right(Unit)
    }
}
