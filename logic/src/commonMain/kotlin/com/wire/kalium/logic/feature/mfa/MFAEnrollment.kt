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

public data class MFAEnrollment(
    public val id: String,
    public val account: String,
    public val setupKey: String,
) {
    public val authenticatorUri: String
        get() = "otpauth://totp/Wire:$account?secret=$setupKey&issuer=Wire&algorithm=SHA1&digits=6&period=30"
}

public object MFAConstraints {
    public const val AUTHENTICATION_CODE_LENGTH: Int = 6
    public const val MAX_AUTHENTICATOR_APPS: Int = 5
}

public enum class MFAFailure {
    INVALID_PASSWORD,
    INVALID_CODE,
    INVALID_NAME,
    INVALID_ENROLLMENT,
    MFA_DISABLED,
    MFA_ALREADY_ENABLED,
    NOT_FOUND,
    LAST_APP,
    LIMIT_REACHED,
}
