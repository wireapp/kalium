package com.wire.kalium.logic.failure

import com.wire.kalium.logic.CoreFailure

sealed class ClientFailure : CoreFailure.FeatureFailure() {
    object WrongPassword: ClientFailure()
    object TooManyClients: ClientFailure()
}

