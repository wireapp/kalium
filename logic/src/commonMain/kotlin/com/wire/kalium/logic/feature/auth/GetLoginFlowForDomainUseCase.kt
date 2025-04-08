/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.auth

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.data.auth.LoginDomainPath
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isEnterpriseServiceNotEnabled

/**
 * Use case to get the login flow for the client app/user to follow.
 * This is determined by the backend according to registration of the domain.¬
 *
 * @param [email] the email to look up the domain registration for
 */
interface GetLoginFlowForDomainUseCase {
    suspend operator fun invoke(email: String): EnterpriseLoginResult
}

@Suppress("FunctionNaming")
internal fun GetLoginFlowForDomainUseCase(
    loginRepository: LoginRepository,
    customServerConfigRepository: CustomServerConfigRepository,
    mapper: LoginRedirectMapper = LoginRedirectMapperImpl
) = object : GetLoginFlowForDomainUseCase {
    override suspend fun invoke(email: String): EnterpriseLoginResult {
        logger.d("Get domain registration")
        return loginRepository.getDomainRegistration(email)
            .handleEnterpriseServiceNotEnabled()
            .fold({
                val failure = it.mapFailure()
                logger.logStructuredJson(
                    level = KaliumLogLevel.ERROR,
                    leadingMessage = "Get domain registration",
                    jsonStringKeyValues = mapOf("error" to failure.toLogString())
                )
                failure
            }, {
                logger.logStructuredJson(
                    level = KaliumLogLevel.DEBUG,
                    leadingMessage = "Get domain registration",
                    jsonStringKeyValues = mapOf("path" to it.toLogString())
                )
                it.mapLoginPathToResult()
            })
    }

    private suspend fun LoginDomainPath.mapLoginPathToResult(): EnterpriseLoginResult {
        return when (this) {
            is LoginDomainPath.Default,
            is LoginDomainPath.ExistingAccountWithClaimedDomain,
            is LoginDomainPath.NoRegistration,
            is LoginDomainPath.SSO -> EnterpriseLoginResult.Success(mapper.fromModelToResult(this))

            is LoginDomainPath.CustomBackend -> {
                loginRepository.fetchDomainRedirectCustomBackendConfig(backendConfigUrl)
                    .flatMap {
                        customServerConfigRepository.fetchRemoteConfig(it.configJsonUrl)
                    }.fold({
                        it.mapFailure().also {
                            logger.logStructuredJson(
                                level = KaliumLogLevel.ERROR,
                                leadingMessage = "Fetch domain redirect custom backend config",
                                jsonStringKeyValues = mapOf("error" to it.toLogString())
                            )
                        }
                    }, {
                        EnterpriseLoginResult.Success(mapper.fromModelToCustomBackendResult(this, it))
                    })
            }
        }
    }

    private fun Either<NetworkFailure, LoginDomainPath>.handleEnterpriseServiceNotEnabled() = this.flatMapLeft {
        if (it is NetworkFailure.ServerMiscommunication
            && it.kaliumException is KaliumException.ServerError
            && (it.kaliumException as KaliumException.ServerError).isEnterpriseServiceNotEnabled()
        ) {
            logger.logStructuredJson(
                level = KaliumLogLevel.DEBUG,
                leadingMessage = "Get domain registration",
                jsonStringKeyValues = mapOf("error" to "EnterpriseServiceNotEnabled")
            )
            // if enterprise service is not enabled, the app should treat it as "no-registration" and continue
            LoginDomainPath.NoRegistration.right()

        } else {
            it.left()
        }
    }

    private fun NetworkFailure.mapFailure() =
        when (this) {
            is NetworkFailure.FeatureNotSupported -> EnterpriseLoginResult.Failure.NotSupported
            else -> EnterpriseLoginResult.Failure.Generic(this)
        }
}

/**
 * Result of the [GetLoginFlowForDomainUseCase].
 * Indicating error cases or the actual login path for the domain.
 */
sealed interface EnterpriseLoginResult {
    sealed class Failure : EnterpriseLoginResult {

        abstract fun toLogString(): String

        /**
         * The feature is not supported by this backend/client version
         */
        data object NotSupported : Failure() {
            override fun toLogString(): String {
                return "NotSupported"
            }
        }

        /**
         * Generic failure case.
         */
        data class Generic(val coreFailure: CoreFailure) : Failure() {
            override fun toLogString(): String {
                return "Generic"
            }
        }
    }

    /**
     * Enterprise Login is supported for the domain.
     */
    data class Success(val loginRedirectPath: LoginRedirectPath) : EnterpriseLoginResult
}

private const val TAG = "GetLoginFlowForDomainUseCase"
private val logger = kaliumLogger.withTextTag(TAG)
