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
package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.DomainLookupResult
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DomainLookupUseCaseTest {

    @Test
    fun givenEmail_whenInvoked_thenLookupForTheEmailDomainOnly() = runTest {
        val userEmail = "cool-person@wire.com"
        val (arrangement, useCases) = Arrangement()
            .withDomainLookupResult(Either.Left(NetworkFailure.NoNetworkConnection(IOException())))
            .arrange()
        useCases(userEmail)

        verify(arrangement.ssoLoginRepository)
            .suspendFunction(arrangement.ssoLoginRepository::domainLookup)
            .with(eq("wire.com"))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenDomain_whenInvoked_thenUserInputIsNotChangedForLookup() = runTest {
        val userEmail = "wire.com"
        val (arrangement, useCases) = Arrangement()
            .withDomainLookupResult(Either.Left(NetworkFailure.NoNetworkConnection(IOException())))
            .arrange()
        useCases(userEmail)

        verify(arrangement.ssoLoginRepository)
            .suspendFunction(arrangement.ssoLoginRepository::domainLookup)
            .with(eq("wire.com"))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSuccessForDomainLookup_whenLookup_thenFetchServerConfigUsingTheServerConfigUrl() = runTest {
        val userEmail = "cool-person@wire.com"


        val (arrangement, useCases) = Arrangement()
            .withDomainLookupResult(Either.Right(DomainLookupResult("https://wire.com", "https://wire.com")))
            .withFetchServerConfigResult(NetworkFailure.NoNetworkConnection(IOException()).left())
            .arrange()
        useCases(userEmail)

        verify(arrangement.customServerConfigRepository)
            .suspendFunction(arrangement.customServerConfigRepository::fetchRemoteConfig)
            .with(eq("https://wire.com"))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSuccess_whenLookup_thenSuccessIsPropagated() = runTest {
        val userEmail = "cool-person@wire.com"
        val expectedServerLinks = newServerConfig(1).links

        val (arrangement, useCases) = Arrangement()
            .withDomainLookupResult(Either.Right(DomainLookupResult("https://wire.com", "https://wire.com")))
            .withFetchServerConfigResult(expectedServerLinks.right())
            .arrange()

        useCases(userEmail).also {
            assertIs<DomainLookupUseCase.Result.Success>(it)
            assertEquals(expectedServerLinks, it.serverLinks)
        }

        verify(arrangement.ssoLoginRepository)
            .suspendFunction(arrangement.ssoLoginRepository::domainLookup)
            .with(eq("wire.com"))
            .wasInvoked(exactly = once)

        verify(arrangement.customServerConfigRepository)
            .suspendFunction(arrangement.customServerConfigRepository::fetchRemoteConfig)
            .with(eq("https://wire.com"))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val ssoLoginRepository: SSOLoginRepository = mock(SSOLoginRepository::class)

        @Mock
        val customServerConfigRepository: CustomServerConfigRepository = mock(CustomServerConfigRepository::class)

        private val useCases = DomainLookupUseCase(
            customServerConfigRepository,
            ssoLoginRepository
        )

        fun withDomainLookupResult(result: Either<NetworkFailure, DomainLookupResult>) = apply {
            given(ssoLoginRepository)
                .suspendFunction(ssoLoginRepository::domainLookup)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withFetchServerConfigResult(result: Either<NetworkFailure, ServerConfig.Links>) = apply {
            given(customServerConfigRepository)
                .suspendFunction(customServerConfigRepository::fetchRemoteConfig)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to useCases
    }
}
