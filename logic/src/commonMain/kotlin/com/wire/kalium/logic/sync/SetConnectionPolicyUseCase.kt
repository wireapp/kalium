package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository

class SetConnectionPolicyUseCase internal constructor(
    private val incrementalSyncRepository: IncrementalSyncRepository,
) {
    operator fun invoke(connectionPolicy: ConnectionPolicy) {
        incrementalSyncRepository.setConnectionPolicy(connectionPolicy)
    }
}
