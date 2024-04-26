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

package com.wire.kalium.logic.sync.slow

import app.cash.turbine.test
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.framework.TestClient
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class SlowSyncCriteriaProviderTest {

    @Test
    fun givenClientIsNull_whenCollectingStartCriteriaFlow_thenShouldBeMissingCriteria() = runTest {
        // Given
        val clientFlow = flowOf<ClientId?>(null)
        val e2eiIsRequiredFlow = flowOf<Boolean?>(null)

        val (_, syncCriteriaProvider) = Arrangement()
            .withObserveClientReturning(clientFlow)
            .withObserveIsClientRegistrationBlockedByE2EIReturning(e2eiIsRequiredFlow)
            .withNoLogouts()
            .arrange()

        // When
        val result = syncCriteriaProvider.syncCriteriaFlow()

        // Then
        result.test {
            assertIs<SyncCriteriaResolution.MissingRequirement>(awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun givenE2EIIsRequired_whenCollectingStartCriteriaFlow_thenShouldBeMissingCriteria() = runTest {
        // Given
        val clientFlow = flowOf<ClientId?>(TestClient.CLIENT_ID)
        val e2eiIsRequiredFlow = flowOf<Boolean?>(true)

        val (_, syncCriteriaProvider) = Arrangement()
            .withObserveClientReturning(clientFlow)
            .withObserveIsClientRegistrationBlockedByE2EIReturning(e2eiIsRequiredFlow)
            .withNoLogouts()
            .arrange()

        // When
        val result = syncCriteriaProvider.syncCriteriaFlow()

        // Then
        result.test {
            assertIs<SyncCriteriaResolution.MissingRequirement>(awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }


    @Test
    fun givenClientIsFirstNullAndThenRegistered_whenCollectingStartCriteriaFlow_thenCriteriaShouldBeMissingThenReady() = runTest {
        // Given
        val clientChannel = Channel<ClientId?>(Channel.UNLIMITED)
        val e2eiIsRequiredFlow = flowOf<Boolean?>(null)
        clientChannel.send(null)

        val (_, syncCriteriaProvider) = Arrangement()
            .withObserveClientReturning(clientChannel.consumeAsFlow())
            .withObserveIsClientRegistrationBlockedByE2EIReturning(e2eiIsRequiredFlow)
            .withNoLogouts()
            .arrange()

        // When
        val result = syncCriteriaProvider.syncCriteriaFlow()

        // Then
        result.test {
            assertIs<SyncCriteriaResolution.MissingRequirement>(awaitItem())

            // Update client Id
            clientChannel.send(TestClient.CLIENT_ID)
            assertIs<SyncCriteriaResolution.Ready>(awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun givenClientIsRegisteredAndThenNull_whenCollectingStartCriteriaFlow_thenCriteriaShouldBeReadyThenMissing() = runTest {
        // Given
        val clientChannel = Channel<ClientId?>(Channel.UNLIMITED)
        val e2eiIsRequiredFlow = flowOf<Boolean?>(null)
        clientChannel.send(TestClient.CLIENT_ID)

        val (_, syncCriteriaProvider) = Arrangement()
            .withObserveClientReturning(clientChannel.consumeAsFlow())
            .withObserveIsClientRegistrationBlockedByE2EIReturning(e2eiIsRequiredFlow)
            .withNoLogouts()
            .arrange()

        // When
        val result = syncCriteriaProvider.syncCriteriaFlow()

        // Then
        result.test {
            assertIs<SyncCriteriaResolution.Ready>(awaitItem())

            // Update client Id
            clientChannel.send(null)
            assertIs<SyncCriteriaResolution.MissingRequirement>(awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun givenLogoutHappens_whenCollectingStartCriteriaFlow_thenCriteriaShouldGoFromReadyToMissing() = runTest {
        // Given
        val logoutReasonsChannel = Channel<LogoutReason>()
        val e2eiIsRequiredFlow = flowOf<Boolean?>(null)

        val (_, syncCriteriaProvider) = Arrangement()
            .withObserveClientReturning(flowOf(TestClient.CLIENT_ID))
            .withObserveLogoutReturning(logoutReasonsChannel.consumeAsFlow())
            .withObserveIsClientRegistrationBlockedByE2EIReturning(e2eiIsRequiredFlow)
            .arrange()

        // When
        val result = syncCriteriaProvider.syncCriteriaFlow()

        // Then
        result.test {
            assertIs<SyncCriteriaResolution.Ready>(awaitItem())

            // Cause a Logout
            logoutReasonsChannel.send(LogoutReason.SESSION_EXPIRED)
            assertIs<SyncCriteriaResolution.MissingRequirement>(awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    private class Arrangement {

        @Mock
        private val clientRepository = mock(ClientRepository::class)

        @Mock
        private val logoutRepository = mock(LogoutRepository::class)

        private val syncCriteriaProvider = SlowSlowSyncCriteriaProviderImpl(clientRepository, logoutRepository)

        suspend fun withObserveClientReturning(flow: Flow<ClientId?>) = apply {
            coEvery {
                clientRepository.observeCurrentClientId()
            }.returns(flow)
        }

        suspend fun withObserveIsClientRegistrationBlockedByE2EIReturning(flow: Flow<Boolean?>) = apply {
            coEvery {
                clientRepository::observeIsClientRegistrationBlockedByE2EI.invoke()
            }.returns(flow)
        }

        suspend fun withObserveLogoutReturning(flow: Flow<LogoutReason>) = apply {
            coEvery {
                logoutRepository.observeLogout()
            }.returns(flow)
        }

        suspend fun withNoLogouts() = apply {
            coEvery {
                logoutRepository.observeLogout()
            }.returns(emptyFlow())
        }

        fun arrange() = this to syncCriteriaProvider

    }
}
