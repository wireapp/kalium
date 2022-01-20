package com.wire.kalium.logic.failure

import com.wire.kalium.logic.CoreFailure

sealed class ResourceFailure: CoreFailure.FeatureFailure()

object ResourceNotFound : ResourceFailure()
