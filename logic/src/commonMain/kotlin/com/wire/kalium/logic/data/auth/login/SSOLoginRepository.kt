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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.model.AuthenticationResultDTO
import com.wire.kalium.network.api.base.unauthenticated.domainLookup.DomainLookupApi
import com.wire.kalium.network.api.unauthenticated.sso.InitiateParam
import com.wire.kalium.network.api.base.unauthenticated.sso.SSOLoginApi
import com.wire.kalium.network.api.unauthenticated.sso.SSOSettingsResponse
import io.mockative.Mockable

@Mockable
interface SSOLoginRepository {

    suspend fun initiate(
        uuid: String,
        successRedirect: String,
        errorRedirect: String
    ): Either<NetworkFailure, String>

    suspend fun initiate(uuid: String): Either<NetworkFailure, String>

    suspend fun finalize(cookie: String): Either<NetworkFailure, String>

    suspend fun provideLoginSession(cookie: String): Either<NetworkFailure, AuthenticationResultDTO>

    suspend fun metaData(): Either<NetworkFailure, String>

    suspend fun settings(): Either<NetworkFailure, SSOSettingsResponse>
    suspend fun domainLookup(domain: String): Either<NetworkFailure, DomainLookupResult>
}

class SSOLoginRepositoryImpl(
    private val ssoLogin: SSOLoginApi,
    private val domainLookup: DomainLookupApi
) : SSOLoginRepository {

    override suspend fun initiate(
        uuid: String,
        successRedirect: String,
        errorRedirect: String
    ): Either<NetworkFailure, String> = wrapApiRequest {
        ssoLogin.initiate(InitiateParam.WithRedirect(successRedirect, errorRedirect, uuid))
    }

    override suspend fun initiate(uuid: String): Either<NetworkFailure, String> = wrapApiRequest {
        ssoLogin.initiate(InitiateParam.WithoutRedirect(uuid))
    }

    override suspend fun finalize(cookie: String): Either<NetworkFailure, String> = wrapApiRequest {
        ssoLogin.finalize(cookie)
    }

    override suspend fun provideLoginSession(cookie: String): Either<NetworkFailure, AuthenticationResultDTO> = wrapApiRequest {
        ssoLogin.provideLoginSession(cookie)
    }

    override suspend fun metaData(): Either<NetworkFailure, String> = wrapApiRequest {
        ssoLogin.metaData()
    }

    override suspend fun settings(): Either<NetworkFailure, SSOSettingsResponse> = wrapApiRequest {
        ssoLogin.settings()
    }

     override suspend fun domainLookup(domain: String): Either<NetworkFailure, DomainLookupResult> = wrapApiRequest {
        domainLookup.lookup(domain)
    }.map {
        DomainLookupResult(it.configJsonUrl, it.webappWelcomeUrl)
    }
}
