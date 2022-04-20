package com.wire.kalium.logic

import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse

sealed class CoreFailure {

    /**
     * The attempted operation requires that this client is registered.
     */
    object MissingClientRegistration : CoreFailure()

    data class Unknown(val rootCause: Throwable?) : CoreFailure()

    abstract class FeatureFailure : CoreFailure()
}

sealed class NetworkFailure : CoreFailure() {
    /**
     * Failed to establish a connection with the necessary servers in order to pull/push data.
     * Caused by weak - complete lack of - internet connection.
     */
    object NoNetworkConnection : NetworkFailure()

    /**
     * Server internal error, or we can't parse the response,
     * or anything API-related that is out of control from the user.
     * Either fix our app or our backend.
     */
    class ServerMiscommunication(internal val kaliumException: KaliumException) : NetworkFailure() {
        constructor(cause: Throwable) : this(KaliumException.GenericError(cause))

        val rootCause: Throwable get() = kaliumException
    }
}

class ProteusFailure(internal val proteusException: ProteusException) : CoreFailure() {
    constructor(cause: Throwable) : this(ProteusException("Unknown error caught from logic", code = ProteusException.Code.UNKNOWN_ERROR))

    val rootCause: Throwable get() = proteusException
}

sealed class StorageFailure : CoreFailure() {
    object DataNotFound : StorageFailure()
    class Generic(val rootCause: Throwable) : StorageFailure()
}

internal inline fun <T : Any> wrapApiRequest(networkCall: () -> NetworkResponse<T>): Either<NetworkFailure, T> {
    // TODO: check for internet connection and return NoNetworkConnection
    return try {
        when (val result = networkCall()) {
            is NetworkResponse.Success -> Either.Right(result.value)
            is NetworkResponse.Error -> {
                kaliumLogger.e(result.kException.stackTraceToString())
                Either.Left(NetworkFailure.ServerMiscommunication(result.kException))
            }
        }
    } catch (e: Exception) {
        kaliumLogger.e(e.stackTraceToString())
        Either.Left(NetworkFailure.ServerMiscommunication(e))
    }
}

internal inline fun <T : Any> wrapCryptoRequest(cryptoRequest: () -> T): Either<ProteusFailure, T> {
    return try {
        Either.Right(cryptoRequest())
    } catch (e: ProteusException) {
        kaliumLogger.e(e.stackTraceToString())
        Either.Left(ProteusFailure(e))
    } catch (e: Exception) {
        kaliumLogger.e(e.stackTraceToString())
        Either.Left(ProteusFailure(e))
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
