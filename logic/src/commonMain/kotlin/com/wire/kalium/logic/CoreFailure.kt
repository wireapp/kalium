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

package com.wire.kalium.logic

import com.wire.crypto.CryptoException
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.exceptions.KaliumException
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

    /**
     * The attempted operation requires that this client is registered.
     */
    object MissingClientRegistration : CoreFailure

    /**
     * A user has no key packages available which prevents him/her from being added
     * to an existing or new conversation.
     */
    data class NoKeyPackagesAvailable(val userId: UserId) : CoreFailure

    /**
     * It's not allowed to run the application with development API enabled when
     * connecting to the production environment.
     */
    object DevelopmentAPINotAllowedOnProduction : CoreFailure

    data class Unknown(val rootCause: Throwable?) : CoreFailure

    abstract class FeatureFailure : CoreFailure

    /**
     * It's only allowed to insert system messages as bulk for all conversations.
     */
    object OnlySystemMessageAllowed : FeatureFailure()

    /**
     * The sender ID of the event is invalid.
     * usually happens with events that alter a message state [ButtonActionConfirmation]
     * when the sender ID is not the same are the original message sender id
     */
    object InvalidEventSenderID : FeatureFailure()
    /**
     * This operation is not supported by proteus conversations
     */
    object NotSupportedByProteus : FeatureFailure()
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

        data class General(val label: String) : FederatedBackendFailure()

        data class ConflictingBackends(val domains: List<String>) : FederatedBackendFailure()

        data class FailedDomains(val domains: List<String> = emptyList()) : FederatedBackendFailure()

    }

    /**
     * Failure due to a feature not supported by the current client/backend.
     */
    object FeatureNotSupported : NetworkFailure()
}

interface MLSFailure : CoreFailure {

    object WrongEpoch : MLSFailure

    object ConversationDoesNotSupportMLS : MLSFailure

    class Generic(internal val exception: Exception) : MLSFailure {
        val rootCause: Throwable get() = exception
    }
}

class E2EIFailure(internal val exception: Exception) : CoreFailure {

    val rootCause: Throwable get() = exception
}

class ProteusFailure(internal val proteusException: ProteusException) : CoreFailure {

    val rootCause: Throwable get() = proteusException
}

sealed class EncryptionFailure : CoreFailure.FeatureFailure() {
    object GenericEncryptionError : EncryptionFailure()
    object GenericDecryptionError : EncryptionFailure()
    object WrongAssetHash : EncryptionFailure()
}

sealed class StorageFailure : CoreFailure {
    object DataNotFound : StorageFailure()
    data class Generic(val rootCause: Throwable) : StorageFailure()
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
                    val cause = exception.errorResponse.cause
                    if (exception.errorResponse.label == "federation-unreachable-domains-error") {
                        Either.Left(NetworkFailure.FederatedBackendFailure.FailedDomains(cause?.domains.orEmpty()))
                    } else {
                        Either.Left(NetworkFailure.FederatedBackendFailure.General(exception.errorResponse.label))
                    }
                }

                exception is KaliumException.FederationConflictException -> {
                    Either.Left(NetworkFailure.FederatedBackendFailure.ConflictingBackends(exception.errorResponse.nonFederatingBackends))
                }

                // todo SocketException is platform specific so need to wrap it in our own exceptions
                exception.cause?.message?.contains(SOCKS_EXCEPTION, true) == true -> {
                    Either.Left(NetworkFailure.ProxyError(exception.cause))
                }

                exception is APINotSupported -> {
                    Either.Left(NetworkFailure.FeatureNotSupported)
                }

                else -> {
                    if (exception is KaliumException.GenericError && exception.cause is IOException) {
                        Either.Left(NetworkFailure.NoNetworkConnection(exception))
                    } else {
                        Either.Left(NetworkFailure.ServerMiscommunication(result.kException))
                    }
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
    } catch (cryptoException: CryptoException) {
        kaliumLogger.e(cryptoException.stackTraceToString())
        val mappedFailure = when (cryptoException) {
            is CryptoException.WrongEpoch -> MLSFailure.WrongEpoch
            // TODO: Handle all cases explicitly.
            //       Blocked by https://github.com/wireapp/core-crypto/pull/214
            else -> MLSFailure.Generic(cryptoException)
        }
        Either.Left(mappedFailure)
    } catch (e: Exception) {
        kaliumLogger.e(e.stackTraceToString())
        Either.Left(MLSFailure.Generic(e))
    }
}

internal inline fun <T> wrapE2EIRequest(e2eiRequest: () -> T): Either<E2EIFailure, T> {
    return try {
        Either.Right(e2eiRequest())
    } catch (e: Exception) {
        kaliumLogger.e(e.stackTraceToString())
        Either.Left(E2EIFailure(e))
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
