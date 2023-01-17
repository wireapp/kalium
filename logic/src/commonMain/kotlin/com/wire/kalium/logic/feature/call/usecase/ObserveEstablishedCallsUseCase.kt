package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for observing the established calls.
 */
class ObserveEstablishedCallsUseCase internal constructor(
    private val callRepository: CallRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {
    /**
     * That Flow emits everytime when the list is changed
     * @return a [Flow] of the list of established calls that should be shown to the user.
     */
    suspend operator fun invoke(): Flow<List<Call>> = withContext(dispatcher.default) {
        callRepository.establishedCallsFlow()
    }
}
