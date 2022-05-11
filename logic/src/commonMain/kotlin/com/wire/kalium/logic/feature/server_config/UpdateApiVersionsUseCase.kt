package com.wire.kalium.logic.feature.server_config

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfigUtil
import com.wire.kalium.logic.configuration.server.ServerConfigUtilImpl
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.network.api.api_version.VersionInfoDTO
import com.wire.kalium.network.tools.ServerConfigDTO

interface UpdateApiVersionsUseCase {
    suspend operator fun invoke(): UpdateApiVersionsResult
}

class UpdateApiVersionsUseCaseImpl internal constructor(
    private val configRepository: ServerConfigRepository,
    private val serverConfigMapper: ServerConfigMapper,
    private val serverConfigUtil: ServerConfigUtil = ServerConfigUtilImpl
) : UpdateApiVersionsUseCase {
    override suspend operator fun invoke(): UpdateApiVersionsResult {
        val serverConfigDTOList = configRepository.configList().fold(
            { return UpdateApiVersionsResult.Failure(it) },
            { configList -> configList.map { serverConfigMapper.toDTO(it) } }
        )

        val updateApiVersionDataList = serverConfigDTOList.foldToEitherWhileRight(listOf()) { item, list: List<UpdateApiVersionData> ->
            item.getUpdateApiVersionData().map { list.plus(it) }
        }.fold(
            { return UpdateApiVersionsResult.Failure(it) },
            { it }
        )

        return updateApiVersionDataList.foldToEitherWhileRight(listOf()) { updateApiVersionData, list: List<ServerConfig> ->
            val (serverConfigDTO, versionInfoDTO, commonApiVersion) = updateApiVersionData
            configRepository.storeConfig(serverConfigDTO, versionInfoDTO.domain, commonApiVersion.version, versionInfoDTO.federation)
                .map { list.plus(it) }
        }.fold({ UpdateApiVersionsResult.Failure(it) }, { UpdateApiVersionsResult.Success(it) })
    }

    private suspend fun ServerConfigDTO.getUpdateApiVersionData(): Either<CoreFailure, UpdateApiVersionData> {
        val versionInfoDTO = configRepository.fetchRemoteApiVersion(this).let {
            when (it) {
                is Either.Right -> it.value
                is Either.Left -> return it
            }
        }
        val commonApiVersionType = serverConfigUtil.calculateApiVersion(versionInfoDTO.supported).let {
            when (it) {
                is Either.Right -> CommonApiVersionType.Valid(it.value)
                is Either.Left -> when (it.value) {
                    ServerConfigFailure.UnknownServerVersion -> CommonApiVersionType.Unknown
                    ServerConfigFailure.NewServerVersion -> CommonApiVersionType.New
                }
            }
        }
        return Either.Right(UpdateApiVersionData(this, versionInfoDTO, commonApiVersionType))
    }
}

private typealias UpdateApiVersionData = Triple<ServerConfigDTO, VersionInfoDTO, CommonApiVersionType>

sealed class UpdateApiVersionsResult {
    // TODO: change to return the id only so we are now passing the whole config object around in the app
    class Success(val serverConfigList: List<ServerConfig>) : UpdateApiVersionsResult()
    class Failure(val genericFailure: CoreFailure) : UpdateApiVersionsResult()
}
