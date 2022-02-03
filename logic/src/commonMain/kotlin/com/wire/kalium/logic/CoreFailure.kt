package com.wire.kalium.logic

sealed class CoreFailure {
    /**
     * Failed to establish a connection with the necessary servers in order to pull/push data.
     * Caused by weak - complete lack of - internet connection.
     */
    object NoNetworkConnection : CoreFailure()

    /**
     * Server internal error, or we can't parse the response,
     * or anything API-related that is out of control from the user.
     * Either fix our app or our backend.
     */
    object ServerMiscommunication : CoreFailure()

    /**
     * The attempted operation requires that this client is registered.
     */
    object MissingClientRegistration : CoreFailure()

    class Unknown(val rootCause: Throwable?) : CoreFailure()

    abstract class FeatureFailure : CoreFailure()
}
