/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.auth.autoVersioningAuth

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigMapper
import com.wire.kalium.logic.feature.auth.AuthenticationScope
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.logic.util.stubs.newServerConfigEntity
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import sun.misc.Unsafe
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class AuthenticationScopeForConfigIdUseCaseTest {

    @Test
    fun givenConfigFound_whenInvoked_thenReturnsSuccessWithAuthScope() = runTest {
        val entity = newServerConfigEntity(1)
        val serverConfig = newServerConfig(1)
        val authScope = allocateUninitializedInstance<AuthenticationScope>()

        val dao = mock<ServerConfigurationDAO> {
            every { configById(serverConfig.id) } returns entity
        }
        val mapper = mock<ServerConfigMapper> {
            every { fromEntity(entity) } returns serverConfig
        }
        var factoryCalledWith: ServerConfig? = null
        val useCase = AuthenticationScopeForConfigIdUseCase(
            serverConfigurationDAO = dao,
            serverConfigMapper = mapper,
            authenticationScopeFactory = {
                factoryCalledWith = it
                authScope
            }
        )

        val result = useCase(serverConfig.id)

        assertIs<AutoVersionAuthScopeUseCase.Result.Success>(result)
        assertSame(authScope, result.authenticationScope)
        assertSame(serverConfig, factoryCalledWith)

        verify { dao.configById(serverConfig.id) }
        verify { mapper.fromEntity(entity) }
    }

    @Test
    fun givenConfigNotFound_whenInvoked_thenReturnsFailure() = runTest {
        val configId = "non-existent-id"

        val dao = mock<ServerConfigurationDAO> {
            every { configById(configId) } returns null
        }
        val mapper = mock<ServerConfigMapper>()
        var factoryCalled = false
        val useCase = AuthenticationScopeForConfigIdUseCase(
            serverConfigurationDAO = dao,
            serverConfigMapper = mapper,
            authenticationScopeFactory = {
                factoryCalled = true
                error("Should not be called")
            }
        )

        val result = useCase(configId)

        assertIs<AutoVersionAuthScopeUseCase.Result.Failure.Generic>(result)
        assertIs<StorageFailure.DataNotFound>(result.genericFailure)
        assertEquals(false, factoryCalled)

        verify { dao.configById(configId) }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> allocateUninitializedInstance(): T {
        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as Unsafe
        return unsafe.allocateInstance(T::class.java) as T
    }
}
