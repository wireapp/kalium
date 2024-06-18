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
@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly", "konsist.useCasesShouldNotAccessDaoLayerDirectly")

package com.wire.kalium.logic.feature.appVersioning

import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.appVersioning.ObserveIfAppUpdateRequiredUseCaseImpl.Companion.CHECK_APP_VERSION_FREQUENCY_MS
import com.wire.kalium.logic.feature.auth.AuthenticationScopeProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.intervalFlow
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 *
 * Observes ServerConfigs and checks if App needs to be updated for each of it.
 * Checking happens every [CHECK_APP_VERSION_FREQUENCY_MS] milliseconds, or when config was added into DB.
 * @return Flow<Boolean> emits true if at least one ServerConfig requires app updating, false - otherwise.
 *
 */
interface ObserveIfAppUpdateRequiredUseCase {
    suspend operator fun invoke(currentAppVersion: Int): Flow<Boolean>
}

class ObserveIfAppUpdateRequiredUseCaseImpl internal constructor(
    private val customServerConfigRepository: CustomServerConfigRepository,
    private val authenticationScopeProvider: AuthenticationScopeProvider,
    private val userSessionScopeProvider: UserSessionScopeProvider,
    private val globalDatabaseBuilder: GlobalDatabaseBuilder,
    private val kaliumConfigs: KaliumConfigs
) : ObserveIfAppUpdateRequiredUseCase {

    @Suppress("ComplexMethod", "LongMethod")
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun invoke(currentAppVersion: Int): Flow<Boolean> {
        val currentDate = DateTimeUtil.currentIsoDateTimeString()
        val dateForChecking = DateTimeUtil.minusMilliseconds(currentDate, CHECK_APP_VERSION_FREQUENCY_MS)

        return intervalFlow(CHECK_APP_VERSION_FREQUENCY_MS)
            .flatMapLatest {
                customServerConfigRepository.getServerConfigsWithUserIdAfterTheDate(dateForChecking)
                    .onFailure { kaliumLogger.e("$TAG: error while getting configs for checking $it") }
                    .getOrElse(flowOf(listOf()))
            }
            .distinctUntilChanged()
            .map { serverConfigsWithUserId ->
                val configIdWithFreshFlag = serverConfigsWithUserId
                    .map { (serverConfig, userId) ->
                        val proxyCredentials: ProxyCredentials? = serverConfig
                            .links
                            .apiProxy
                            ?.needsAuthentication
                            ?.let {
                                if (it) {
                                    val credentials = userSessionScopeProvider.get(userId)
                                        ?.getProxyCredentials
                                        ?.invoke()
                                        ?.let { dtoCredentials ->
                                            MapperProvider.sessionMapper().fromDTOToProxyCredentialsModel(dtoCredentials)
                                        }
                                    if (credentials == null) {
                                        kaliumLogger.e("$TAG proxy credentials required, but it's null")
                                    }
                                    credentials
                                } else {
                                    null
                                }
                            }

                        withContext(coroutineContext) {
                            async {
                                val isUpdateRequired = authenticationScopeProvider
                                    .provide(
                                        serverConfig = serverConfig,
                                        proxyCredentials = proxyCredentials,
                                        globalDatabase = globalDatabaseBuilder,
                                        kaliumConfigs = kaliumConfigs,
                                    )
                                    .checkIfUpdateRequired(currentAppVersion, serverConfig.links.blackList)
                                serverConfig.id to isUpdateRequired
                            }
                        }
                    }
                    .awaitAll()

                val noUpdateRequiredConfigIds = configIdWithFreshFlag
                    .filter { (_, isUpdateRequired) -> !isUpdateRequired }
                    .map { (configId, _) -> configId }
                    .toSet()

                if (noUpdateRequiredConfigIds.isNotEmpty()) {
                    customServerConfigRepository.updateAppBlackListCheckDate(noUpdateRequiredConfigIds, currentDate)
                }

                configIdWithFreshFlag.any { (_, isUpdateRequired) -> isUpdateRequired }
            }
    }

    companion object {
        private const val CHECK_APP_VERSION_FREQUENCY_MS = 24 * 60 * 60 * 1000L
        private const val TAG = "ObserveIfAppFreshEnoughUseCase"
    }

}
