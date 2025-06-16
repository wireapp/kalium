/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
@file:Suppress("TooGenericExceptionCaught")
package com.wire.kalium.common.error

import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.exceptions.ProteusException
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
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.cancellation.CancellationException

const val SOCKS_EXCEPTION = "socks"

inline fun <T : Any> wrapApiRequest(networkCall: () -> NetworkResponse<T>): Either<NetworkFailure, T> =
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

inline fun <T : Any> wrapProteusRequest(proteusRequest: () -> T): Either<ProteusFailure, T> {
    return try {
        Either.Right(proteusRequest())
    } catch (e: ProteusException) {
        kaliumLogger.e(
            """{ "ProteusException": "${e.message},"
                |"cause": ${e.cause} }",
                |"stackTrace": ${e.stackTraceToString()} """.trimMargin()
        )
        Either.Left(ProteusFailure(e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        kaliumLogger.e(
            """{ "ProteusException": "${e.message},"
                |"cause": ${e.cause} },
                |"stackTrace": ${e.stackTraceToString()} """.trimMargin()
        )
        Either.Left(ProteusFailure(ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR, null, e.cause)))
    }
}

inline fun <T> wrapMLSRequest(mlsRequest: () -> T): Either<MLSFailure, T> {
    return try {
        Either.Right(mlsRequest())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        kaliumLogger.e(
            """{ "MLSException": "${e.message},"
                |"cause": ${e.cause} }",
                |"stackTrace": ${e.stackTraceToString()} """.trimMargin()
        )
        Either.Left(mapMLSException(e))
    }
}

inline fun wrapMLSException(e: Throwable): MLSFailure {
    return if(e is Exception) {
        kaliumLogger.e(
            """{ "MLSException": "${e.message},"
                |"cause": ${e.cause} }",
                |"stackTrace": ${e.stackTraceToString()} """.trimMargin()
        )
        mapMLSException(e)
    } else {
        throw e
    }
}

inline fun <T> wrapE2EIRequest(e2eiRequest: () -> T): Either<E2EIFailure, T> {
    return try {
        Either.Right(e2eiRequest())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        kaliumLogger.e(
            """{ "E2EIException": "${e.message},"
                |"cause": ${e.cause} }",
                |"stackTrace": ${e.stackTraceToString()} """.trimMargin()
        )
        Either.Left(E2EIFailure.Generic(e))
    }
}

inline fun <T : Any> wrapStorageRequest(storageRequest: () -> T?): Either<StorageFailure, T> {
    val result = try {
        storageRequest()?.let { data -> Either.Right(data) } ?: Either.Left(StorageFailure.DataNotFound)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Either.Left(StorageFailure.Generic(e))
    }
    result.onFailure { storageFailure -> kaliumLogger.e(storageFailure.toString()) }
    return result
}

/**
 * Wrap a storage request with a custom error handler that let's delegate the error handling to the caller.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T : Any> wrapStorageRequest(
    noinline errorHandler: (Exception) -> Either<StorageFailure, T>,
    storageRequest: () -> T?
): Either<StorageFailure, T> {
    return try {
        storageRequest()?.let { data -> Either.Right(data) } ?: Either.Left(StorageFailure.DataNotFound)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        errorHandler(e)
    }
}

inline fun <T : Any> wrapStorageNullableRequest(storageRequest: () -> T?): Either<StorageFailure, T?> {
    return try {
        storageRequest().let { data -> Either.Right(data) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        kaliumLogger.e(e.stackTraceToString())
        Either.Left(StorageFailure.Generic(e))
    }
}

fun <T : Any> Flow<T?>.wrapStorageRequest(): Flow<Either<StorageFailure, T>> =
    this.map {
        it?.let { data -> Either.Right(data) } ?: Either.Left<StorageFailure>(StorageFailure.DataNotFound)
    }.catch { e ->
        if (e is CancellationException) {
            throw e
        }
        emit(Either.Left(StorageFailure.Generic(e)))
    }.onEach {
        it.onFailure { storageFailure -> kaliumLogger.e(storageFailure.toString()) }
    }

inline fun <T : Any> wrapFlowStorageRequest(storageRequest: () -> Flow<T?>): Flow<Either<StorageFailure, T>> {
    return try {
        storageRequest().map {
            it?.let { data -> Either.Right(data) } ?: Either.Left<StorageFailure>(StorageFailure.DataNotFound)
        }.catch { e ->
            if (e is CancellationException) {
                throw e
            }
            emit(Either.Left(StorageFailure.Generic(e)))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        flowOf(Either.Left(StorageFailure.Generic(e)))
    }.onEach {
        it.onFailure { storageFailure -> kaliumLogger.e(storageFailure.toString()) }
    }
}

inline fun <T : Any> wrapNullableFlowStorageRequest(storageRequest: () -> Flow<T?>): Flow<Either<StorageFailure, T?>> {
    return try {
        storageRequest().map {
            Either.Right(it) as Either<StorageFailure, T?>
        }.catch { e ->
            if (e is CancellationException) {
                throw e
            }
            emit(Either.Left<StorageFailure>(StorageFailure.Generic(e)))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        flowOf(Either.Left<StorageFailure>(StorageFailure.Generic(e)))
    }.onEach {
        it.onFailure { storageFailure -> kaliumLogger.e(storageFailure.toString()) }
    }
}

// TODO: Handle all cases explicitly.
//       Blocked by https://github.com/wireapp/core-crypto/pull/214
expect fun mapMLSException(exception: Exception): MLSFailure
