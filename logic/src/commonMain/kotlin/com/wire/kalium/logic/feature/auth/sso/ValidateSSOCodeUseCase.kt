/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.auth.sso

import com.benasher44.uuid.uuidFrom
import com.wire.kalium.logic.feature.auth.sso.ValidateSSOCodeUseCase.Companion.SSO_CODE_WIRE_PREFIX
import io.mockative.Mockable

/**
 * Validates a SSO code
 */
@Mockable
interface ValidateSSOCodeUseCase {
    /**
     * @param ssoCode the SSO code to validate
     * @return the [ValidateSSOCodeResult] with the UUID if successful
     */
    operator fun invoke(ssoCode: String): ValidateSSOCodeResult

    companion object {
        const val SSO_CODE_WIRE_PREFIX = "wire-"
    }
}

internal class ValidateSSOCodeUseCaseImpl : ValidateSSOCodeUseCase {
    override fun invoke(ssoCode: String): ValidateSSOCodeResult =
        if (!ssoCode.startsWith(SSO_CODE_WIRE_PREFIX)) ValidateSSOCodeResult.Invalid
        else ssoCode.removePrefix(SSO_CODE_WIRE_PREFIX).let { uuid ->
            try {
                uuidFrom(uuid).let { ValidateSSOCodeResult.Valid(uuid) }
            } catch (e: IllegalArgumentException) {
                ValidateSSOCodeResult.Invalid
            }
        }
}

sealed class ValidateSSOCodeResult {
    data class Valid(val uuid: String) : ValidateSSOCodeResult()
    data object Invalid : ValidateSSOCodeResult()
}
