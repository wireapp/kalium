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

/** Confirms the account password before the caller offers recovery-code management. */
public interface ConfirmMFAPasswordUseCase {
    public suspend operator fun invoke(password: String): Either<MFAFailure, Unit>
}

internal class ConfirmMFAPasswordUseCaseImpl(private val repository: MFARepository) : ConfirmMFAPasswordUseCase {
    override suspend fun invoke(password: String): Either<MFAFailure, Unit> = repository.update {
        if (password.isBlank()) Either.Left(MFAFailure.INVALID_PASSWORD) else Either.Right(Unit)
    }
}

/** Replaces recovery codes, returning their values once and publishing only their counts in the overview. */
public interface GenerateMFARecoveryCodesUseCase {
    public suspend operator fun invoke(): Either<MFAFailure, List<String>>
}

internal class GenerateMFARecoveryCodesUseCaseImpl(private val repository: MFARepository) : GenerateMFARecoveryCodesUseCase {
    override suspend fun invoke(): Either<MFAFailure, List<String>> = repository.update {
        val enabled = overview as? MFAOverview.Enabled ?: return@update Either.Left(MFAFailure.MFA_DISABLED)
        val codes = DummyMFAData.recoveryCodes()
        overview = enabled.copy(recoveryCodesStatus = RecoveryCodesStatus(codes.size, codes.size))
        Either.Right(codes)
    }
}
