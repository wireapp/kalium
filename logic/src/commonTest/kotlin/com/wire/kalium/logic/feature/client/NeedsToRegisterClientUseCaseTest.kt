/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.client.CurrentClientIdProvider
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NeedsToRegisterClientUseCaseTest {

    @Test
    fun givenAccountIsInvalid_thenReturnFalse() = runTest {

        val (arrangement, needsToRegisterClient) = Arrangement()
            .withUserAccountInfo(Either.Right(AccountInfo.Invalid(selfUserId, LogoutReason.SESSION_EXPIRED)))
            .arrange()
        needsToRegisterClient().also {
            assertEquals(false, it)
        }

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::userAccountInfo)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.currentClientIdProvider)
            .suspendFunction(arrangement.currentClientIdProvider::invoke)
            .wasNotInvoked()
    }

    @Test
    fun givenAccountIsValidAndThereISNoClient_thenReturnTrue() = runTest {

        val (arrangement, needsToRegisterClient) = Arrangement()
            .withUserAccountInfo(Either.Right(AccountInfo.Valid(selfUserId)))
            .withCurrentClientId(Either.Left(StorageFailure.DataNotFound))
            .arrange()
        needsToRegisterClient().also {
            assertEquals(true, it)
        }

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::userAccountInfo)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.currentClientIdProvider)
            .suspendFunction(arrangement.currentClientIdProvider::invoke)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAccountIsValidAndClientIsRegistered_thenReturnFalse() = runTest {

        val (arrangement, needsToRegisterClient) = Arrangement()
            .withUserAccountInfo(Either.Right(AccountInfo.Valid(selfUserId)))
            .withCurrentClientId(Either.Right(ClientId("client-id")))
            .arrange()
        needsToRegisterClient().also {
            assertEquals(false, it)
        }

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::userAccountInfo)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.currentClientIdProvider)
            .suspendFunction(arrangement.currentClientIdProvider::invoke)
            .wasInvoked(exactly = once)
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

        private var needsToRegisterClientUseCase: NeedsToRegisterClientUseCase =
            NeedsToRegisterClientUseCaseImpl(currentClientIdProvider, sessionRepository, selfUserId)

        fun withCurrentClientId(result: Either<StorageFailure, ClientId>) = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .then { result }
        }

        suspend fun withUserAccountInfo(result: Either<StorageFailure, AccountInfo>) = apply {
            given(sessionRepository)
                .coroutine { userAccountInfo(selfUserId) }
                .then { result }
        }

        fun arrange() = this to needsToRegisterClientUseCase
    }
}
