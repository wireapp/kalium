package com.wire.kalium.logic.featureFlags

import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * This use case will observe and return the build configs.
 * @see KaliumConfigs
 */
interface GetBuildConfigsUseCase {
    suspend operator fun invoke(): Flow<KaliumConfigs>
}

internal class GetBuildConfigsUseCaseImpl(
    private val kaliumConfigs: KaliumConfigs,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : GetBuildConfigsUseCase {

    override suspend operator fun invoke(): Flow<KaliumConfigs> = withContext(dispatchers.default) {
        flowOf(kaliumConfigs)
    }
}
