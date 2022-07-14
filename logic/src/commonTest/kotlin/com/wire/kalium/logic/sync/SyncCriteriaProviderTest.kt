package com.wire.kalium.logic.sync

import app.cash.turbine.test
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.framework.TestClient
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class SyncCriteriaProviderTest {

    @Test
    fun givenClientIsNull_whenCollectingStartCriteriaFlow_thenShouldBeMissingCriteria() = runTest {
        // Given
        val clientFlow = flowOf<ClientId?>(null)

        val (_, syncCriteriaProvider) = Arrangement()
            .withObserveClientReturning(clientFlow)
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
        clientChannel.send(null)

        val (_, syncCriteriaProvider) = Arrangement()
            .withObserveClientReturning(clientChannel.consumeAsFlow())
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
        clientChannel.send(TestClient.CLIENT_ID)

        val (_, syncCriteriaProvider) = Arrangement()
            .withObserveClientReturning(clientChannel.consumeAsFlow())
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

        val (_, syncCriteriaProvider) = Arrangement()
            .withObserveClientReturning(flowOf(TestClient.CLIENT_ID))
            .withObserveLogoutReturning(logoutReasonsChannel.consumeAsFlow())
            .arrange()

        // When
        val result = syncCriteriaProvider.syncCriteriaFlow()

        // Then
        result.test {
            assertIs<SyncCriteriaResolution.Ready>(awaitItem())

            // Update client Id
            logoutReasonsChannel.send(LogoutReason.EXPIRED_SESSION)
            assertIs<SyncCriteriaResolution.MissingRequirement>(awaitItem())

            cancelAndConsumeRemainingEvents()
        }
    }

    private class Arrangement {

        @Mock
        private val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        private val logoutRepository = mock(classOf<LogoutRepository>())

        private val syncCriteriaProvider = SyncCriteriaProviderImpl(clientRepository, logoutRepository)

        fun withObserveClientReturning(flow: Flow<ClientId?>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::observeCurrentClientId)
                .whenInvoked()
                .thenReturn(flow)
        }

        fun withObserveLogoutReturning(flow: Flow<LogoutReason>) = apply {
            given(logoutRepository)
                .suspendFunction(logoutRepository::observeLogout)
                .whenInvoked()
                .thenReturn(flow)
        }

        fun withNoLogouts() = apply {
            given(logoutRepository)
                .suspendFunction(logoutRepository::observeLogout)
                .whenInvoked()
                .thenReturn(emptyFlow())
        }

        fun arrange() = this to syncCriteriaProvider

    }
}
