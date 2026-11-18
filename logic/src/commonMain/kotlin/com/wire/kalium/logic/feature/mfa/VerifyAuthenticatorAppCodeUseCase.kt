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
import com.wire.kalium.logic.data.mfa.MFARepository

/**
 * Verifies an additional authenticator's enrollment without generating recovery codes.
 *
 * **Warning:** Currently backed by a temporary dummy implementation.
 */
public interface VerifyAuthenticatorAppCodeUseCase {
    public suspend operator fun invoke(enrollmentId: String, code: String): VerifyAuthenticatorAppCodeResult
}

internal class VerifyAuthenticatorAppCodeUseCaseImpl(private val repository: MFARepository) : VerifyAuthenticatorAppCodeUseCase {
    override suspend fun invoke(enrollmentId: String, code: String): VerifyAuthenticatorAppCodeResult =
        repository.verifyAuthenticatorCode(enrollmentId, code).fold(
            { VerifyAuthenticatorAppCodeResult.Failure(it) },
            { VerifyAuthenticatorAppCodeResult.Success },
        )
}

/** Outcome of verifying an additional authenticator's authentication code. */
public sealed interface VerifyAuthenticatorAppCodeResult {
    /** The enrollment's authentication code was verified. */
    public data object Success : VerifyAuthenticatorAppCodeResult

    /** The reason the enrollment could not be verified. */
    public data class Failure(public val failure: MFAFailure) : VerifyAuthenticatorAppCodeResult
}
