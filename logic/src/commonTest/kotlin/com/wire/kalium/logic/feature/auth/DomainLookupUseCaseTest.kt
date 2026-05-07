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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.configuration.server.CustomServerConfigRepository
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.DomainLookupResult
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.util.stubs.newServerConfig
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.ssoLoginRepository.domainLookup("wire.com")
        }
    }

    @Test
    fun givenDomain_whenInvoked_thenUserInputIsNotChangedForLookup() = runTest {
        val userEmail = "wire.com"
        val (arrangement, useCases) = Arrangement()
            .withDomainLookupResult(Either.Left(NetworkFailure.NoNetworkConnection(IOException())))
            .arrange()
        useCases(userEmail)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.ssoLoginRepository.domainLookup("wire.com")
        }
    }

    @Test
    fun givenSuccessForDomainLookup_whenLookup_thenFetchServerConfigUsingTheServerConfigUrl() = runTest {
        val userEmail = "cool-person@wire.com"

        val (arrangement, useCases) = Arrangement()
            .withDomainLookupResult(Either.Right(DomainLookupResult("https://wire.com", "https://wire.com")))
            .withFetchServerConfigResult(NetworkFailure.NoNetworkConnection(IOException()).left())
            .arrange()
        useCases(userEmail)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.customServerConfigRepository.fetchRemoteConfig("https://wire.com")
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.ssoLoginRepository.domainLookup("wire.com")
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.customServerConfigRepository.fetchRemoteConfig("https://wire.com")
        }
    }

    private class Arrangement {

        val ssoLoginRepository: SSOLoginRepository = mock(mode = MockMode.autoUnit)
        val customServerConfigRepository: CustomServerConfigRepository = mock(mode = MockMode.autoUnit)

        private val useCases = DomainLookupUseCase(
            customServerConfigRepository,
            ssoLoginRepository
        )

        suspend fun withDomainLookupResult(result: Either<NetworkFailure, DomainLookupResult>) = apply {
            everySuspend {
                ssoLoginRepository.domainLookup(any())
            } returns result
        }

        suspend fun withFetchServerConfigResult(result: Either<NetworkFailure, ServerConfig.Links>) = apply {
            everySuspend {
                customServerConfigRepository.fetchRemoteConfig(any())
            } returns result
        }

        fun arrange() = this to useCases
    }
}
