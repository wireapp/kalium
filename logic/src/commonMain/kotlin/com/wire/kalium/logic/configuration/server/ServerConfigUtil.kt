package com.wire.kalium.logic.configuration.server

import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.SupportedApiVersions
import com.wire.kalium.network.api.api_version.VersionInfoDTO


interface ServerConfigUtil {
    fun calculateApiVersion(versionInfoDTO: VersionInfoDTO, appVersion: Set<Int> = SupportedApiVersions): Either<ServerConfigFailure, Int>
}

object ServerConfigUtilImpl : ServerConfigUtil {
    override fun calculateApiVersion(versionInfoDTO: VersionInfoDTO, appVersion: Set<Int>): Either<ServerConfigFailure, Int> =
        versionInfoDTO.supported.intersect(appVersion).maxOrNull().let { maxCommonVersion ->
            when (maxCommonVersion) {
                null -> handleNoCommonVersion(versionInfoDTO.supported, appVersion)
                else -> Either.Right(maxCommonVersion)
            }
        }

    private fun handleNoCommonVersion(serverVersion: List<Int>, appVersion: Set<Int>): Either.Left<ServerConfigFailure> {
        return try {
            val maxBEVersion = serverVersion.maxOrNull()!!
            val maxAppVersion = appVersion.maxOrNull()!!
            if (maxBEVersion > maxAppVersion) Either.Left(ServerConfigFailure.NewServerVersion)
            else Either.Left(ServerConfigFailure.UnknownServerVersion)
        } catch (e: NullPointerException) {
            kaliumLogger.w("empty app/server config list")
            Either.Left(ServerConfigFailure.UnknownServerVersion)
        }
    }
}
