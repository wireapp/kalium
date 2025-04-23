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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.ProxyCredentialsEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateApiVersionUseCaseTest {

    @Test
    fun givenError_whenCallingValidSessionsWithServerConfig_thenDoNothingElse() = runTest {
        val (arrangement, updateApiVersionsUseCase) = Arrangement()
            .arrange {
                withValidSessionWithServerConfig(Either.Left(StorageFailure.Generic(IOException())))
            }

        updateApiVersionsUseCase()
        advanceUntilIdle()

        assertEquals(0, arrangement.serverConfigProviderCalledCount)
    }

    @Test
    fun givenUsersWithServerConfigNoProxy_thenDoNotFetchProxyCredentials() = runTest {
        val (arrangement, updateApiVersionsUseCase) = Arrangement()
            .arrange {
                withValidSessionWithServerConfig(
                    Either.Right(
                        mapOf(
                            userId1 to serverConfig1.copy(
                                links = serverConfig1.links.copy(
                                    apiProxy = null
                                )
                            )
                        )
                    )
                )
                withUpdateConfigMetaData(serverConfig1, Either.Right(Unit))
            }

        updateApiVersionsUseCase()
        advanceUntilIdle()

        coVerify {
            arrangement.tokenStorage.proxyCredentials(any<UserIDEntity>())
        }.wasNotInvoked()

        coVerify {
            arrangement.serverConfigRepository1.updateConfigMetaData(eq(serverConfig1))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserWithProxyButNoAuthentication_thenDoNotFetchProxyCredentials() = runTest {
        val (arrangement, updateApiVersionsUseCase) = Arrangement()
            .arrange {
                withValidSessionWithServerConfig(
                    Either.Right(
                        mapOf(
                            userId1 to serverConfig1.copy(
                                links = serverConfig1.links.copy(
                                    apiProxy = ServerConfig.ApiProxy(
                                        host = "host",
                                        port = 1234,
                                        needsAuthentication = false
                                    )
                                )
                            )
                        )
                    )
                )
                withUpdateConfigMetaData(serverConfig1, Either.Right(Unit))
            }

        updateApiVersionsUseCase()
        advanceUntilIdle()

        coVerify {
            arrangement.tokenStorage.proxyCredentials(any<UserIDEntity>())
        }.wasNotInvoked()

        coVerify {
            arrangement.serverConfigRepository1.updateConfigMetaData(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserWithProxyAndNeedAuthentication_thenFetchProxyCredentials() = runTest {
        val (arrangement, updateApiVersionsUseCase) = Arrangement()
            .arrange {
                withValidSessionWithServerConfig(
                    Either.Right(
                        mapOf(
                            userId1 to serverConfig1.copy(
                                links = serverConfig1.links.copy(
                                    apiProxy = ServerConfig.ApiProxy(
                                        host = "host",
                                        port = 1234,
                                        needsAuthentication = true
                                    )
                                )
                            )
                        )
                    )
                )
                withUpdateConfigMetaData(serverConfig1, Either.Right(Unit))
                withProxyCredForUser(userId1.toDao(), ProxyCredentialsEntity("user", "pass"))
            }

        updateApiVersionsUseCase()
        advanceUntilIdle()

        assertEquals(1, arrangement.serverConfigProviderCalledCount)
        coVerify {
            arrangement.tokenStorage.proxyCredentials(any<UserIDEntity>())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.serverConfigRepository1.updateConfigMetaData(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMultipleUsers_thenUpdateApiVersionForAll() = runTest {
        val (arrangement, updateApiVersionsUseCase) = Arrangement()
            .arrange {
                withValidSessionWithServerConfig(
                    Either.Right(
                        mapOf(
                            userId1 to serverConfig1.copy(
                                links = serverConfig1.links.copy(
                                    apiProxy = ServerConfig.ApiProxy(
                                        host = "host",
                                        port = 1234,
                                        needsAuthentication = false
                                    )
                                )
                            ),
                            userId2 to serverConfig2.copy(
                                links = serverConfig2.links.copy(
                                    apiProxy = ServerConfig.ApiProxy(
                                        host = "host",
                                        port = 1234,
                                        needsAuthentication = true
                                    )
                                )
                            )
                        )
                    )
                )
                withUpdateConfigMetaData(serverConfig1, Either.Right(Unit))
                withUpdateConfigMetaData(serverConfig2, Either.Right(Unit))
                withProxyCredForUser(userId2.toDao(), ProxyCredentialsEntity("user", "pass"))
            }

        updateApiVersionsUseCase()
        advanceUntilIdle()

        assertEquals(2, arrangement.serverConfigProviderCalledCount)
        coVerify {
            arrangement.tokenStorage.proxyCredentials(eq(userId2.toDao()))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.tokenStorage.proxyCredentials(eq(userId1.toDao()))
        }.wasNotInvoked()

        coVerify {
            arrangement.serverConfigRepository1.updateConfigMetaData(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.serverConfigRepository2.updateConfigMetaData(any())
        }.wasInvoked(exactly = once)

    }

    private companion object {
        val userId1: UserId = UserId("user1", "domnaion1")
        val userId2: UserId = UserId("user2", "domnaion2")
        val serverConfig1 = newServerConfig(1)
        val serverConfig2 = newServerConfig(2)
    }

    private class Arrangement {
                val sessionRepository: SessionRepository = mock(SessionRepository::class)
        val tokenStorage: AuthTokenStorage = mock(AuthTokenStorage::class)

        var serverConfigProviderCalledCount: Int = 0
            private set
        val serverConfigRepository1: ServerConfigRepository = mock(ServerConfigRepository::class)
        val serverConfigRepository2: ServerConfigRepository = mock(ServerConfigRepository::class)

        fun withProxyCredForUser(
            userId: UserIDEntity,
            result: ProxyCredentialsEntity?
        ) {
            every {
                tokenStorage.proxyCredentials(eq(userId))
            }.returns(result)
        }

        suspend fun withUpdateConfigMetaData(
            serverConfig: ServerConfig,
            result: Either<CoreFailure, Unit>
        ) {
            when (serverConfig.id) {
                serverConfig1.id ->
                    coEvery { serverConfigRepository1.updateConfigMetaData(any()) }
                        .returns(result)

                serverConfig2.id -> coEvery { serverConfigRepository2.updateConfigMetaData(any()) }
                    .returns(result)

                else -> throw IllegalArgumentException("Unexpected server config: $serverConfig")
            }
        }

        suspend fun withValidSessionWithServerConfig(
            result: Either<StorageFailure, Map<UserId, ServerConfig>>
        ) {
            coEvery {
                sessionRepository.validSessionsWithServerConfig()
            }.returns(result)
        }

        private val updateApiVersionsUseCase = UpdateApiVersionsUseCaseImpl(
            sessionRepository,
            tokenStorage,
            { serverConfig: ServerConfig, proxyCredentials: ProxyCredentials? ->
                serverConfigProviderCalledCount++
                when (serverConfig.id) {
                    serverConfig1.id -> serverConfigRepository1
                    serverConfig2.id -> serverConfigRepository2
                    else -> throw IllegalArgumentException("Unexpected server config: $serverConfig")
                }
            }
        )

        suspend fun arrange(block: suspend Arrangement.() -> Unit) = let {
            block()
            this to updateApiVersionsUseCase
        }
    }
}
