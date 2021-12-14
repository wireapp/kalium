package com.wire.kalium.logic

sealed class GenericFailure {
    /**
     * Failed to establish a connection with the necessary servers in order to pull/push data.
     * Caused by weak - complete lack of - internet connection.
     */
    object NoNetworkConnection : GenericFailure()

    /**
     * Server internal error, or we can't parse the response,
     * or anything API-related that is out of control from the user.
     * Either fix our app or our backend.
     */
    object ServerMiscommunication : GenericFailure()

    class UnknownFailure(val rootCause: Throwable?): GenericFailure()

}
