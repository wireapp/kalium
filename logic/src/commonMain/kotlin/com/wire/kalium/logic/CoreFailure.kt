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

class StorageFailure(val rootCause: Throwable) : CoreFailure()

inline fun <T : Any> wrapApiRequest(networkCall: () -> NetworkResponse<T>): Either<NetworkFailure, T> {
    // TODO: check for internet connection and return NoNetworkConnection
    return try {
        when (val result = networkCall()) {
            is NetworkResponse.Success -> Either.Right(result.value)
            is NetworkResponse.Error -> when (result.kException) {
                else -> Either.Left(NetworkFailure.ServerMiscommunication(result.kException))
            }
        }
    } catch (e: Exception) {
        Either.Left(NetworkFailure.ServerMiscommunication(e))
    }
}

inline fun <T : Any> wrapCryptoRequest(cryptoRequest: () -> T): Either<ProteusFailure, T> {
    return try {
        Either.Right(cryptoRequest())
    } catch (e: ProteusException) {
        Either.Left(ProteusFailure(e))
    } catch (e: Exception) {
        Either.Left(ProteusFailure(e))
    }
}

inline fun <T : Any> wrapStorageRequest(storageRequest: () -> T): Either<StorageFailure, T> {
    return try {
        Either.Right(storageRequest())
    } catch (e: Exception) {
        Either.Left(StorageFailure(e))
    }
}
