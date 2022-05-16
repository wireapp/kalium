package com.wire.kalium.logic.configuration.server

import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.SupportedApiVersions


interface ServerConfigUtil {
    fun calculateApiVersion(serverVersion: List<Int>, appVersion: Set<Int> = SupportedApiVersions): Either<ServerConfigFailure, Int>
}

object ServerConfigUtilImpl : ServerConfigUtil {
    override fun calculateApiVersion(serverVersion: List<Int>, appVersion: Set<Int>): Either<ServerConfigFailure, Int> =
        serverVersion.intersect(appVersion).maxOrNull().let { maxCommonVersion ->
            when (maxCommonVersion) {
                null -> handleNoCommonVersion(serverVersion, appVersion)
                else -> Either.Right(maxCommonVersion)
            }
        }

    private fun handleNoCommonVersion(serverVersion: List<Int>, appVersion: Set<Int>): Either.Left<ServerConfigFailure> {
        return serverVersion.maxOrNull()?.let { maxBEVersion ->
            appVersion.maxOrNull()?.let { maxAppVersion ->
                if (maxBEVersion > maxAppVersion) Either.Left(ServerConfigFailure.NewServerVersion)
                else Either.Left(ServerConfigFailure.UnknownServerVersion)
            }
        } ?: run {
            kaliumLogger.w("empty app/server config list")
            Either.Left(ServerConfigFailure.UnknownServerVersion)
        }
    }
}
