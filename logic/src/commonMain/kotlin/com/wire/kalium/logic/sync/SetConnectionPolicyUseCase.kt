package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

class SetConnectionPolicyUseCase internal constructor(
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    suspend operator fun invoke(connectionPolicy: ConnectionPolicy) = withContext(dispatchers.default) {
        incrementalSyncRepository.setConnectionPolicy(connectionPolicy)
    }
}
