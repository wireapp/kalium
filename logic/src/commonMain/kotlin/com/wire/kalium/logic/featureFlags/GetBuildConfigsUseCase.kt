package com.wire.kalium.logic.featureFlags

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface GetBuildConfigsUseCase {
    suspend operator fun invoke(): Flow<BuildTimeConfigs>
}

internal class GetBuildConfigsUseCaseImpl(
    private val buildTimeConfigs: BuildTimeConfigs,
) : GetBuildConfigsUseCase {

    override suspend operator fun invoke(): Flow<BuildTimeConfigs> {
        return flowOf(buildTimeConfigs)
    }
}
