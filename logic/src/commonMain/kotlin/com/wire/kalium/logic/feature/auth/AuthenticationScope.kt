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

package com.wire.kalium.logic.feature.auth

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.di.KaliumAuthenticationVerificationApi
import com.wire.kalium.logic.feature.appVersioning.CheckIfUpdateRequiredUseCase
import com.wire.kalium.logic.feature.auth.sso.SSOLoginScope
import com.wire.kalium.logic.feature.auth.verification.RequestSecondFactorVerificationCodeUseCase
import com.wire.kalium.logic.feature.register.RegisterScope
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.persistence.db.GlobalDatabaseBuilder
import dev.zacsweers.metro.createGraphFactory

/** The existing cache owns one authentication graph per server configuration/proxy pair. */
internal class AuthenticationScopeProvider internal constructor(
    private val userAgent: String,
    private val graphFactory: AuthenticationGraph.Factory = createGraphFactory<AuthenticationGraph.Factory>(),
) {
    private val authenticationScopeStorage: ConcurrentMutableMap<
        Pair<ServerConfig, ProxyCredentials?>,
        AuthenticationScope
        > by lazy {
        ConcurrentMutableMap()
    }

    internal fun provide(
        serverConfig: ServerConfig,
        proxyCredentials: ProxyCredentials?,
        globalDatabase: GlobalDatabaseBuilder,
        kaliumConfigs: KaliumConfigs,
    ): AuthenticationScope =
        authenticationScopeStorage.computeIfAbsent(serverConfig to proxyCredentials) {
            AuthenticationScope(
                graphFactory.create(
                    AuthenticationGraphInputValues(userAgent, serverConfig, proxyCredentials, globalDatabase, kaliumConfigs)
                )
            )
        }
}

/** Compatibility facade over the authentication cache's individual bindings. */
public class AuthenticationScope internal constructor(private val api: AuthenticationApi) {
    internal val serverConfigRepository: ServerConfigRepository
        get() = api.serverConfigRepository

    internal val ssoLoginRepository: SSOLoginRepository
        get() = api.ssoLoginRepository

    // This was eager before Metro: creating the facade must still initialize 2FA and its network.
    internal val secondFactorVerificationRepository: SecondFactorVerificationRepository = api.secondFactorVerificationRepository

    public val getLoginFlowForDomainUseCase: GetLoginFlowForDomainUseCase
        get() = api.getLoginFlowForDomainUseCase
    public val login: LoginUseCase
        get() = api.login
    public val requestSecondFactorVerificationCode: RequestSecondFactorVerificationCodeUseCase
        get() = api.requestSecondFactorVerificationCode
    public val registerScope: RegisterScope
        get() = api.registerScope
    public val ssoLoginScope: SSOLoginScope
        get() = api.ssoLoginScope
    internal val checkIfUpdateRequired: CheckIfUpdateRequiredUseCase
        get() = api.checkIfUpdateRequired
    public val domainLookup: DomainLookupUseCase
        get() = api.domainLookup
    public val isNomadProfilesEnabled: IsNomadProfilesEnabledUseCase
        get() = api.isNomadProfilesEnabled

    /** Same keyed graph, after this owner's eager 2FA/network initialization. */
    public val individualVerificationApi: KaliumAuthenticationVerificationApi
        get() = api

    // Retain one function value and read the graph input when invoked, not when the facade is built.
    public val currentServerConfig: () -> ServerConfig = { api.serverConfig }
}
