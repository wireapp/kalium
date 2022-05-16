package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.CoreFailure

sealed class GetServerConfigResult {
    // TODO(qol): change to return the id only so we are now passing the whole config object around in the app
    class Success(val serverConfig: ServerConfig): GetServerConfigResult()

    sealed class Failure : GetServerConfigResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
