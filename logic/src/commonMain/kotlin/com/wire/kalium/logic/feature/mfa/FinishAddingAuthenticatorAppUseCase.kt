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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Adds a verified enrollment, enforcing the account's authenticator limit and publishing the change. */
public interface FinishAddingAuthenticatorAppUseCase {
    public suspend operator fun invoke(enrollmentId: String, name: String): Either<MFAFailure, AuthenticatorApp>
}

internal class FinishAddingAuthenticatorAppUseCaseImpl(private val repository: MFARepository) : FinishAddingAuthenticatorAppUseCase {
    override suspend fun invoke(enrollmentId: String, name: String): Either<MFAFailure, AuthenticatorApp> = repository.update {
        val enabled = overview as? MFAOverview.Enabled ?: return@update Either.Left(MFAFailure.MFA_DISABLED)
        val existing = enabled.authenticatorApps.find { it.id == enrollmentId }
        if (existing != null) return@update Either.Right(existing)
        val pending = enrollments[enrollmentId]
        if (pending == null || !pending.isVerified) return@update Either.Left(MFAFailure.INVALID_ENROLLMENT)
        if (enabled.authenticatorApps.size >= MFAConstraints.MAX_AUTHENTICATOR_APPS) {
            return@update Either.Left(MFAFailure.LIMIT_REACHED)
        }
        if (name.isBlank()) return@update Either.Left(MFAFailure.INVALID_NAME)
        val app = AuthenticatorApp(enrollmentId, name.trim(), Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date)
        overview = enabled.copy(authenticatorApps = enabled.authenticatorApps + app)
        enrollments.remove(enrollmentId)
        Either.Right(app)
    }
}
