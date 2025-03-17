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

package com.wire.kalium.logic.data.auth.login

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.auth.DomainRegistrationMapper
import com.wire.kalium.logic.data.auth.LoginDomainPath
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.unauthenticated.domainregistration.GetDomainRegistrationApi
import com.wire.kalium.network.api.base.unauthenticated.login.LoginApi
import com.wire.kalium.network.api.unauthenticated.login.LoginParam

internal interface LoginRepository {
    suspend fun loginWithEmail(
        email: String,
        password: String,
        label: String?,
        shouldPersistClient: Boolean,
        secondFactorVerificationCode: String? = null,
    ): Either<NetworkFailure, Pair<AccountTokens, SsoId?>>

    suspend fun loginWithHandle(
        handle: String,
        password: String,
        label: String?,
        shouldPersistClient: Boolean
    ): Either<NetworkFailure, Pair<AccountTokens, SsoId?>>

    suspend fun getDomainRegistration(email: String): Either<NetworkFailure, LoginDomainPath>
    suspend fun fetchDomainRedirectCustomBackendConfig(backendUrl: String): Either<NetworkFailure, DomainLookupResult>
}

internal class LoginRepositoryImpl internal constructor(
    private val loginApi: LoginApi,
    private val getDomainRegistrationApi: GetDomainRegistrationApi,
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val domainRegistrationMapper: DomainRegistrationMapper = MapperProvider.domainRegistrationMapper()
) : LoginRepository {

    override suspend fun loginWithEmail(
        email: String,
        password: String,
        label: String?,
        shouldPersistClient: Boolean,
        secondFactorVerificationCode: String?,
    ): Either<NetworkFailure, Pair<AccountTokens, SsoId?>> =
        login(
            LoginParam.LoginWithEmail(email, password, label, secondFactorVerificationCode),
            shouldPersistClient
        )

    override suspend fun loginWithHandle(
        handle: String,
        password: String,
        label: String?,
        shouldPersistClient: Boolean,
    ): Either<NetworkFailure, Pair<AccountTokens, SsoId?>> =
        login(
            LoginParam.LoginWithHandle(handle, password, label),
            shouldPersistClient
        )

    override suspend fun getDomainRegistration(email: String): Either<NetworkFailure, LoginDomainPath> = wrapApiRequest {
        getDomainRegistrationApi.getDomainRegistration(email)
    }.map {
        domainRegistrationMapper.fromApiModel(it, email)
    }

    override suspend fun fetchDomainRedirectCustomBackendConfig(backendUrl: String): Either<NetworkFailure, DomainLookupResult> =
        wrapApiRequest{
            getDomainRegistrationApi.customBackendConfig(backendUrl)
        }.map {
            DomainLookupResult(it.configJsonUrl, it.webappWelcomeUrl)
        }

    private suspend fun login(
        loginParam: LoginParam,
        persistClient: Boolean
    ): Either<NetworkFailure, Pair<AccountTokens, SsoId?>> = wrapApiRequest {
        loginApi.login(param = loginParam, persist = persistClient)
    }.map {
        Pair(sessionMapper.fromSessionDTO(it.first), idMapper.toSsoId(it.second.ssoID))
    }
}
