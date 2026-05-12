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
package com.wire.kalium.logic.feature.conversation.guestroomlink

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.stubs.newServerConfig
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanCreatePasswordProtectedLinksUseCaseTest {

    @Test
    fun givenApiIs4_whenInvokingUseCase_thenReturnTrue() = runTest {
        val expected = newServerConfig(1).let {
            it.copy(metaData = it.metaData.copy(commonApiVersion = CommonApiVersionType.Valid(4)))
        }
        val (arrangement, useCase) = Arrangement().arrange {
            withServerConfigForUser(Either.Right(expected))
        }

        useCase().also {
            assertTrue(it)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serverConfigRepository.configForUser(eq(SELF_USER_ID))
        }
    }

    @Test
    fun givenApiIsGraterThan4_whenInvokingUseCase_thenReturnTrue() = runTest {
        val expected = newServerConfig(1).let {
            it.copy(metaData = it.metaData.copy(commonApiVersion = CommonApiVersionType.Valid(5)))
        }
        val (arrangement, useCase) = Arrangement().arrange {
            withServerConfigForUser(Either.Right(expected))
        }

        useCase().also {
            assertTrue(it)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serverConfigRepository.configForUser(eq(SELF_USER_ID))
        }
    }

    @Test
    fun givenApiIsLessThan4_whenInvokingUseCase_thenReturnFalse() = runTest {
        val expected = newServerConfig(1).let {
            it.copy(metaData = it.metaData.copy(commonApiVersion = CommonApiVersionType.Valid(3)))
        }
        val (arrangement, useCase) = Arrangement().arrange {
            withServerConfigForUser(Either.Right(expected))
        }

        useCase().also {
            assertFalse(it)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serverConfigRepository.configForUser(eq(SELF_USER_ID))
        }
    }

    private companion object {
        val SELF_USER_ID = UserId("selfUser", "domain")
    }

    private class Arrangement {
        val serverConfigRepository = mock<ServerConfigRepository>(mode = MockMode.autoUnit)

        private val useCase: CanCreatePasswordProtectedLinksUseCase = CanCreatePasswordProtectedLinksUseCase(
            serverConfigRepository,
            SELF_USER_ID
        )

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, CanCreatePasswordProtectedLinksUseCase> {
            block()
            return this to useCase
        }

        suspend fun withServerConfigForUser(result: Either<StorageFailure, ServerConfig>) {
            everySuspend { serverConfigRepository.configForUser(eq(SELF_USER_ID)) } returns result
        }
    }
}
