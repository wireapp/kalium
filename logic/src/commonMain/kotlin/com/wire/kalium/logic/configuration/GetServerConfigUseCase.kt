package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.functional.Either

class GetServerConfigUseCase (
    private val configRepository: ServerConfigRepository
) {
    suspend operator fun invoke(url:String): GetServerConfigResult =
        when (val result = configRepository.fetchRemoteConfig(url)) {
            is Either.Left -> {
                GetServerConfigResult.Failure.Generic(result.value)
            }
            is Either.Right -> GetServerConfigResult.Success(result.value)
        }
}
