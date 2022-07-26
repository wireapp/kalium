package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.data.sync.SyncRepository

class SetConnectionPolicyUseCase internal constructor(
    private val syncRepository: SyncRepository,
) {
    operator fun invoke(connectionPolicy: ConnectionPolicy) {
        syncRepository.setConnectionPolicy(connectionPolicy)
    }
}
