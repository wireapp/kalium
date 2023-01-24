package com.wire.kalium.logic

import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

sealed class CoreFailure {

    /**
     * The attempted operation requires that this client is registered.
     */
    object MissingClientRegistration : CoreFailure()

    /**
     * A user has no key packages available which prevents him/her from being added
     * to an existing or new conversation.
     */
    data class NoKeyPackagesAvailable(val userId: UserId) : CoreFailure()

    /**
     * It's not allowed to run the application with development API enabled when
     * connecting to the production environment.
     */
    object DevelopmentAPINotAllowedOnProduction : CoreFailure()

    data class Unknown(val rootCause: Throwable?) : CoreFailure()

    abstract class FeatureFailure : CoreFailure()
}

sealed class NetworkFailure : CoreFailure() {
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
}

class MLSFailure(internal val exception: Exception) : CoreFailure() {

    val rootCause: Throwable get() = exception
}

class ProteusFailure(internal val proteusException: ProteusException) : CoreFailure() {

    val rootCause: Throwable get() = proteusException
}

sealed class EncryptionFailure : CoreFailure.FeatureFailure() {
    object GenericEncryptionError : EncryptionFailure()
    object GenericDecryptionError : EncryptionFailure()
    object WrongAssetHash : EncryptionFailure()
}

sealed class StorageFailure : CoreFailure() {
    object DataNotFound : StorageFailure()
    class Generic(val rootCause: Throwable) : StorageFailure()
}

private const val SOCKS_EXCEPTION = "socks"

internal inline fun <T : Any> wrapApiRequest(networkCall: () -> NetworkResponse<T>): Either<NetworkFailure, T> =
    when (val result = networkCall()) {
        is NetworkResponse.Success -> Either.Right(result.value)
        is NetworkResponse.Error -> {
            kaliumLogger.e(result.kException.stackTraceToString())
            val exception = result.kException
            // todo SocketException is platform specific so need to wrap it in our own exceptions
            if (exception.cause?.message?.contains(SOCKS_EXCEPTION, true) == true) {
                Either.Left(NetworkFailure.ProxyError(exception.cause))
            } else {
                if (exception is KaliumException.GenericError && exception.cause is IOException) {
                    Either.Left(NetworkFailure.NoNetworkConnection(exception))
                } else {
                    Either.Left(NetworkFailure.ServerMiscommunication(result.kException))
                }
            }
        }
    }

internal inline fun <T : Any> wrapCryptoRequest(cryptoRequest: () -> T): Either<ProteusFailure, T> {
    return try {
        Either.Right(cryptoRequest())
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
        kaliumLogger.e(e.stackTraceToString())
        Either.Left(MLSFailure(e))
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
