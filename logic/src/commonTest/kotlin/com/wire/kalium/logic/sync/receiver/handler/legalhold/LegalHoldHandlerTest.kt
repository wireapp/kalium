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
import com.wire.kalium.logic.feature.legalhold.LegalHoldState
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
    fun givenLegalHoldEvent_whenUserIdIsSelfUserThenUpdateSelfUserClientsAndDeleteLegalHoldRequest() = runTest {
        val (arrangement, handler) = Arrangement()
            .withDeleteLegalHoldSuccess()
            .withSetLegalHoldChangeNotifiedSuccess()
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
        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::setLegalHoldChangeNotified)
            .with(eq(false))
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

    @Test
    fun givenUserLegalHoldEnabled_whenHandlingEnable_thenDoNotCreateOrUpdateSystemMessages() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Enabled)
            .withSetLegalHoldChangeNotifiedSuccess()
            .arrange()
        // when
        handler.handleEnable(legalHoldEventEnabled)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleEnable)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenUserLegalHoldDisabled_whenHandlingDisable_thenDoNotCreateOrUpdateSystemMessages() = runTest {
        // given
        val (arrangement, handler) = Arrangement()
            .withObserveLegalHoldStateForUserSuccess(LegalHoldState.Disabled)
            .arrange()
        // when
        handler.handleDisable(legalHoldEventDisabled)
        // then
        verify(arrangement.legalHoldSystemMessagesHandler)
            .suspendFunction(arrangement.legalHoldSystemMessagesHandler::handleDisable)
            .with(any())
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val persistOtherUserClients = mock(PersistOtherUserClientsUseCase::class)

        @Mock
        val fetchSelfClientsFromRemote = mock(FetchSelfClientsFromRemoteUseCase::class)

        @Mock
        val observeLegalHoldStateForUser = mock(ObserveLegalHoldStateForUserUseCase::class)

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        @Mock
        val legalHoldSystemMessagesHandler = configure(mock(LegalHoldSystemMessagesHandler::class)) { stubsUnitByDefault = true }

        init {
            withObserveLegalHoldStateForUserSuccess(LegalHoldState.Disabled)
            withFetchSelfClientsFromRemoteSuccess()
            withDeleteLegalHoldRequestSuccess()
        }

        fun arrange() =
            this to LegalHoldHandlerImpl(
                selfUserId = TestUser.SELF.id,
                persistOtherUserClients = persistOtherUserClients,
                fetchSelfClientsFromRemote = fetchSelfClientsFromRemote,
                observeLegalHoldStateForUser = observeLegalHoldStateForUser,
                userConfigRepository = userConfigRepository,
                legalHoldSystemMessagesHandler = legalHoldSystemMessagesHandler,
            )

        fun withDeleteLegalHoldSuccess() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::deleteLegalHoldRequest)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))
        }

        fun withSetLegalHoldChangeNotifiedSuccess() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setLegalHoldChangeNotified)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withFetchSelfClientsFromRemoteSuccess() = apply {
            given(fetchSelfClientsFromRemote)
                .suspendFunction(fetchSelfClientsFromRemote::invoke)
                .whenInvoked()
                .thenReturn(SelfClientsResult.Success(emptyList(), ClientId("client-id")))
        }

        fun withObserveLegalHoldStateForUserSuccess(state: LegalHoldState) = apply {
            given(observeLegalHoldStateForUser)
                .suspendFunction(observeLegalHoldStateForUser::invoke)
                .whenInvokedWith(any())
                .thenReturn(flowOf(state))
        }

        fun withDeleteLegalHoldRequestSuccess() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::deleteLegalHoldRequest)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))
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
