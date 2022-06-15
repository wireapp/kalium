package com.wire.kalium.logic.failure

import com.wire.kalium.logic.CoreFailure

sealed class ServerConfigFailure: CoreFailure.FeatureFailure() {
    object UnknownServerVersion: ServerConfigFailure()
    object NewServerVersion: ServerConfigFailure()
}

