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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.model.FederationErrorResponse
import com.wire.kalium.network.api.model.MLSErrorResponse
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.exceptions.FederationError
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.MLSError
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.coroutines.cancellation.CancellationException

const val SOCKS_EXCEPTION = "socks"

@Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
inline fun <T : Any> wrapApiRequest(networkCall: () -> NetworkResponse<T>): Either<NetworkFailure, T> =
    when (val result = networkCall()) {
        is NetworkResponse.Success -> Either.Right(result.value)
        is NetworkResponse.Error -> {
            kaliumLogger.e(result.kException.stackTraceToString())
            val exception = result.kException
            when {
                exception is FederationError -> {
                    when (val errorResponse = exception.errorResponse) {
                        is FederationErrorResponse.Conflict -> {
                            Either.Left(NetworkFailure.FederatedBackendFailure.ConflictingBackends(errorResponse.nonFederatingBackends))
                        }

                        is FederationErrorResponse.ConflictWithMissingUsers -> {
                            Either.Left(
                                NetworkFailure.FederatedBackendFailure.ConflictingBackendsWithMissingUsers(errorResponse.missingUsers)
                            )
                        }

                        is FederationErrorResponse.Unreachable -> {
                            Either.Left(NetworkFailure.FederatedBackendFailure.FailedDomains(errorResponse.unreachableBackends))
                        }

                        is FederationErrorResponse.Generic -> {
                            val failure = when {
                                errorResponse.isFederationDenied() ->
                                    NetworkFailure.FederatedBackendFailure.FederationDenied(errorResponse.label)

                                errorResponse.isFederationNotEnabled() ->
                                    NetworkFailure.FederatedBackendFailure.FederationNotEnabled(errorResponse.label)

                                errorResponse.isFederationNotImplemented() ->
                                    NetworkFailure.FederatedBackendFailure.FederationNotImplemented(errorResponse.label)

                                else ->
                                    NetworkFailure.FederatedBackendFailure.General(errorResponse.label)
                            }

                            Either.Left(failure)
                        }
                    }
                }

                exception is MLSError -> Either.Left(mapMLSError(exception))

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

fun mapMLSError(mlsError: MLSError): NetworkFailure.MlsMessageRejectedFailure = when (val body = mlsError.errorBody) {
    is MLSErrorResponse.ClientMismatch -> NetworkFailure.MlsMessageRejectedFailure.ClientMismatch
    is MLSErrorResponse.CommitMissingReferences -> NetworkFailure.MlsMessageRejectedFailure.CommitMissingReferences
    is MLSErrorResponse.GroupOutOfSync -> {
        val ids = body.missingUsers.map { user -> UserId(user.value, user.domain) }
        NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync(ids)
    }

    is MLSErrorResponse.InvalidLeafNodeIndex -> NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeIndex
    is MLSErrorResponse.InvalidLeafNodeSignature -> NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeSignature
    is MLSErrorResponse.StaleMessage -> NetworkFailure.MlsMessageRejectedFailure.StaleMessage
    is MLSErrorResponse.MissingGroupInfo -> NetworkFailure.MlsMessageRejectedFailure.MissingGroupInfo
    is MLSErrorResponse.UnsupportedProposal,
    is MLSErrorResponse.ClientSenderUserMismatch,
    is MLSErrorResponse.GroupConversationMismatch,
    is MLSErrorResponse.NotEnabled,
    is MLSErrorResponse.ProposalNotFound,
    is MLSErrorResponse.ProtocolError,
    is MLSErrorResponse.SelfRemovalNotAllowed,
    is MLSErrorResponse.SubconversationJoinParentMissing,
    is MLSErrorResponse.UnsupportedMessage,
    is MLSErrorResponse.WelcomeMismatch -> NetworkFailure.MlsMessageRejectedFailure.Other(body)
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
    } catch (e: CommonizedMLSException) {
        kaliumLogger.e(
            """{ "MLSException": "${e.message},"
                |"cause": ${e.cause} }",
                |"stackTrace": ${e.stackTraceToString()} """.trimMargin()
        )
        Either.Left(e.failure)
    } catch (e: Exception) {
        kaliumLogger.e(
            """{ "MLSException": "${e.message},"
                |"cause": ${e.cause} }",
                |"stackTrace": ${e.stackTraceToString()} """.trimMargin()
        )
        Either.Left(commonizeMLSException(e).failure)
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

/**
 * Converts a given platform-specific exception to a `CommonizedMLSException`
 * that consistently represents errors in MLS operations across all platforms.
 *
 * @param exception The platform-specific exception that occurred during an MLS operation.
 * @return A `CommonizedMLSException` representing the provided exception.
 */
expect fun commonizeMLSException(exception: Exception): CommonizedMLSException
