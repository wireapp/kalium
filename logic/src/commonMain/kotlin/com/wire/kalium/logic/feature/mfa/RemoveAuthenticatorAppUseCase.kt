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

/** Confirms credentials and removes an authenticator atomically, preserving the final authenticator. */
public interface RemoveAuthenticatorAppUseCase {
    public suspend operator fun invoke(appId: String, password: String, code: String): Either<MFAFailure, Unit>
}

internal class RemoveAuthenticatorAppUseCaseImpl(private val repository: MFARepository) : RemoveAuthenticatorAppUseCase {
    override suspend fun invoke(appId: String, password: String, code: String): Either<MFAFailure, Unit> = repository.update {
        if (password.isBlank()) return@update Either.Left(MFAFailure.INVALID_PASSWORD)
        if (code != DummyMFAData.TOTP_CODE) return@update Either.Left(MFAFailure.INVALID_CODE)
        val enabled = overview as? MFAOverview.Enabled ?: return@update Either.Left(MFAFailure.MFA_DISABLED)
        if (enabled.authenticatorApps.none { it.id == appId }) return@update Either.Left(MFAFailure.NOT_FOUND)
        if (enabled.authenticatorApps.size <= 1) return@update Either.Left(MFAFailure.LAST_APP)
        overview = enabled.copy(authenticatorApps = enabled.authenticatorApps.filterNot { it.id == appId })
        Either.Right(Unit)
    }
}
