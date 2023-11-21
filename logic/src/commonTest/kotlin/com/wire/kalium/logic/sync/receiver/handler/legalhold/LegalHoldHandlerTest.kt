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
package com.wire.kalium.logic.sync.receiver.handler.legalhold

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.PersistOtherUserClientsUseCase
import com.wire.kalium.logic.feature.client.SelfClientsResult
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.BeforeTest
import kotlin.test.Test

class LegalHoldHandlerTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenLegalHoldEvent_whenUserIdIsSelfUserThenUpdateSelfUserClients() = runTest {
        val (arrangement, handler) = Arrangement()
            .withDeleteLegalHoldSuccess()
            .withFetchSelfClientsFromRemoteSuccess()
            .arrange()

        handler.handleEnable(legalHoldEventEnabled)

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::deleteLegalHoldRequest)
            .wasInvoked(once)

        advanceUntilIdle()
        verify(arrangement.fetchSelfClientsFromRemote)
            .suspendFunction(arrangement.fetchSelfClientsFromRemote::invoke)
            .wasInvoked(once)
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenLegalHoldEvent_whenUserIdIsOtherUserThenUpdateOtherUserClients() = runTest {
        val (arrangement, handler) = Arrangement().arrange()

        handler.handleDisable(legalHoldEventDisabled)

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::deleteLegalHoldRequest)
            .wasNotInvoked()

        advanceUntilIdle()
        verify(arrangement.persistOtherUserClients)
            .suspendFunction(arrangement.persistOtherUserClients::invoke)
            .with(any())
            .wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val persistOtherUserClients = mock(PersistOtherUserClientsUseCase::class)

        @Mock
        val fetchSelfClientsFromRemote = mock(FetchSelfClientsFromRemoteUseCase::class)

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        fun arrange() =
            this to LegalHoldHandlerImpl(
                selfUserId = TestUser.SELF.id,
                persistOtherUserClients = persistOtherUserClients,
                fetchSelfClientsFromRemote = fetchSelfClientsFromRemote,
                userConfigRepository = userConfigRepository,
                coroutineContext = StandardTestDispatcher()
            )

        fun withDeleteLegalHoldSuccess() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::deleteLegalHoldRequest)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))
        }

        fun withFetchSelfClientsFromRemoteSuccess() = apply {
            given(fetchSelfClientsFromRemote)
                .suspendFunction(fetchSelfClientsFromRemote::invoke)
                .whenInvoked()
                .thenReturn(SelfClientsResult.Success(emptyList(), ClientId("client-id")))
        }
    }

    companion object {
        val legalHoldEventEnabled = Event.User.LegalHoldEnabled(
            transient = false,
            live = false,
            id = "id-1",
            userId = TestUser.SELF.id,
        )
        val legalHoldEventDisabled = Event.User.LegalHoldDisabled(
            transient = false,
            live = false,
            id = "id-2",
            userId = TestUser.OTHER_USER_ID
        )
    }
}