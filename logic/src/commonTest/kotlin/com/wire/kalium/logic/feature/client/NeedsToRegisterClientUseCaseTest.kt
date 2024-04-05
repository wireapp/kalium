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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.configure
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeedsToRegisterClientUseCaseTest {

    @Test
    fun givenAccountIsInvalid_thenReturnFalse() = runTest {

        val (arrangement, needsToRegisterClient) = Arrangement()
            .withUserAccountInfo(Either.Right(AccountInfo.Invalid(selfUserId, LogoutReason.SESSION_EXPIRED)))
            .arrange()
        needsToRegisterClient().also {
            assertEquals(false, it)
        }

        coVerify {
            arrangement.sessionRepository.userAccountInfo(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.currentClientIdProvider.invoke()
        }.wasNotInvoked()
    }

    @Test
    fun givenAccountIsValidAndThereISNoClient_thenReturnTrue() = runTest {

        val (arrangement, needsToRegisterClient) = Arrangement()
            .withUserAccountInfo(Either.Right(AccountInfo.Valid(selfUserId)))
            .withCurrentClientId(Either.Left(StorageFailure.DataNotFound))
            .withProteusClientSuccess()
            .arrange()
        needsToRegisterClient().also {
            assertEquals(true, it)
        }

        coVerify {
            arrangement.sessionRepository.userAccountInfo(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.currentClientIdProvider.invoke()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.proteusClientProvider.getOrError()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAccountIsValidAndClientIsRegisteredAndLocalCryptoFilesExists_thenReturnFalse() = runTest {
        val (arrangement, needsToRegisterClient) = Arrangement()
            .withUserAccountInfo(Either.Right(AccountInfo.Valid(selfUserId)))
            .withCurrentClientId(Either.Right(ClientId("client-id")))
            .withProteusClientSuccess()
            .arrange()
        needsToRegisterClient().also {
            assertEquals(false, it)
        }

        coVerify {
            arrangement.sessionRepository.userAccountInfo(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.currentClientIdProvider.invoke()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.proteusClientProvider.getOrError()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAccountIsValidAndClientIsRegisteredAndLocalCryptoFilesAreMissing_thenReturnTrue() = runTest {
        val (arrangement, needsToRegisterClient) = Arrangement()
            .withUserAccountInfo(Either.Right(AccountInfo.Valid(selfUserId)))
            .withProteusClientFailure(Either.Left(CoreFailure.MissingClientRegistration))
            .arrange()
        needsToRegisterClient().also {
            assertTrue(it)
        }

        coVerify {
            arrangement.sessionRepository.userAccountInfo(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.currentClientIdProvider.invoke()
        }.wasNotInvoked()

        coVerify {
            arrangement.proteusClientProvider.getOrError()
        }.wasInvoked(exactly = once)
    }

    private companion object {
        val selfUserId = UserId("selfUserId", "selfUserDomain")
    }

    private class Arrangement {
        @Mock
        val currentClientIdProvider = configure(mock(classOf<CurrentClientIdProvider>())) {
            stubsUnitByDefault = true
        }

        @Mock
        val sessionRepository = mock(SessionRepository::class)

        @Mock
        val proteusClientProvider = mock(ProteusClientProvider::class)

        @Mock
        private val proteusClient = mock(ProteusClient::class)

        private var needsToRegisterClientUseCase: NeedsToRegisterClientUseCase =
            NeedsToRegisterClientUseCaseImpl(currentClientIdProvider, sessionRepository, proteusClientProvider, selfUserId)

        suspend fun withCurrentClientId(result: Either<StorageFailure, ClientId>) = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(result)
        }

        suspend fun withUserAccountInfo(result: Either<StorageFailure, AccountInfo>) = apply {
            coEvery {
                sessionRepository.userAccountInfo(selfUserId)
            }.returns(result)
        }

        suspend fun withProteusClientSuccess() = apply {
            coEvery {
                proteusClientProvider.getOrError()
            }.returns(Either.Right(proteusClient))
        }

        suspend fun withProteusClientFailure(result: Either.Left<CoreFailure>) = apply {
            coEvery {
                proteusClientProvider.getOrError()
            }.returns(result)
        }

        fun arrange() = this to needsToRegisterClientUseCase
    }
}
