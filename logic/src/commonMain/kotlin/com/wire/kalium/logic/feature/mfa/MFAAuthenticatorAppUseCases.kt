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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Verifies an additional authenticator's enrollment without generating recovery codes. */
public interface VerifyAuthenticatorAppCodeUseCase {
    public suspend operator fun invoke(enrollmentId: String, code: String): Either<MFAFailure, Unit>
}

internal class VerifyAuthenticatorAppCodeUseCaseImpl(private val repository: MFARepository) : VerifyAuthenticatorAppCodeUseCase {
    override suspend fun invoke(enrollmentId: String, code: String): Either<MFAFailure, Unit> = repository.update {
        val pending = enrollments[enrollmentId] ?: return@update Either.Left(MFAFailure.INVALID_ENROLLMENT)
        if (code != DummyMFAData.TOTP_CODE) return@update Either.Left(MFAFailure.INVALID_CODE)
        enrollments[enrollmentId] = pending.copy(isVerified = true)
        Either.Right(Unit)
    }
}

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

/** Renames an existing authenticator, preserving its identity, added date, and the account's recovery codes. */
public interface RenameAuthenticatorAppUseCase {
    public suspend operator fun invoke(appId: String, name: String): Either<MFAFailure, AuthenticatorApp>
}

internal class RenameAuthenticatorAppUseCaseImpl(private val repository: MFARepository) : RenameAuthenticatorAppUseCase {
    override suspend fun invoke(appId: String, name: String): Either<MFAFailure, AuthenticatorApp> = repository.update {
        val enabled = overview as? MFAOverview.Enabled ?: return@update Either.Left(MFAFailure.MFA_DISABLED)
        val app = enabled.authenticatorApps.find { it.id == appId } ?: return@update Either.Left(MFAFailure.NOT_FOUND)
        if (name.isBlank()) return@update Either.Left(MFAFailure.INVALID_NAME)
        val renamed = app.copy(name = name.trim())
        overview = enabled.copy(authenticatorApps = enabled.authenticatorApps.map { if (it.id == appId) renamed else it })
        Either.Right(renamed)
    }
}

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
