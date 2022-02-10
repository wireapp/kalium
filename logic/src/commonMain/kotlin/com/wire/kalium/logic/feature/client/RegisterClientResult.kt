package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.Client

sealed class RegisterClientResult {
    class Success(val client: Client) : RegisterClientResult()

    sealed class Failure : RegisterClientResult() {
        object InvalidCredentials : Failure()
        object TooManyClients : Failure()
        data class ProtuseFailure(val e: Throwable): Failure()
        class Generic(val genericFailure: CoreFailure) : Failure()
    }
}

