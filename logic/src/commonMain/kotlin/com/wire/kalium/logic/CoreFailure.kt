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

package com.wire.kalium.logic

import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isFederationDenied
import com.wire.kalium.network.exceptions.isFederationNotEnabled
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

sealed interface CoreFailure {

    val isInvalidRequestError: Boolean
        get() = this is NetworkFailure.ServerMiscommunication
                && this.kaliumException is KaliumException.InvalidRequestError

    val hasUnreachableDomainsError: Boolean
        get() = this is NetworkFailure.FederatedBackendFailure.FailedDomains && this.domains.isNotEmpty()

    val hasConflictingDomainsError: Boolean
        get() = this is NetworkFailure.FederatedBackendFailure.ConflictingBackends && this.domains.isNotEmpty()

    val isRetryable: Boolean
        get() = this is NetworkFailure.FederatedBackendFailure.RetryableFailure

    /**
     * The attempted operation requires that this client is registered.
     */
    data object MissingClientRegistration : CoreFailure

    /**
     * Key packages requested not available which prevents them from being added
     * to an existing or new conversation.
     */
    data class NoKeyPackagesAvailable(val failedUserIds: Set<UserId>) : CoreFailure

    /**
     * It's not allowed to run the application with development API enabled when
     * connecting to the production environment.
     */
    data object DevelopmentAPINotAllowedOnProduction : CoreFailure

    data class Unknown(val rootCause: Throwable?) : CoreFailure

    abstract class FeatureFailure : CoreFailure

    /**
     * It's only allowed to insert system messages as bulk for all conversations.
     */
    data object OnlySystemMessageAllowed : FeatureFailure()

    /**
     * The sender ID of the event is invalid.
     * usually happens with events that alter a message state [ButtonActionConfirmation]
     * when the sender ID is not the same are the original message sender id
     */
    data object InvalidEventSenderID : FeatureFailure()

    /**
     * This operation is not supported by proteus conversations
     */
    data object NotSupportedByProteus : FeatureFailure()

    /**
     * The desired event was not found when fetching pending events.
     * This can happen when this client has been offline for a long period of time,
     * and the backend has deleted old events.
     *
     * This is a recoverable error, the client should:
     * - Do a full slow sync
     * - Try incremental sync again using the oldest event ID available in the backend
     * - Warn the user that some events might have been missed.
     *
     * This could also mean that the client was deleted. In this case, SlowSync will fail.
     * The client should identify this scenario through other means and logout.
     */
    data object SyncEventOrClientNotFound : FeatureFailure()

    /**
     * No common Protocol found in order to establish a conversation between parties.
     * Could be, for example, that the desired user only supports Proteus, but we only support MLS.
     */
    data object NoCommonProtocolFound : FeatureFailure()
}

sealed class NetworkFailure : CoreFailure {
    /**
     * Failed to establish a connection with the necessary servers in order to pull/push data.
     *
     * Caused by weak – or complete lack of – internet connection:
     * - Device not connected at all
     * - Timeout due to slow connection
     * - Server is offline
     * - Unreachable due to ISP blocks
     * - _many_ others, we just can't say specifically, and we handle them all the same way
     *
     * [cause] can help to understand better what was caused and triggered this failure
     */
    class NoNetworkConnection(val cause: Throwable?) : NetworkFailure()

    class ProxyError(val cause: Throwable?) : NetworkFailure()

    /**
     * Server internal error, or we can't parse the response,
     * or anything API-related that is out of control from the user.
     * Either fix our app or our backend.
     */
    class ServerMiscommunication(internal val kaliumException: KaliumException) : NetworkFailure() {
        constructor(cause: Throwable) : this(KaliumException.GenericError(cause))

        val rootCause: Throwable get() = kaliumException

        override fun toString(): String {
            return "ServerMiscommunication(cause = $rootCause)"
        }

    }

    /**
     * Failure due to a federated backend context
     */
    sealed class FederatedBackendFailure : NetworkFailure() {

        /**
         * Failure due to a federated backend context that can be retried
         */
        interface RetryableFailure {
            val domains: List<String>
        }

        data class General(val label: String) : FederatedBackendFailure()
        data class FederationDenied(val label: String) : FederatedBackendFailure()
        data class FederationNotEnabled(val label: String) : FederatedBackendFailure()

        data class ConflictingBackends(override val domains: List<String>) : FederatedBackendFailure(), RetryableFailure

        data class FailedDomains(override val domains: List<String> = emptyList()) : FederatedBackendFailure(), RetryableFailure

    }

    /**
     * Failure due to a feature not supported by the current client/backend.
     */
    data object FeatureNotSupported : NetworkFailure()
}

interface MLSFailure : CoreFailure {

    data object WrongEpoch : MLSFailure

    data object DuplicateMessage : MLSFailure

    data object BufferedFutureMessage : MLSFailure

    data object SelfCommitIgnored : MLSFailure

    data object UnmergedPendingGroup : MLSFailure

    data object ConversationAlreadyExists : MLSFailure

