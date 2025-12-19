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

package com.wire.kalium.logic.feature.auth

import kotlin.uuid.Uuid
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.feature.auth.verification.RequestSecondFactorVerificationCodeUseCase
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.network.exceptions.AuthenticationCodeFailure
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.authenticationCodeFailure
import com.wire.kalium.network.exceptions.isAccountPendingActivation
import com.wire.kalium.network.exceptions.isAccountSuspended
import com.wire.kalium.network.exceptions.isBadRequest
import com.wire.kalium.network.exceptions.isInvalidCredentials

public sealed class AuthenticationResult {
    public data class Success(
        val authData: AccountTokens,
        val ssoID: SsoId?,
        val serverConfigId: String,
        val proxyCredentials: ProxyCredentials?
    ) : AuthenticationResult()

    public sealed class Failure : AuthenticationResult() {
        public data object SocketError : Failure()
        public sealed class InvalidCredentials : Failure() {
            /**
             * The team has enabled 2FA but the user has not entered it yet
             */
            public data object Missing2FA : InvalidCredentials()

            /**
             * The user has entered an invalid 2FA code, or the 2FA code has expired
             */
            public data object Invalid2FA : InvalidCredentials()

            /**
             * The user has entered an invalid email/handle or password combination
             */
            public data object InvalidPasswordIdentityCombination : InvalidCredentials()
        }

        /**
         * The user has entered a text that isn't considered a valid email or handle
         */
        public data object InvalidUserIdentifier : Failure()

        /**
         * The user's account has been suspended, for instance via backoffice
         */
        public data object AccountSuspended : Failure()

        /**
         * The user has been invited to a team but hasn't accepted the invitation yet
         */
        public data object AccountPendingActivation : Failure()

        public data class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

public interface LoginUseCase {
    /**
     * Login with user credentials and return the session
     * Be noticed that session won't be stored locally, to store it use [AddAuthenticatedUserUseCase].
     *
     * If fails due to missing or invalid 2FA code, use
     * [RequestSecondFactorVerificationCodeUseCase] to request a new code
     * and then call this method again with the new code.
     *
     * @see AddAuthenticatedUserUseCase
     * @see RequestSecondFactorVerificationCodeUseCase
     */
    public suspend operator fun invoke(
        userIdentifier: String,
        password: String,
        shouldPersistClient: Boolean,
        cookieLabel: String? = Uuid.random().toString(),
        secondFactorVerificationCode: String? = null,
    ): AuthenticationResult
}

internal class LoginUseCaseImpl internal constructor(
    private val loginRepository: LoginRepository,
    private val validateEmailUseCase: ValidateEmailUseCase,
    private val validateUserHandleUseCase: ValidateUserHandleUseCase,
    private val serverConfig: ServerConfig,
    private val proxyCredentials: ProxyCredentials?,
    private val secondFactorVerificationRepository: SecondFactorVerificationRepository,
) : LoginUseCase {
    override suspend operator fun invoke(
        userIdentifier: String,
        password: String,
        shouldPersistClient: Boolean,
        cookieLabel: String?,
        secondFactorVerificationCode: String?,
    ): AuthenticationResult {
        // remove White Spaces around userIdentifier
        val cleanUserIdentifier = userIdentifier.trim()
        val isEmail = validateEmailUseCase(cleanUserIdentifier)
        val clean2FACode = secondFactorVerificationCode?.trim()?.takeIf { it.isNotBlank() }
        return when {
            isEmail -> {
                loginRepository.loginWithEmail(
                    email = cleanUserIdentifier,
                    password = password,
                    label = cookieLabel,
                    shouldPersistClient = shouldPersistClient,
                    secondFactorVerificationCode = clean2FACode
                )
            }

            validateUserHandleUseCase(cleanUserIdentifier).isValid -> {
                loginRepository.loginWithHandle(
                    handle = cleanUserIdentifier,
                    password = password,
                    label = cookieLabel,
                    shouldPersistClient = shouldPersistClient,
                )
            }

            else -> return AuthenticationResult.Failure.InvalidUserIdentifier
        }.map { (authTokens, ssoId) -> AuthenticationResult.Success(authTokens, ssoId, serverConfig.id, proxyCredentials) }
            .fold({
                when (it) {
                    is NetworkFailure.ProxyError -> AuthenticationResult.Failure.SocketError
                    is NetworkFailure.ServerMiscommunication -> handleServerMiscommunication(it, isEmail, cleanUserIdentifier)
                    is NetworkFailure.NoNetworkConnection,
                    is NetworkFailure.FederatedBackendFailure,
                    is NetworkFailure.FeatureNotSupported,
                    is NetworkFailure.MlsMessageRejectedFailure -> AuthenticationResult.Failure.Generic(it)
                }
            }, {
                if (isEmail && clean2FACode != null) {
                    secondFactorVerificationRepository.storeVerificationCode(cleanUserIdentifier, clean2FACode)
                }
                it
            })
    }

    private suspend fun handleServerMiscommunication(
        error: NetworkFailure.ServerMiscommunication,
        isEmail: Boolean,
        userIdentifier: String,
    ): AuthenticationResult.Failure {
        fun genericError() = AuthenticationResult.Failure.Generic(error)

        val kaliumException = error.kaliumException

        return when {
            kaliumException !is KaliumException.InvalidRequestError -> genericError()
            kaliumException.isInvalidCredentials() || kaliumException.isBadRequest() -> {
                AuthenticationResult.Failure.InvalidCredentials.InvalidPasswordIdentityCombination
            }
            kaliumException.isAccountSuspended() -> AuthenticationResult.Failure.AccountSuspended
            kaliumException.isAccountPendingActivation() -> AuthenticationResult.Failure.AccountPendingActivation

            else -> when (kaliumException.authenticationCodeFailure) {
                AuthenticationCodeFailure.MISSING_AUTHENTICATION_CODE ->
                    AuthenticationResult.Failure.InvalidCredentials.Missing2FA

                AuthenticationCodeFailure.INVALID_OR_EXPIRED_AUTHENTICATION_CODE -> {
                    if (isEmail) {
                        secondFactorVerificationRepository.clearStoredVerificationCode(userIdentifier)
                    }
                    AuthenticationResult.Failure.InvalidCredentials.Invalid2FA
                }

                else -> genericError()
            }
        }
    }
}
