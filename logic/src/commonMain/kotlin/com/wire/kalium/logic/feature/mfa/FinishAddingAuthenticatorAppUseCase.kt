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
 * Adds a verified enrollment, enforcing the account's authenticator limit and publishing the change.
 *
 * **Warning:** Currently backed by a temporary dummy implementation.
 */
public interface FinishAddingAuthenticatorAppUseCase {
    public suspend operator fun invoke(enrollmentId: String, name: String): FinishAddingAuthenticatorAppResult
}

internal class FinishAddingAuthenticatorAppUseCaseImpl(private val repository: MFARepository) : FinishAddingAuthenticatorAppUseCase {
    override suspend fun invoke(enrollmentId: String, name: String): FinishAddingAuthenticatorAppResult =
        repository.finishAddingAuthenticator(enrollmentId, name).fold(
            { FinishAddingAuthenticatorAppResult.Failure(it) },
            { FinishAddingAuthenticatorAppResult.Success(it) },
        )
}

/** Outcome of completing an additional authenticator's enrollment. */
public sealed interface FinishAddingAuthenticatorAppResult {
    /** The authenticator added to the account. */
    public data class Success(public val authenticatorApp: AuthenticatorApp) : FinishAddingAuthenticatorAppResult

    /** The reason the authenticator could not be added. */
    public data class Failure(public val failure: MFAFailure) : FinishAddingAuthenticatorAppResult
}
