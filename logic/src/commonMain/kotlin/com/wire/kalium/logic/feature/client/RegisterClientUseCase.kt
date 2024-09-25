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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.auth.verification.RequestSecondFactorVerificationCodeUseCase
import com.wire.kalium.logic.feature.client.RegisterClientUseCase.Companion.FIRST_KEY_ID
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.network.exceptions.AuthenticationCodeFailure
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.authenticationCodeFailure
import com.wire.kalium.network.exceptions.isBadRequest
import com.wire.kalium.network.exceptions.isInvalidCredentials
import com.wire.kalium.network.exceptions.isMissingAuth
import com.wire.kalium.network.exceptions.isTooManyClients
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

sealed class RegisterClientResult {
    class Success(val client: Client) : RegisterClientResult()

    class E2EICertificateRequired(val client: Client, val userId: UserId) : RegisterClientResult()

    sealed class Failure : RegisterClientResult() {
        sealed class InvalidCredentials : Failure() {
            /**
             * The team has enabled 2FA but has not provided a 2FA code.
             */
            data object Missing2FA : InvalidCredentials()

            /**
             * The team has enabled 2FA but the user has provided an invalid or expired 2FA code.
             */
            data object Invalid2FA : InvalidCredentials()

            /**
             * The password is invalid.
             */
            data object InvalidPassword : InvalidCredentials()
        }

        data object TooManyClients : Failure()
        data object PasswordAuthRequired : Failure()
        data class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

/**
 * This use case is responsible for registering the client.
 * The client will be registered on the backend and the local storage.
 *
 * If fails due to missing or invalid 2FA code, use
 * [RequestSecondFactorVerificationCodeUseCase] to request a new code
 * and then call this method again with the new code.
 *
 * @see RequestSecondFactorVerificationCodeUseCase
 */
interface RegisterClientUseCase {
    suspend operator fun invoke(
        registerClientParam: RegisterClientParam
    ): RegisterClientResult

    /**
     * The required data needed to register a client
     * password
     * capabilities :Hints provided by the client for the backend so it can behave in a backwards-compatible way.
     * ex : legalHoldConsent
     * preKeysToSend : the initial public keys to start a conversation with another client
     * @see [RegisterClientParam]
     */
    data class RegisterClientParam(
        val password: String?,
        val capabilities: List<ClientCapability>?,
        val clientType: ClientType? = null,
        val model: String? = null,
        val preKeysToSend: Int = DEFAULT_PRE_KEYS_COUNT,
        val secondFactorVerificationCode: String? = null,
        val modelPostfix: String? = null
    )