    data object ConversationDoesNotSupportMLS : MLSFailure
    data object StaleProposal : MLSFailure
    data object StaleCommit : MLSFailure

    class Generic(internal val exception: Exception) : MLSFailure {
        val rootCause: Throwable get() = exception
    }
}

interface E2EIFailure : CoreFailure {
    data object Disabled : E2EIFailure
    data object MissingDiscoveryUrl : E2EIFailure
    data class MissingMLSClient(internal val reason: CoreFailure) : E2EIFailure
    data class MissingE2EIClient(internal val reason: CoreFailure) : E2EIFailure
    data object MissingTeamSettings : E2EIFailure
    data class GettingE2EIClient(internal val reason: CoreFailure) : E2EIFailure
    data class TrustAnchors(internal val reason: CoreFailure) : E2EIFailure
    data class IntermediateCert(internal val reason: CoreFailure) : E2EIFailure
    data class CRL(internal val reason: CoreFailure) : E2EIFailure
    data class OAuthRefreshToken(internal val reason: CoreFailure) : E2EIFailure
    data class AcmeNonce(internal val reason: CoreFailure) : E2EIFailure
    data class AcmeNewAccount(internal val reason: CoreFailure) : E2EIFailure
    data class AcmeDirectories(internal val reason: CoreFailure) : E2EIFailure
    data class AcmeNewOrder(internal val reason: CoreFailure) : E2EIFailure
    data object AcmeAuthorizations : E2EIFailure
    data class OAuth(val reason: String) : E2EIFailure
    data class WireNonce(internal val reason: CoreFailure) : E2EIFailure
    data class DPoPToken(internal val reason: CoreFailure) : E2EIFailure
    data class WireAccessToken(internal val reason: CoreFailure) : E2EIFailure
    data class DPoPChallenge(internal val reason: CoreFailure) : E2EIFailure
    data class OIDCChallenge(internal val reason: CoreFailure) : E2EIFailure
    data class CheckOrderRequest(internal val reason: CoreFailure) : E2EIFailure
    data class FinalizeRequest(internal val reason: CoreFailure) : E2EIFailure
    data class RotationAndMigration(internal val reason: CoreFailure) : E2EIFailure
    data class InitMLSClient(internal val reason: CoreFailure) : E2EIFailure
    data class Certificate(internal val reason: CoreFailure) : E2EIFailure
    class Generic(internal val exception: Exception) : E2EIFailure {
        val rootCause: Throwable get() = exception
    }
}

class ProteusFailure(internal val proteusException: ProteusException) : CoreFailure {

    val rootCause: Throwable get() = proteusException
}

sealed class EncryptionFailure : CoreFailure.FeatureFailure() {
    data object GenericEncryptionError : EncryptionFailure()
    data object GenericDecryptionError : EncryptionFailure()
    data object WrongAssetHash : EncryptionFailure()
}

sealed class StorageFailure : CoreFailure {
    data object DataNotFound : StorageFailure()
    data class Generic(val rootCause: Throwable) : StorageFailure() {
        override fun toString(): String {
            return "Generic(rootCause = ${rootCause.stackTraceToString()})"
        }
    }
}

private const val SOCKS_EXCEPTION = "socks"

internal inline fun <T : Any> wrapApiRequest(networkCall: () -> NetworkResponse<T>): Either<NetworkFailure, T> =
    when (val result = networkCall()) {
        is NetworkResponse.Success -> Either.Right(result.value)
        is NetworkResponse.Error -> {
            kaliumLogger.e(result.kException.stackTraceToString())
            val exception = result.kException
            when {
                exception is KaliumException.FederationError -> {
                    if (exception.isFederationDenied()) {
                        Either.Left(NetworkFailure.FederatedBackendFailure.FederationDenied(exception.errorResponse.label))
                    } else if (exception.isFederationNotEnabled()) {
                        Either.Left(NetworkFailure.FederatedBackendFailure.FederationNotEnabled(exception.errorResponse.label))
                    } else {
                        Either.Left(NetworkFailure.FederatedBackendFailure.General(exception.errorResponse.label))
                    }
                }

                exception is KaliumException.FederationUnreachableException -> {
                    Either.Left(NetworkFailure.FederatedBackendFailure.FailedDomains(exception.errorResponse.unreachableBackends))
                }

                exception is KaliumException.FederationConflictException -> {
                    Either.Left(NetworkFailure.FederatedBackendFailure.ConflictingBackends(exception.errorResponse.nonFederatingBackends))
                }

                // todo SocketException is platform specific so need to wrap it in our own exceptions
                exception.cause?.message?.contains(SOCKS_EXCEPTION, true) == true -> {
                    Either.Left(NetworkFailure.ProxyError(exception.cause))
                }

                exception is KaliumException.NoNetwork -> {
                    Either.Left(NetworkFailure.NoNetworkConnection(exception))
                }

                exception is KaliumException.GenericError && exception.cause is IOException -> {
                    Either.Left(NetworkFailure.NoNetworkConnection(exception))
                }

                exception is APINotSupported -> {
                    Either.Left(NetworkFailure.FeatureNotSupported)
                }

                else -> {
                    Either.Left(NetworkFailure.ServerMiscommunication(result.kException))
                }
            }
        }
    }

