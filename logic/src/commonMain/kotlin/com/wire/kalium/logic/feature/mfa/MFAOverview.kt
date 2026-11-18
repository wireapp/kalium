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

import kotlinx.datetime.LocalDate

/** The account's MFA configuration, including whether MFA is enabled. */
public sealed interface MFAOverview {
    /** MFA is not enabled for the account. */
    public data object Disabled : MFAOverview

    /**
     * MFA is enabled for the account.
     *
     * @property authenticatorApps Authenticators registered to the account.
     * @property recoveryCodesStatus Availability of the account's recovery codes.
     */
    public data class Enabled(
        public val authenticatorApps: List<AuthenticatorApp>,
        public val recoveryCodesStatus: RecoveryCodesStatus,
    ) : MFAOverview
}

/**
 * An authenticator registered to an account for MFA.
 *
 * @property id Identifies the authenticator when renaming or removing it.
 * @property name User-visible name of the authenticator.
 * @property addedOn Date the authenticator was registered.
 */
public data class AuthenticatorApp(
    public val id: String,
    public val name: String,
    public val addedOn: LocalDate,
)

/**
 * Recovery-code availability without exposing the code values.
 *
 * @property remaining Number of unused recovery codes available.
 * @property total Number of recovery codes in the current set, including those already used.
 */
public data class RecoveryCodesStatus(
    public val remaining: Int,
    public val total: Int,
)
