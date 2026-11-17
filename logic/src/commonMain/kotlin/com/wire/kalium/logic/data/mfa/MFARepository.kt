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
import com.wire.kalium.logic.feature.mfa.MFAEnrollment
import com.wire.kalium.logic.feature.mfa.MFAFailure
import com.wire.kalium.logic.feature.mfa.MFAOverview
import kotlinx.coroutines.flow.StateFlow

internal interface MFARepository {
    val overview: StateFlow<MFAOverview?>
    suspend fun refresh(): Either<CoreFailure, Unit>
    suspend fun startSetup(password: String): Either<MFAFailure, MFAEnrollment>
    suspend fun verifySetupCode(enrollmentId: String, code: String): Either<MFAFailure, List<String>>
    suspend fun completeSetup(enrollmentId: String, name: String): Either<MFAFailure, Unit>
    suspend fun verifyAuthenticatorCode(enrollmentId: String, code: String): Either<MFAFailure, Unit>
    suspend fun finishAddingAuthenticator(enrollmentId: String, name: String): Either<MFAFailure, AuthenticatorApp>
    suspend fun renameAuthenticator(appId: String, name: String): Either<MFAFailure, AuthenticatorApp>
    suspend fun removeAuthenticator(appId: String, password: String, code: String): Either<MFAFailure, Unit>
    suspend fun confirmPassword(password: String): Either<MFAFailure, Unit>
    suspend fun generateRecoveryCodes(): Either<MFAFailure, List<String>>
    suspend fun turnOff(password: String, code: String): Either<MFAFailure, Unit>
}
