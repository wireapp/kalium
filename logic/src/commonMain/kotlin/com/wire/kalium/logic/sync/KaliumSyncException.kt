package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure

class KaliumSyncException(message: String, cause: CoreFailure): RuntimeException(message)
