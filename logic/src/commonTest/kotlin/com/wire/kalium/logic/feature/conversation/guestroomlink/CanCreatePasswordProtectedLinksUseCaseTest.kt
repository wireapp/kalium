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

import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.ServerConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ServerConfigRepositoryArrangementImpl
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import kotlinx.coroutines.runBlocking
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

        coVerify {
            arrangement.serverConfigRepository.configForUser(eq(SELF_USER_ID))
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.serverConfigRepository.configForUser(eq(SELF_USER_ID))
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.serverConfigRepository.configForUser(eq(SELF_USER_ID))
        }.wasInvoked(exactly = once)
    }

    private companion object {
        val SELF_USER_ID = UserId("selfUser", "domain")
    }

    private class Arrangement : ServerConfigRepositoryArrangement by ServerConfigRepositoryArrangementImpl() {

        private val useCase: CanCreatePasswordProtectedLinksUseCase = CanCreatePasswordProtectedLinksUseCase(
            serverConfigRepository,
            SELF_USER_ID
        )

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, CanCreatePasswordProtectedLinksUseCase> {
            runBlocking { block() }
            return this to useCase
        }
    }
}
