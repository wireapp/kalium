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

/**
 * Details needed to configure an authenticator for MFA enrollment.
 *
 * @property id Identifies the enrollment when verifying and completing setup.
 * @property account Account label displayed by the authenticator.
 * @property setupKey Shared secret for manually configuring the authenticator.
 * @property authenticatorUri Server-provided TOTP provisioning URI for configuring an authenticator.
 */
public data class MFAEnrollment(
    public val id: String,
    public val account: String,
    public val setupKey: String,
    public val authenticatorUri: String,
)

/** Limits and format requirements for MFA configuration. */
public object MFAConstraints {
    /** Number of digits in an authenticator-generated authentication code. */
    public const val AUTHENTICATION_CODE_LENGTH: Int = 6

    /** Maximum number of authenticators that can be registered to an account. */
    public const val MAX_AUTHENTICATOR_APPS: Int = 5
}

/** Reasons an MFA operation could not be completed. */
public enum class MFAFailure {
    /** The account password could not be confirmed. */
    INVALID_PASSWORD,

    /** The authentication code could not be verified. */
    INVALID_CODE,

    /** The authenticator name does not meet naming requirements. */
    INVALID_NAME,

    /** The enrollment does not exist or has not completed the required verification steps. */
    INVALID_ENROLLMENT,

    /** The operation requires MFA to be enabled. */
    MFA_DISABLED,

    /** Initial setup cannot be completed because MFA is already enabled. */
    MFA_ALREADY_ENABLED,

    /** The requested authenticator does not exist. */
    NOT_FOUND,

    /** The account's final authenticator cannot be removed while MFA is enabled. */
    LAST_APP,

    /** The account has reached its authenticator limit. */
    LIMIT_REACHED,
}
