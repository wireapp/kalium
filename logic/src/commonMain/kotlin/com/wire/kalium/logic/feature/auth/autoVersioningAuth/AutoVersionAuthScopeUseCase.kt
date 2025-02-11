/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.auth.autoVersioningAuth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.CoreLogicCommon
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.SupportedApiVersions

/**
 * This use case is responsible for obtaining the authentication scope for the current version of the app.
 * It will try to validate if the current client is able to talk to a specific backend version.
 */
class AutoVersionAuthScopeUseCase(
    private val kaliumConfigs: KaliumConfigs,
    private val serverLinks: ServerConfig.Links,
    private val coreLogic: CoreLogicCommon,
) {

    suspend operator fun invoke(
        proxyCredentials: ProxyCredentials?
    ): Result =
        coreLogic.getAuthenticationScope(
            serverConfig = ServerConfig(
                links = serverLinks,
                id = "initialization",
                metaData = ServerConfig.MetaData(
                    federation = false,
                    commonApiVersion = CommonApiVersionType.Valid(
                        SupportedApiVersions.first()
                    ),
                    domain = null
                )
            ),
            proxyCredentials = proxyCredentials
        ).serverConfigRepository.getOrFetchMetadata(serverLinks).fold({
            handleError(it)
        }, { serverConfig ->
            // Backend team doesn't want any clients using the development APIs in production, so
            // until they disable access to the APIs we'll have this safeguard in the client to
            // prevent any accidental usage.
            if (kaliumConfigs.developmentApiEnabled && serverConfig.links == ServerConfig.PRODUCTION) {
                return Result.Failure.Generic(CoreFailure.DevelopmentAPINotAllowedOnProduction)
            }
            Result.Success(coreLogic.getAuthenticationScope(serverConfig, proxyCredentials))
        })

    private fun handleError(coreFailure: CoreFailure): Result.Failure =
        when (coreFailure) {
            is ServerConfigFailure.NewServerVersion -> Result.Failure.TooNewVersion
            is ServerConfigFailure.UnknownServerVersion -> Result.Failure.UnknownServerVersion
            else -> Result.Failure.Generic(coreFailure)
        }.also { kaliumLogger.e(coreFailure.toString()) }

    sealed class Result {
        class Success(val authenticationScope: AuthenticationScope) : Result()

        sealed class Failure : Result() {
            data object UnknownServerVersion : Failure()
            data object TooNewVersion : Failure()
            data class Generic(val genericFailure: CoreFailure) : Failure()
        }
    }
}
