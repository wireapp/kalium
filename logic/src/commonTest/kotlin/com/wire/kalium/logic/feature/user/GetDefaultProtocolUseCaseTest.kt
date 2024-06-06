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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetDefaultProtocolUseCaseTest {

    @Test
    fun givenGetDefaultProtocolUseCase_whenRequestingDefaultProtocol_thenReturnProteusAsDefaultProtocol() = runTest {
        // given
        val (_, useCase) = GetDefaultProtocolUseCaseTestArrangement()
            .withReturningSuccessProteusProtocol()
            .arrange()

        // when
        val result = useCase()

        // then
        assertEquals(result, SupportedProtocol.PROTEUS)
    }

    @Test
    fun givenGetDefaultProtocolUseCase_whenRequestingDefaultProtocol_thenReturnMLSAsDefaultProtocol() = runTest {
        // given
        val (_, useCase) = GetDefaultProtocolUseCaseTestArrangement()
            .withReturningSuccessMLSProtocol()
            .arrange()

        // when
        val result = useCase()

        // then
        assertEquals(result, SupportedProtocol.MLS)
    }

    @Test
    fun givenRequestingDefaultProtocol_whenRequestFails_thenReturnProteusAsDefaultProtocol() = runTest {
        // given
        val (_, useCase) = GetDefaultProtocolUseCaseTestArrangement()
            .withReturningError()
            .arrange()

        // when
        val result = useCase()

        // then
        assertEquals(result, SupportedProtocol.PROTEUS)
    }

    internal class GetDefaultProtocolUseCaseTestArrangement {
        @Mock
        val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

        val useCase = GetDefaultProtocolUseCaseImpl(
            userConfigRepository = userConfigRepository
        )

        fun withReturningSuccessProteusProtocol() = apply {
            every {
                userConfigRepository.getDefaultProtocol()
            }.returns(Either.Right(SupportedProtocol.PROTEUS))
        }

        fun withReturningSuccessMLSProtocol() = apply {
            every {
                userConfigRepository.getDefaultProtocol()
            }.returns(Either.Right(SupportedProtocol.MLS))
        }

        fun withReturningError() = apply {
            every {
                userConfigRepository.getDefaultProtocol()
            }.returns(Either.Left(StorageFailure.Generic(Throwable())))
        }

        fun arrange() = this to useCase
    }
}
