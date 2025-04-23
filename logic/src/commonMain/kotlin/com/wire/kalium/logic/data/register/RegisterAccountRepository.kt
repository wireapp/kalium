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

package com.wire.kalium.logic.data.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.unauthenticated.register.ActivationParam
import com.wire.kalium.network.api.base.unauthenticated.register.RegisterApi
import com.wire.kalium.network.api.unauthenticated.register.RegisterParam
import com.wire.kalium.network.api.unauthenticated.register.RequestActivationCodeParam
import io.mockative.Mockable

@Mockable
internal interface RegisterAccountRepository {
    suspend fun requestEmailActivationCode(email: String): Either<NetworkFailure, Unit>
    suspend fun verifyActivationCode(
        email: String,
        code: String
    ): Either<NetworkFailure, Unit>

    suspend fun registerPersonalAccountWithEmail(
        email: String,
        code: String,
        name: String,
        password: String,
        cookieLabel: String?
    ): Either<NetworkFailure, Pair<SsoId?, AccountTokens>>

    @Suppress("LongParameterList")
    suspend fun registerTeamWithEmail(
        email: String,
        code: String,
        name: String,
        password: String,
        teamName: String,
        teamIcon: String,
        cookieLabel: String?
    ): Either<NetworkFailure, Pair<SsoId?, AccountTokens>>
}

internal class RegisterAccountDataSource internal constructor(
    private val registerApi: RegisterApi,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val sessionMapper: SessionMapper = MapperProvider.sessionMapper()
) : RegisterAccountRepository {
    override suspend fun requestEmailActivationCode(email: String): Either<NetworkFailure, Unit> =
        requestActivation(RequestActivationCodeParam.Email(email))

    override suspend fun verifyActivationCode(
        email: String,
        code: String
    ): Either<NetworkFailure, Unit> =
        activateUser(ActivationParam.Email(email, code))

    override suspend fun registerPersonalAccountWithEmail(
        email: String,
        code: String,
        name: String,
        password: String,
        cookieLabel: String?
    ): Either<NetworkFailure, Pair<SsoId?, AccountTokens>> =
        register(
            RegisterParam.PersonalAccount(
                email = email,
                emailCode = code,
                name = name,
                cookieLabel = cookieLabel,
                password = password,
            )
        )

    override suspend fun registerTeamWithEmail(
        email: String,
        code: String,
        name: String,
        password: String,
        teamName: String,
        teamIcon: String,
        cookieLabel: String?
    ): Either<NetworkFailure, Pair<SsoId?, AccountTokens>> =
        register(
            RegisterParam.TeamAccount(
                email = email,
                emailCode = code,
                name = name,
                password = password,
                cookieLabel = cookieLabel,
                teamName = teamName,
                teamIcon = teamIcon
            )
        )

    private suspend fun requestActivation(
        param: RequestActivationCodeParam
    ): Either<NetworkFailure, Unit> = wrapApiRequest { registerApi.requestActivationCode(param) }

    private suspend fun activateUser(param: ActivationParam): Either<NetworkFailure, Unit> =
        wrapApiRequest { registerApi.activate(param) }

    private suspend fun register(param: RegisterParam): Either<NetworkFailure, Pair<SsoId?, AccountTokens>> =
        wrapApiRequest { registerApi.register(param) }.map {
            Pair(idMapper.toSsoId(it.first.ssoID), sessionMapper.fromSessionDTO(it.second))
        }
}
