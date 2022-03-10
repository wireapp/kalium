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

sealed class NetworkFailure(internal val kaliumException: KaliumException) : CoreFailure() {
    // exposed as Throwable to the app if needed for logging
    val cause: Throwable get() = kaliumException
    /**
     * Failed to establish a connection with the necessary servers in order to pull/push data.
     * Caused by weak - complete lack of - internet connection.
     */
    class NoNetworkConnection(kaliumException: KaliumException) : NetworkFailure(kaliumException)

    /**
     * Server internal error, or we can't parse the response,
     * or anything API-related that is out of control from the user.
     * Either fix our app or our backend.
     */
    class ServerMiscommunication(kaliumException: KaliumException) : NetworkFailure(kaliumException)
}

class ProteusFailure(internal val proteusException: ProteusException) : CoreFailure()

inline fun <T : Any> wrapApiRequest(networkCall: () -> NetworkResponse<T>): Either<NetworkFailure, T> {
    // TODO: check for internet connection and return NoNetworkConnection
    return when (val result = networkCall()) {
        is NetworkResponse.Success -> Either.Right(result.value)
        is NetworkResponse.Error -> when (result.kException) {
            is KaliumException.NetworkUnavailableError -> Either.Left(NetworkFailure.NoNetworkConnection(result.kException))
            else -> Either.Left(NetworkFailure.ServerMiscommunication(result.kException))
        }
    }
}

inline fun <T : Any> wrapCryptoRequest(cryptoRequest: () -> T): Either<ProteusFailure, T> {
    return try {
        Either.Right(cryptoRequest())
    } catch (e: ProteusException) {
        Either.Left(ProteusFailure(e))
    }
}
