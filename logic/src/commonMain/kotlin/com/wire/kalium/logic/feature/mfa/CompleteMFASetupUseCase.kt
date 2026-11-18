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
 * Enables MFA using a verified initial enrollment and publishes the account's new overview.
 *
 * **Warning:** Currently backed by a temporary dummy implementation.
 */
public interface CompleteMFASetupUseCase {
    public suspend operator fun invoke(enrollmentId: String, name: String): CompleteMFASetupResult
}

internal class CompleteMFASetupUseCaseImpl(private val repository: MFARepository) : CompleteMFASetupUseCase {
    override suspend fun invoke(enrollmentId: String, name: String): CompleteMFASetupResult =
        repository.completeSetup(enrollmentId, name).fold(
            { CompleteMFASetupResult.Failure(it) },
            { CompleteMFASetupResult.Success },
        )
}

/** Outcome of completing initial MFA setup. */
public sealed interface CompleteMFASetupResult {
    /** MFA setup completed successfully. */
    public data object Success : CompleteMFASetupResult

    /** The reason setup could not be completed. */
    public data class Failure(public val failure: MFAFailure) : CompleteMFASetupResult
}
