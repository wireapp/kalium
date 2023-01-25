/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.feature.client.RegisterClientUseCase.Companion.FIRST_KEY_ID
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isBadRequest
import com.wire.kalium.network.exceptions.isInvalidCredentials
import com.wire.kalium.network.exceptions.isMissingAuth
import com.wire.kalium.network.exceptions.isTooManyClients
import com.wire.kalium.util.DelicateKaliumApi

sealed class RegisterClientResult {
    class Success(val client: Client) : RegisterClientResult()

    sealed class Failure : RegisterClientResult() {
        object InvalidCredentials : Failure()
        object TooManyClients : Failure()
        object PasswordAuthRequired : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

/**
 * This use case is responsible for registering the client.
 * The client will be registered on the backend and the local storage.
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
        val preKeysToSend: Int = DEFAULT_PRE_KEYS_COUNT,
    )

    companion object {
        const val FIRST_KEY_ID = 0
        const val DEFAULT_PRE_KEYS_COUNT = 100
    }
}

class RegisterClientUseCaseImpl @OptIn(DelicateKaliumApi::class) constructor(
    private val isAllowedToRegisterMLSClient: IsAllowedToRegisterMLSClientUseCase,
    private val clientRepository: ClientRepository,
    private val preKeyRepository: PreKeyRepository,
    private val keyPackageRepository: KeyPackageRepository,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val mlsClientProvider: MLSClientProvider
) : RegisterClientUseCase {

    @OptIn(DelicateKaliumApi::class)
    override suspend operator fun invoke(registerClientParam: RegisterClientUseCase.RegisterClientParam): RegisterClientResult =
        with(registerClientParam) {
            generateProteusPreKeys(preKeysToSend, password, capabilities, clientType).fold({
                RegisterClientResult.Failure.Generic(it)
            }, { registerClientParam ->
                clientRepository.registerClient(registerClientParam)
                    .flatMap { registeredClient ->
                        if (isAllowedToRegisterMLSClient()) {
                            createMLSClient(registeredClient)
                        } else {
                            Either.Right(registeredClient)
                        }.map { client -> client to registerClientParam.preKeys.maxOfOrNull { it.id } }
                    }.flatMap { (client, otrLastKeyId) ->
                        otrLastKeyId?.let { preKeyRepository.updateOTRLastPreKeyId(it) }
                        Either.Right(client)
                    }.fold({ failure ->
                        if (failure is NetworkFailure.ServerMiscommunication &&
                            failure.kaliumException is KaliumException.InvalidRequestError
                        )
                            when {
                                failure.kaliumException.isTooManyClients() -> RegisterClientResult.Failure.TooManyClients
                                failure.kaliumException.isMissingAuth() -> RegisterClientResult.Failure.PasswordAuthRequired
                                failure.kaliumException.isInvalidCredentials() -> RegisterClientResult.Failure.InvalidCredentials
                                failure.kaliumException.isBadRequest() -> RegisterClientResult.Failure.InvalidCredentials
                                else -> RegisterClientResult.Failure.Generic(failure)
                            }
                        else RegisterClientResult.Failure.Generic(failure)
                    }, { client ->
                        RegisterClientResult.Success(client)
                    })
            })
        }

    // TODO(mls): when https://github.com/wireapp/core-crypto/issues/11 is implemented we
    // can remove registerMLSClient() and supply the MLS public key in registerClient().
    private suspend fun createMLSClient(client: Client): Either<CoreFailure, Client> =
        mlsClientProvider.getMLSClient(client.id)
            .flatMap { clientRepository.registerMLSClient(client.id, it.getPublicKey()) }
            .flatMap { keyPackageRepository.uploadNewKeyPackages(client.id, keyPackageLimitsProvider.refillAmount()) }
            .map { client }

    private suspend fun generateProteusPreKeys(
        preKeysToSend: Int,
        password: String?,
        capabilities: List<ClientCapability>?,
        clientType: ClientType? = null
    ) = preKeyRepository.generateNewPreKeys(FIRST_KEY_ID, preKeysToSend).flatMap { preKeys ->
        preKeyRepository.generateNewLastKey().flatMap { lastKey ->
            Either.Right(
                RegisterClientParam(
                    password = password,
                    capabilities = capabilities,
                    preKeys = preKeys,
                    lastKey = lastKey,
                    deviceType = null,
                    label = null,
                    model = null,
                    clientType = clientType
                )
            )
        }
    }
}
