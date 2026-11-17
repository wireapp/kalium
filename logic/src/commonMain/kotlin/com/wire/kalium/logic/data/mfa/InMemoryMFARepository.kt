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

package com.wire.kalium.logic.data.mfa

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.feature.mfa.AuthenticatorApp
import com.wire.kalium.logic.feature.mfa.MFAConstraints
import com.wire.kalium.logic.feature.mfa.MFAEnrollment
import com.wire.kalium.logic.feature.mfa.MFAFailure
import com.wire.kalium.logic.feature.mfa.MFAOverview
import com.wire.kalium.logic.feature.mfa.RecoveryCodesStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Simulated account backend. One instance belongs to one Kalium account session. */
internal class InMemoryMFARepository(
    initialOverview: MFAOverview = MFAOverview.Disabled,
    private val loadOverview: (suspend () -> MFAOverview)? = null,
) : MFARepository {
    private val account = MFAAccountState(initialOverview)
    private val mutableOverview = MutableStateFlow<MFAOverview?>(null)
    override val overview = mutableOverview.asStateFlow()
    private val mutex = Mutex()
    private var hasLocalChanges = false

    @Suppress("TooGenericExceptionCaught")
    override suspend fun refresh(): Either<CoreFailure, Unit> {
        if (!mutex.tryLock()) return Either.Right(Unit)
        return try {
            delay(DummyMFAData.OPERATION_DELAY_MILLIS)
            val loaded = loadOverview?.invoke() ?: account.overview
            if (!hasLocalChanges) account.overview = loaded
            mutableOverview.value = account.overview
            Either.Right(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Either.Left(CoreFailure.Unknown(failure))
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun startSetup(password: String): Either<MFAFailure, MFAEnrollment> = update {
        if (password.isBlank()) return@update Either.Left(MFAFailure.INVALID_PASSWORD)
        val enrollment = DummyMFAData.enrollment()
        enrollments[enrollment.id] = PendingMFAEnrollment()
        Either.Right(enrollment)
    }

    override suspend fun verifySetupCode(enrollmentId: String, code: String): Either<MFAFailure, List<String>> = update {
        val pending = enrollments[enrollmentId] ?: return@update Either.Left(MFAFailure.INVALID_ENROLLMENT)
        if (code != DummyMFAData.TOTP_CODE) return@update Either.Left(MFAFailure.INVALID_CODE)
        val codes = DummyMFAData.initialRecoveryCodes
        enrollments[enrollmentId] = pending.copy(isVerified = true, recoveryCodesCount = codes.size)
        Either.Right(codes)
    }

    override suspend fun completeSetup(enrollmentId: String, name: String): Either<MFAFailure, Unit> = update {
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

    override suspend fun verifyAuthenticatorCode(enrollmentId: String, code: String): Either<MFAFailure, Unit> = update {
        val pending = enrollments[enrollmentId] ?: return@update Either.Left(MFAFailure.INVALID_ENROLLMENT)
        if (code != DummyMFAData.TOTP_CODE) return@update Either.Left(MFAFailure.INVALID_CODE)
        enrollments[enrollmentId] = pending.copy(isVerified = true)
        Either.Right(Unit)
    }

    override suspend fun finishAddingAuthenticator(enrollmentId: String, name: String): Either<MFAFailure, AuthenticatorApp> = update {
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

    override suspend fun renameAuthenticator(appId: String, name: String): Either<MFAFailure, AuthenticatorApp> = update {
        val enabled = overview as? MFAOverview.Enabled ?: return@update Either.Left(MFAFailure.MFA_DISABLED)
        val app = enabled.authenticatorApps.find { it.id == appId } ?: return@update Either.Left(MFAFailure.NOT_FOUND)
        if (name.isBlank()) return@update Either.Left(MFAFailure.INVALID_NAME)
        val renamed = app.copy(name = name.trim())
        overview = enabled.copy(authenticatorApps = enabled.authenticatorApps.map { if (it.id == appId) renamed else it })
        Either.Right(renamed)
    }

    override suspend fun removeAuthenticator(appId: String, password: String, code: String): Either<MFAFailure, Unit> = update {
        if (password.isBlank()) return@update Either.Left(MFAFailure.INVALID_PASSWORD)
        if (code != DummyMFAData.TOTP_CODE) return@update Either.Left(MFAFailure.INVALID_CODE)
        val enabled = overview as? MFAOverview.Enabled ?: return@update Either.Left(MFAFailure.MFA_DISABLED)
        if (enabled.authenticatorApps.none { it.id == appId }) return@update Either.Left(MFAFailure.NOT_FOUND)
        if (enabled.authenticatorApps.size <= 1) return@update Either.Left(MFAFailure.LAST_APP)
        overview = enabled.copy(authenticatorApps = enabled.authenticatorApps.filterNot { it.id == appId })
        Either.Right(Unit)
    }

    override suspend fun confirmPassword(password: String): Either<MFAFailure, Unit> = update {
        if (password.isBlank()) Either.Left(MFAFailure.INVALID_PASSWORD) else Either.Right(Unit)
    }

    override suspend fun generateRecoveryCodes(): Either<MFAFailure, List<String>> = update {
        val enabled = overview as? MFAOverview.Enabled ?: return@update Either.Left(MFAFailure.MFA_DISABLED)
        val codes = DummyMFAData.recoveryCodes()
        overview = enabled.copy(recoveryCodesStatus = RecoveryCodesStatus(codes.size, codes.size))
        Either.Right(codes)
    }

    override suspend fun turnOff(password: String, code: String): Either<MFAFailure, Unit> = update {
        if (password.isBlank()) return@update Either.Left(MFAFailure.INVALID_PASSWORD)
        if (code != DummyMFAData.TOTP_CODE) return@update Either.Left(MFAFailure.INVALID_CODE)
        overview = MFAOverview.Disabled
        enrollments.clear()
        Either.Right(Unit)
    }

    private suspend fun <T> update(block: MFAAccountState.() -> T): T = mutex.withLock {
        delay(DummyMFAData.OPERATION_DELAY_MILLIS)
        val previous = account.overview
        val result = account.block()
        if (account.overview != previous) {
            hasLocalChanges = true
            mutableOverview.value = account.overview
        }
        result
    }

    /** Only enrollment metadata is retained here, never passwords, setup keys, or recovery-code values. */
    private class MFAAccountState(var overview: MFAOverview) {
        val enrollments = mutableMapOf<String, PendingMFAEnrollment>()
    }

    private data class PendingMFAEnrollment(val isVerified: Boolean = false, val recoveryCodesCount: Int = 0)
}
