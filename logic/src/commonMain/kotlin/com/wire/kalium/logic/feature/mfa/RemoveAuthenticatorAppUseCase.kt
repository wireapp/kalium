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
import com.wire.kalium.logic.data.mfa.MFAFailure
import com.wire.kalium.logic.data.mfa.MFARepository

/**
 * Confirms credentials and removes an authenticator atomically, preserving the final authenticator.
 *
 * **Warning:** Currently backed by a temporary dummy implementation.
 */
public interface RemoveAuthenticatorAppUseCase {
    public suspend operator fun invoke(appId: String, password: String, code: String): RemoveAuthenticatorAppResult
}

internal class RemoveAuthenticatorAppUseCaseImpl(private val repository: MFARepository) : RemoveAuthenticatorAppUseCase {
    override suspend fun invoke(appId: String, password: String, code: String): RemoveAuthenticatorAppResult =
        repository.removeAuthenticator(appId, password, code).fold(
            { RemoveAuthenticatorAppResult.Failure(it) },
            { RemoveAuthenticatorAppResult.Success },
        )
}

public sealed interface RemoveAuthenticatorAppResult {
    public data object Success : RemoveAuthenticatorAppResult

    public data class Failure(public val failure: MFAFailure) : RemoveAuthenticatorAppResult
}
