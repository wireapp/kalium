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
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages an account's multi-factor authentication (MFA) configuration and pending authenticator enrollments.
 *
 * Exposes the current configuration through [overview] and publishes configuration changes made by successful operations.
 * Operations that depend on the current configuration validate and apply their changes atomically.
 */
internal interface MFARepository {
    /**
     * The account's latest known MFA configuration, or null if it has not been loaded.
     * Contains authenticator details and recovery-code counts, but not recovery-code values.
     */
    val overview: StateFlow<MFAOverview?>

    /**
     * Refreshes the account's MFA configuration and publishes it through [overview].
     * Retains the latest known configuration on failure and propagates coroutine cancellation.
     *
     * @return A successful result, or the failure that prevented the refresh.
     */
    suspend fun refresh(): Either<CoreFailure, Unit>

    /**
     * Confirms [password] and starts an enrollment for initial MFA setup or an additional authenticator.
     * Starting an enrollment does not enable MFA or add an authenticator to the account.
     *
     * @return Enrollment details for configuring an authenticator, or an MFA failure.
     */
    suspend fun startSetup(password: String): Either<MFAFailure, MFAEnrollment>

    /**
     * Verifies [code] for the initial enrollment identified by [enrollmentId] and prepares its recovery codes.
     * The enrollment must be completed with [completeSetup] to enable MFA.
     *
     * @return Recovery-code values for the caller to present to the user, or an MFA failure.
     */
    suspend fun verifySetupCode(enrollmentId: String, code: String): Either<MFAFailure, List<String>>

    /**
     * Enables MFA using the initial enrollment identified by [enrollmentId] and names its authenticator with [name].
     * Requires an enrollment verified through [verifySetupCode] with recovery codes prepared, and a nonblank name.
     * Publishes the enabled configuration through [overview].
     *
     * @return A successful result, or an MFA failure if setup cannot be completed.
     */
    suspend fun completeSetup(enrollmentId: String, name: String): Either<MFAFailure, Unit>

    /**
     * Verifies [code] for an additional authenticator's enrollment identified by [enrollmentId].
     * Does not replace recovery codes or add the authenticator; use [finishAddingAuthenticator] to complete enrollment.
     *
     * @return A successful result, or an MFA failure if the enrollment or code is invalid.
     */
    suspend fun verifyAuthenticatorCode(enrollmentId: String, code: String): Either<MFAFailure, Unit>

    /**
     * Adds the verified enrollment identified by [enrollmentId] as an authenticator with [name].
     * Requires MFA to be enabled, a nonblank name, and capacity within the account's authenticator limit.
     * Publishes the updated configuration while preserving existing recovery codes.
     *
     * @return The added authenticator, or an MFA failure.
     */
    suspend fun finishAddingAuthenticator(enrollmentId: String, name: String): Either<MFAFailure, AuthenticatorApp>

    /**
     * Renames the authenticator identified by [appId] using the nonblank [name] and publishes the updated configuration.
     * Preserves the authenticator's identity and added date, and the account's recovery codes.
     *
     * @return The renamed authenticator, or an MFA failure.
     */
    suspend fun renameAuthenticator(appId: String, name: String): Either<MFAFailure, AuthenticatorApp>

    /**
     * Confirms [password] and the authentication [code], then removes the authenticator identified by [appId].
     * Prevents removal of the account's final authenticator; use [turnOff] to disable MFA instead.
     * Publishes the updated configuration while preserving recovery codes.
     *
     * @return A successful result, or an MFA failure if credentials or removal constraints are not satisfied.
     */
    suspend fun removeAuthenticator(appId: String, password: String, code: String): Either<MFAFailure, Unit>

    /**
     * Confirms the account's [password] before recovery-code management.
     * Does not change the MFA configuration or generate recovery codes.
     *
     * @return A successful result, or an MFA failure if the password cannot be confirmed.
     */
    suspend fun confirmPassword(password: String): Either<MFAFailure, Unit>

    /**
     * Replaces recovery codes for an MFA-enabled account and publishes their updated counts through [overview].
     * Preserves the account's authenticators. Recovery-code values are returned directly rather than exposed in [overview].
     *
     * @return The new recovery-code values for the caller to present to the user, or an MFA failure.
     */
    suspend fun generateRecoveryCodes(): Either<MFAFailure, List<String>>

    /**
     * Confirms [password] and the authentication [code], disables MFA, and invalidates pending authenticator enrollments.
     * Publishes the disabled configuration through [overview].
     *
     * @return A successful result, or an MFA failure if the credentials cannot be confirmed.
     */
    suspend fun turnOff(password: String, code: String): Either<MFAFailure, Unit>
}