    companion object {
        const val FIRST_KEY_ID = 0
        const val DEFAULT_PRE_KEYS_COUNT = 100
    }
}

@Suppress("LongParameterList")
class RegisterClientUseCaseImpl @OptIn(DelicateKaliumApi::class) internal constructor(
    private val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase,
    private val clientRepository: ClientRepository,
    private val preKeyRepository: PreKeyRepository,
    private val sessionRepository: SessionRepository,
    private val selfUserId: UserId,
    private val userRepository: UserRepository,
    private val secondFactorVerificationRepository: SecondFactorVerificationRepository,
    private val registerMLSClientUseCase: RegisterMLSClientUseCase,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : RegisterClientUseCase {

    @OptIn(DelicateKaliumApi::class)
    override suspend operator fun invoke(
        registerClientParam: RegisterClientUseCase.RegisterClientParam
    ): RegisterClientResult = with(registerClientParam) {
        val verificationCode = registerClientParam.secondFactorVerificationCode ?: currentlyStoredVerificationCode()
        sessionRepository.cookieLabel(selfUserId)
            .flatMap { cookieLabel ->
                generateProteusPreKeys(
                    preKeysToSend,
                    password,
                    capabilities,
                    clientType,
                    model,
                    cookieLabel,
                    verificationCode,
                    modelPostfix,
                )
            }.fold({
                RegisterClientResult.Failure.Generic(it)
            }, { registerClientParam ->
                clientRepository.registerClient(registerClientParam)
                    // todo? separate this in mls client usesCase register! separate everything
                    .flatMap { registeredClient ->
                        if (isAllowedToRegisterMLSClient()) {
                            registerMLSClientUseCase.invoke(clientId = registeredClient.id).flatMap {
                                if (it is RegisterMLSClientResult.E2EICertificateRequired)
                                    return RegisterClientResult.E2EICertificateRequired(registeredClient, selfUserId)
                                else Either.Right(registeredClient)
                            }
                        } else {
                            Either.Right(registeredClient)
                        }.map { client -> client to registerClientParam.preKeys.maxOfOrNull { it.id } }
                    }.flatMap { (client, otrLastKeyId) ->
                        otrLastKeyId?.let { preKeyRepository.updateMostRecentPreKeyId(it) }
                        Either.Right(client)
                    }.fold({ failure ->
                        handleFailure(failure)
                    }, { client ->
                        RegisterClientResult.Success(client)
                    })
            })
    }

    private suspend fun currentlyStoredVerificationCode(): String? {
        val userEmail = userRepository.getSelfUser()?.email
        return userEmail?.let { secondFactorVerificationRepository.getStoredVerificationCode(it) }
    }

    private suspend fun handleFailure(
        failure: CoreFailure,
    ): RegisterClientResult = if (failure is NetworkFailure.ServerMiscommunication &&
        failure.kaliumException is KaliumException.InvalidRequestError
    ) {
        val kaliumException = failure.kaliumException
        val authCodeFailure = kaliumException.authenticationCodeFailure
        when {
            kaliumException.isTooManyClients() -> RegisterClientResult.Failure.TooManyClients
            kaliumException.isMissingAuth() -> RegisterClientResult.Failure.PasswordAuthRequired
            kaliumException.isInvalidCredentials() ->
                RegisterClientResult.Failure.InvalidCredentials.InvalidPassword

            kaliumException.isBadRequest() ->
                RegisterClientResult.Failure.InvalidCredentials.InvalidPassword

            authCodeFailure != null -> handleAuthCodeFailure(authCodeFailure)

            else -> RegisterClientResult.Failure.Generic(failure)
        }
    } else RegisterClientResult.Failure.Generic(failure)

    private suspend fun handleAuthCodeFailure(authCodeFailure: AuthenticationCodeFailure) = when (authCodeFailure) {
        AuthenticationCodeFailure.MISSING_AUTHENTICATION_CODE ->
            RegisterClientResult.Failure.InvalidCredentials.Missing2FA

        AuthenticationCodeFailure.INVALID_OR_EXPIRED_AUTHENTICATION_CODE -> {
            userRepository.getSelfUser()?.email?.let {
                secondFactorVerificationRepository.clearStoredVerificationCode(it)
            }
            RegisterClientResult.Failure.InvalidCredentials.Invalid2FA
        }
    }

    private suspend fun generateProteusPreKeys(
        preKeysToSend: Int,
        password: String?,
        capabilities: List<ClientCapability>?,
        clientType: ClientType? = null,
        model: String? = null,
        cookieLabel: String?,
        secondFactorVerificationCode: String? = null,
        modelPostfix: String?
    ) = withContext(dispatchers.io) {
        preKeyRepository.generateNewPreKeys(FIRST_KEY_ID, preKeysToSend).flatMap { preKeys ->
            preKeyRepository.generateNewLastResortKey().flatMap { lastKey ->
                Either.Right(
                    RegisterClientParam(
                        password = password,
                        capabilities = capabilities,
                        preKeys = preKeys,
                        lastKey = lastKey,
                        deviceType = null,
                        label = null,
                        model = model,
                        clientType = clientType,
                        cookieLabel = cookieLabel,
                        secondFactorVerificationCode = secondFactorVerificationCode,
                        modelPostfix = modelPostfix
                    )
                )
            }
        }
    }
}
