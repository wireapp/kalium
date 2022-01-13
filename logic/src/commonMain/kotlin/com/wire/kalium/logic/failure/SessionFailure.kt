package com.wire.kalium.logic.failure

import com.wire.kalium.logic.CoreFailure

sealed class SessionFailure : CoreFailure.FeatureFailure() {
    object NoSessionFound: SessionFailure()
}
