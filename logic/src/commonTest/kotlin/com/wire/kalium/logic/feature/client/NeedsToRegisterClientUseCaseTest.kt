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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeedsToRegisterClientUseCaseTest {

    @Test
    fun givenAccountIsInvalid_thenReturnFalse() = runTest {

        val (arrangement, needsToRegisterClient) = Arrangement()
            .withUserAccountInfo(Either.Right(AccountInfo.Invalid(selfUserId, LogoutReason.SESSION_EXPIRED)))
            .arrange{}
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
            .arrange{
                withProteusTransactionReturning(Either.Right(Unit))
            }
        needsToRegisterClient().also {
            assertEquals(true, it)
        }

        coVerify {
            arrangement.sessionRepository.userAccountInfo(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.currentClientIdProvider.invoke()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAccountIsValidAndClientIsRegisteredAndLocalCryptoFilesExists_thenReturnFalse() = runTest {
        val (arrangement, needsToRegisterClient) = Arrangement()
            .withUserAccountInfo(Either.Right(AccountInfo.Valid(selfUserId)))
            .withCurrentClientId(Either.Right(ClientId("client-id")))
            .arrange{
                withProteusTransactionReturning(Either.Right(Unit))
            }
        needsToRegisterClient().also {
            assertEquals(false, it)
        }

        coVerify {
            arrangement.sessionRepository.userAccountInfo(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.currentClientIdProvider.invoke()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAccountIsValidAndClientIsRegisteredAndLocalCryptoFilesAreMissing_thenReturnTrue() = runTest {
        val (arrangement, needsToRegisterClient) = Arrangement()
            .withUserAccountInfo(Either.Right(AccountInfo.Valid(selfUserId)))
            .arrange{
                withProteusTransactionResultOnly(Either.Left(CoreFailure.MissingClientRegistration))
            }
        needsToRegisterClient().also {
            assertTrue(it)
        }

        coVerify {
            arrangement.sessionRepository.userAccountInfo(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.currentClientIdProvider.invoke()
        }.wasNotInvoked()
    }

    private companion object {
        val selfUserId = UserId("selfUserId", "selfUserDomain")
    }

    private class Arrangement: CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val currentClientIdProvider = mock(CurrentClientIdProvider::class)
        val sessionRepository = mock(SessionRepository::class)

        private val needsToRegisterClientUseCase: NeedsToRegisterClientUseCase =
            NeedsToRegisterClientUseCaseImpl(currentClientIdProvider, sessionRepository, cryptoTransactionProvider, selfUserId)

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

        fun arrange(block: suspend Arrangement.() -> Unit) = let {
            runBlocking { block() }
            this to needsToRegisterClientUseCase
        }

    }
}
