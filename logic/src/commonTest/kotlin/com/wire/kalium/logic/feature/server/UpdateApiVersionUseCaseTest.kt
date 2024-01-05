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

package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.Mock
import io.mockative.Times
import io.mockative.any
import io.mockative.configure
import io.mockative.given
import io.mockative.matchers.OneOfMatcher
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateApiVersionUseCaseTest {

    @Mock
    internal val configRepository = configure(mock(ServerConfigRepository::class)) { stubsUnitByDefault = true }

    private lateinit var updateApiVersionsUseCase: UpdateApiVersionsUseCase

    @BeforeTest
    fun setup() {
        updateApiVersionsUseCase = UpdateApiVersionsUseCaseImpl(configRepository)
    }

    @Test
    fun givenConfigList_whenUpdatingApiVersions_thenALLMUSTBEUPDATED() = runTest {
        val configList = listOf(newServerConfig(1), newServerConfig(2), newServerConfig(3), newServerConfig(4))

        given(configRepository)
            .suspendFunction(configRepository::configList)
            .whenInvoked()
            .thenReturn(
                Either.Right(configList)
            )

        given(configRepository)
            .suspendFunction(configRepository::updateConfigApiVersion)
            .whenInvokedWith(any())
            .then { Either.Right(Unit) }

        updateApiVersionsUseCase()

        verify(configRepository)
            .suspendFunction(configRepository::updateConfigApiVersion)
            .with(OneOfMatcher(configList.map { it.id }))
            .wasInvoked(exactly = Times(configList.size))
    }
}
