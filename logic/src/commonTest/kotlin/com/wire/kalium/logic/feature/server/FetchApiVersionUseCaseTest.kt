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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FetchApiVersionUseCaseTest {

    @Test
    fun givenASuccessfulRepositoryResponse_whenInvokingTheUseCase_thenSuccessResultIsReturned() = runTest {
        // Given
        val serverConfig = ServerConfig(
            "",
            ServerConfig.DEFAULT,
            ServerConfig.MetaData(false, CommonApiVersionType.New, "")
        )

        val (arrangement, fetchApiVersionUseCase) = Arrangement()
            .withSuccessfulResponse(serverConfig)
            .arrange()

        // When
        fetchApiVersionUseCase.invoke(serverConfig.links)

        // Then
        verify(arrangement.configRepository)
            .suspendFunction(arrangement.configRepository::fetchApiVersionAndStore).with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFailWithNewServerVersion_thenTooNewVersionIsReturned() = runTest {
        // Given
        val newServerVersionFail = ServerConfigFailure.NewServerVersion
        val serverConfig = ServerConfig(
            "",
            ServerConfig.DEFAULT,
            ServerConfig.MetaData(false, CommonApiVersionType.New, "")
        )

        val (arrangement, fetchApiVersionUseCase) = Arrangement()
            .withErrorResponse(newServerVersionFail)
            .arrange()

        // When
        val result = fetchApiVersionUseCase.invoke(serverConfig.links)

        // Then
        verify(arrangement.configRepository)
            .suspendFunction(arrangement.configRepository::fetchApiVersionAndStore).with(any())
            .wasInvoked(exactly = once)

        assertEquals(result, FetchApiVersionResult.Failure.TooNewVersion)
    }

    @Test
    fun givenRepositoryCallFailWithUnknownServerVersion_thenUnknownServerVersionIsReturned() = runTest {
        // Given
        val unknownServerVersionFail = ServerConfigFailure.UnknownServerVersion
        val serverConfig = ServerConfig(
            "",
            ServerConfig.DEFAULT,
            ServerConfig.MetaData(false, CommonApiVersionType.New, "")
        )

        val (arrangement, fetchApiVersionUseCase) = Arrangement()
            .withErrorResponse(unknownServerVersionFail)
            .arrange()

        // When
        val result = fetchApiVersionUseCase.invoke(serverConfig.links)

        // Then
        verify(arrangement.configRepository)
            .suspendFunction(arrangement.configRepository::fetchApiVersionAndStore).with(any())
            .wasInvoked(exactly = once)

        assertEquals(result, FetchApiVersionResult.Failure.UnknownServerVersion)
    }

    private class Arrangement {

        @Mock
        val configRepository = mock(classOf<ServerConfigRepository>())

        val fetchApiVersionUseCase = FetchApiVersionUseCaseImpl(configRepository)

        fun withSuccessfulResponse(serverConfig: ServerConfig): Arrangement {
            given(configRepository)
                .suspendFunction(configRepository::fetchApiVersionAndStore).whenInvokedWith(any())
                .thenReturn(Either.Right(serverConfig))
            return this
        }

        fun withErrorResponse(coreFailure: CoreFailure): Arrangement {
            given(configRepository)
                .suspendFunction(configRepository::fetchApiVersionAndStore).whenInvokedWith(any())
                .thenReturn(Either.Left(coreFailure))
            return this
        }

        fun arrange() = this to fetchApiVersionUseCase
    }
}
