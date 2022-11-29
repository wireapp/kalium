package com.wire.kalium.logic.feature.appVersioning

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.auth.AuthenticationScopeProvider
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.TimeParser
import com.wire.kalium.logic.util.TimeParserImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

interface ObserveIfAppFreshEnoughUseCase {
    suspend operator fun invoke(currentAppVersion: Int): Flow<Boolean>
}

class ObserveIfAppFreshEnoughUseCaseImpl internal constructor(
    private val serverConfigRepository: ServerConfigRepository,
    private val authenticationScopeProvider: AuthenticationScopeProvider,
    private val userSessionScopeProvider: UserSessionScopeProvider,
    private val timeParser: TimeParser = TimeParserImpl()
) : ObserveIfAppFreshEnoughUseCase {

    override suspend fun invoke(currentAppVersion: Int): Flow<Boolean> {
        val currentDate = timeParser.currentTimeStamp()
        val dateForChecking = timeParser.dateMinusMilliseconds(currentDate, CHECK_APP_VERSION_FREQUENCY_MS)

        return serverConfigRepository.getServerConfigsWithUserIdAfterTheDate(dateForChecking)
            .onFailure { kaliumLogger.e("$TAG: error while getting configs for checking $it") }
            .getOrElse(flowOf(listOf()))
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
                                        println("$TAG proxy credentials required, but it's null")
                                    }
                                    credentials
                                } else {
                                    null
                                }
                            }

                        withContext(coroutineContext) {
                            async {
                                val isFreshEnough = authenticationScopeProvider
                                    .provide(serverConfig, proxyCredentials)
                                    .checkIfAppFreshEnough(currentAppVersion, serverConfig.links.blackList)
                                serverConfig.id to isFreshEnough
                            }
                        }
                    }
                    .awaitAll()

                val noUpdateRequiredConfigIds = configIdWithFreshFlag
                    .filter { (_, isFreshEnough) -> isFreshEnough }
                    .map { (configId, _) -> configId }
                    .toSet()

                if (noUpdateRequiredConfigIds.isNotEmpty()) {
                    serverConfigRepository.updateAppBlackListCheckDate(noUpdateRequiredConfigIds, currentDate)
                }

                configIdWithFreshFlag.any { (_, isFreshEnough) -> !isFreshEnough }
            }
    }

    companion object {
        private const val CHECK_APP_VERSION_FREQUENCY_MS = 24 * 60 * 60 * 1000L
        private const val TAG = "ObserveIfAppFreshEnoughUseCase"
    }

}
