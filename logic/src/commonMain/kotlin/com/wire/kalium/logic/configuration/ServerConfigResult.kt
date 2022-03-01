package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.CoreFailure

sealed class GetServerConfigResult {
    class Success(val serverConfig: ServerConfig): GetServerConfigResult()

    sealed class Failure : GetServerConfigResult() {
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}
