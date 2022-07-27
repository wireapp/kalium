package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class ObserveEstablishedCallsUseCase internal constructor(
    private val callRepository: CallRepository,
) {
    suspend operator fun invoke(): Flow<List<Call>> {
        return callRepository.establishedCallsFlow()
    }
}
