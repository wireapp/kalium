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

package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides

/** Values captured once when AuthenticationScope.registerScope creates a fresh facade. */
internal class RegisterGraphCapture(
    val repository: RegisterAccountRepository,
    val serverConfig: ServerConfig,
    val proxyCredentials: ProxyCredentials?,
)

internal interface RegisterApi {
    val register: RegisterAccountUseCase
    val requestActivationCode: RequestActivationCodeUseCase
}

/** Unscoped use cases share only this graph's supplied capture, never a user-wide repository. */
@GraphExtension
internal interface RegisterGraph : RegisterApi {
    @Provides
    fun provideRegister(capture: RegisterGraphCapture): RegisterAccountUseCase =
        RegisterAccountUseCase(capture.repository, capture.serverConfig, capture.proxyCredentials)

    @Provides
    fun provideRequestActivationCode(capture: RegisterGraphCapture): RequestActivationCodeUseCase =
        RequestActivationCodeUseCase(capture.repository)

    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides capture: RegisterGraphCapture): RegisterGraph
    }
}
