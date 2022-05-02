package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.client.RegisterClientParam
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.feature.client.RegisterClientUseCase.Companion.FIRST_KEY_ID
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.api.user.pushToken.PushTokenBody
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMissingAuth
import com.wire.kalium.network.exceptions.isTooManyClients

sealed class RegisterClientResult {
    class Success(val client: Client) : RegisterClientResult()

    sealed class Failure : RegisterClientResult() {
        object InvalidCredentials : Failure()
        object TooManyClients : Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

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
     *
     * Sender ID : used only when we have a generated token that is related to the notifications
     * in the case of android it represent the firebase project number
     *
     * @see [RegisterClientParam.ClientWithoutToken]
     * @see [RegisterClientParam.ClientWithToken]
     */
    sealed class RegisterClientParam {
        abstract val password: String?
        abstract val capabilities: List<ClientCapability>?
        abstract val preKeysToSend: Int

        data class ClientWithoutToken(
            override val password: String?,
            override val capabilities: List<ClientCapability>?,
            override val preKeysToSend: Int = DEFAULT_PRE_KEYS_COUNT
        ) : RegisterClientParam()

        data class ClientWithToken(
            override val password: String?,
            override val capabilities: List<ClientCapability>?,
            val senderId: String,
            override val preKeysToSend: Int = DEFAULT_PRE_KEYS_COUNT
        ) : RegisterClientParam()

    }

    companion object {
        const val FIRST_KEY_ID = 0
        const val DEFAULT_PRE_KEYS_COUNT = 100
    }
}

class RegisterClientUseCaseImpl(
    private val clientRepository: ClientRepository,
    private val preKeyRepository: PreKeyRepository,
    private val keyPackageRepository: KeyPackageRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val notificationTokenRepository: NotificationTokenRepository
) : RegisterClientUseCase {

    override suspend operator fun invoke(registerClientParam: RegisterClientUseCase.RegisterClientParam): RegisterClientResult =
        suspending {
            with(registerClientParam) {
                generateProteusPreKeys(preKeysToSend, password, capabilities).coFold({
                    RegisterClientResult.Failure.Generic(it)
                }, { registerClientParam ->
                    clientRepository.registerClient(registerClientParam).flatMap { client ->
                        createMLSClient(client)
                    }.flatMap { client ->
                        if (this is RegisterClientUseCase.RegisterClientParam.ClientWithToken) {
                            registerToken(client, senderId)
                        } else {
                            Either.Right(client)
                        }
                    }.fold({ failure ->
                        if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError)
                            when {
                                failure.kaliumException.isTooManyClients() -> RegisterClientResult.Failure.TooManyClients
                                failure.kaliumException.isMissingAuth() -> RegisterClientResult.Failure.InvalidCredentials
                                else -> RegisterClientResult.Failure.Generic(failure)
                            }
                        else RegisterClientResult.Failure.Generic(failure)
                    }, { client ->
                        RegisterClientResult.Success(client)
                    })
                })
            }
        }

    /**
     * to save the generated token that is related to the notifications , in the android case it's firebase token
     */
    private suspend fun registerToken(client: Client, senderId: String): Either<CoreFailure, Client> = suspending {
        notificationTokenRepository.getNotificationToken().flatMap { notificationToken ->
            clientRepository.registerToken(
                PushTokenBody(
                    senderId = senderId,
                    client = client.clientId.value,
                    token = notificationToken.token,
                    transport = notificationToken.transport
                )
            ).map { client }
        }
    }

    private suspend fun createMLSClient(client: Client): Either<CoreFailure, Client> = suspending {
        // TODO when https://github.com/wireapp/core-crypto/issues/11 is implemented we
        // can remove registerMLSClient() and supply the MLS public key in registerClient().
        mlsClientProvider.getMLSClient(client.clientId)
            .flatMap { clientRepository.registerMLSClient(client.clientId, it.getPublicKey()) }
            .flatMap { keyPackageRepository.uploadNewKeyPackages(client.clientId) }
            .flatMap { clientRepository.persistClientId(client.clientId) }
            .map { client }
    }

    private suspend fun generateProteusPreKeys(
        preKeysToSend: Int,
        password: String?,
        capabilities: List<ClientCapability>?
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
                    model = null
                )
            )
        }
    }
}