internal inline fun <T : Any> wrapProteusRequest(proteusRequest: () -> T): Either<ProteusFailure, T> {
    return try {
        Either.Right(proteusRequest())
    } catch (e: ProteusException) {
        kaliumLogger.e(
            """{ "ProteusException": "${e.message},"
                |"cause": ${e.cause} }" """.trimMargin()
        )
        kaliumLogger.e(e.stackTraceToString())
        Either.Left(ProteusFailure(e))
    } catch (e: Exception) {
        kaliumLogger.e(
            """{ "ProteusException": "${e.message},"
                |"cause": ${e.cause} }" """.trimMargin()
        )
        kaliumLogger.e(e.stackTraceToString())
        Either.Left(ProteusFailure(ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, e.cause)))
    }
}

internal inline fun <T> wrapMLSRequest(mlsRequest: () -> T): Either<MLSFailure, T> {
    return try {
        Either.Right(mlsRequest())
    } catch (e: Exception) {
        kaliumLogger.e(
            """{ "MLSException": "${e.message},"
                |"cause": ${e.cause} }" """.trimMargin()
        )
        kaliumLogger.e(e.stackTraceToString())
        Either.Left(mapMLSException(e))
    }
}

internal inline fun <T> wrapE2EIRequest(e2eiRequest: () -> T): Either<E2EIFailure, T> {
    return try {
        Either.Right(e2eiRequest())
    } catch (e: Exception) {
        kaliumLogger.e(
            """{ "E2EIException": "${e.message},"
                |"cause": ${e.cause} }" """.trimMargin()
        )
        kaliumLogger.e(e.stackTraceToString())
        Either.Left(E2EIFailure.Generic(e))
    }
}

internal inline fun <T : Any> wrapStorageRequest(storageRequest: () -> T?): Either<StorageFailure, T> {
    return try {
        storageRequest()?.let { data -> Either.Right(data) } ?: Either.Left(StorageFailure.DataNotFound)
    } catch (e: Exception) {
        kaliumLogger.e(e.stackTraceToString())
        Either.Left(StorageFailure.Generic(e))
    }
}

/**
 * Wrap a storage request with a custom error handler that let's delegate the error handling to the caller.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun <T : Any> wrapStorageRequest(
    noinline errorHandler: (Exception) -> Either<StorageFailure, T>,
    storageRequest: () -> T?
): Either<StorageFailure, T> {
    return try {
        storageRequest()?.let { data -> Either.Right(data) } ?: Either.Left(StorageFailure.DataNotFound)
    } catch (exception: Exception) {
        errorHandler(exception)
    }
}

internal inline fun <T : Any> wrapStorageNullableRequest(storageRequest: () -> T?): Either<StorageFailure, T?> {
    return try {
        storageRequest().let { data -> Either.Right(data) }
    } catch (e: Exception) {
        kaliumLogger.e(e.stackTraceToString())
        Either.Left(StorageFailure.Generic(e))
    }
}

internal fun <T : Any> Flow<T?>.wrapStorageRequest(): Flow<Either<StorageFailure, T>> =
    this.map {
        it?.let { data -> Either.Right(data) } ?: Either.Left<StorageFailure>(StorageFailure.DataNotFound)
    }.catch { e ->
        kaliumLogger.e(e.stackTraceToString())
        emit(Either.Left(StorageFailure.Generic(e)))
    }

internal inline fun <T : Any> wrapFlowStorageRequest(storageRequest: () -> Flow<T?>): Flow<Either<StorageFailure, T>> {
    return try {
        storageRequest().map {
            it?.let { data -> Either.Right(data) } ?: Either.Left<StorageFailure>(StorageFailure.DataNotFound)
        }.catch { e ->
            kaliumLogger.e(e.stackTraceToString())
            emit(Either.Left(StorageFailure.Generic(e)))
        }
    } catch (e: Exception) {
        kaliumLogger.e(e.stackTraceToString())
        flowOf(Either.Left(StorageFailure.Generic(e)))
    }
}

internal inline fun <T : Any> wrapNullableFlowStorageRequest(storageRequest: () -> Flow<T?>): Flow<Either<StorageFailure, T?>> {
    return try {
        storageRequest().map {
            Either.Right(it) as Either<StorageFailure, T?>
        }.catch { e ->
            kaliumLogger.e(e.stackTraceToString())
            emit(Either.Left(StorageFailure.Generic(e)))
        }
    } catch (e: Exception) {
        kaliumLogger.e(e.stackTraceToString())
        flowOf(Either.Left(StorageFailure.Generic(e)))
    }
}

// TODO: Handle all cases explicitly.
//       Blocked by https://github.com/wireapp/core-crypto/pull/214
expect fun mapMLSException(exception: Exception): MLSFailure
