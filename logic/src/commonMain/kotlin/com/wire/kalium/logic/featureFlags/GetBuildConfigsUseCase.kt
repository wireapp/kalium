package com.wire.kalium.logic.featureFlags

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface GetBuildConfigsUseCase {
    suspend operator fun invoke(): Flow<KaliumConfigs>
}

internal class GetBuildConfigsUseCaseImpl(
    private val kaliumConfigs: KaliumConfigs,
) : GetBuildConfigsUseCase {

    override suspend operator fun invoke(): Flow<KaliumConfigs> {
        return flowOf(kaliumConfigs)
    }
}
