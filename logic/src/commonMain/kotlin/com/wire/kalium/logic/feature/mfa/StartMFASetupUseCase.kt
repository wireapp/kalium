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

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.mfa.MFAEnrollment
import com.wire.kalium.logic.data.mfa.MFAFailure
import com.wire.kalium.logic.data.mfa.MFARepository

/**
 * Confirms the password and starts an enrollment for initial setup or an additional authenticator.
 *
 * **Warning:** Currently backed by a temporary dummy implementation.
 */
public interface StartMFASetupUseCase {
    public suspend operator fun invoke(password: String): StartMFASetupResult
}

internal class StartMFASetupUseCaseImpl(private val repository: MFARepository) : StartMFASetupUseCase {
    override suspend fun invoke(password: String): StartMFASetupResult = repository.startSetup(password).fold(
        { StartMFASetupResult.Failure(it) },
        { StartMFASetupResult.Success(it) },
    )
}

/** Outcome of starting an MFA enrollment. */
public sealed interface StartMFASetupResult {
    /** Enrollment details for configuring an authenticator. */
    public data class Success(public val enrollment: MFAEnrollment) : StartMFASetupResult

    /** The reason enrollment could not be started. */
    public data class Failure(public val failure: MFAFailure) : StartMFASetupResult
}
