package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationsUseCase
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test

class MLSClientManagerTest {

    @Test
    fun givenMLSSupportIsDisabled_whenObservingSyncFinishes_thenMLSClientIsNotRegistered() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(false)
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verify(arrangement.registerMLSClient)
                .suspendFunction(arrangement.registerMLSClient::invoke)
                .with(any())
                .wasNotInvoked()
        }

    @Test
    fun givenMLSClientIsNotRegistered_whenObservingSyncFinishes_thenMLSClientIsRegistered() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(Either.Right(false))
                .withCurrentClientId(Either.Right(TestClient.CLIENT_ID))
                .withRegisterMLSClientSuccessful()
                .withJoinExistingMLSConversationsSuccessful()
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verify(arrangement.registerMLSClient)
                .suspendFunction(arrangement.registerMLSClient::invoke)
                .with(any())
                .wasInvoked(once)

            verify(arrangement.joinExistingMLSConversations)
                .suspendFunction(arrangement.joinExistingMLSConversations::invoke)
                .wasInvoked(once)
        }

    @Test
    fun givenMLSClientIsRegistered_whenObservingSyncFinishes_thenMLSClientIsNotRegistered() =
        runTest(TestKaliumDispatcher.default) {
            val (arrangement, _) = Arrangement()
                .withIsMLSSupported(true)
                .withHasRegisteredMLSClient(Either.Right(true))
                .arrange()

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verify(arrangement.registerMLSClient)
                .suspendFunction(arrangement.registerMLSClient::invoke)
                .with(any())
                .wasNotInvoked()
        }

    private class Arrangement {

        val incrementalSyncRepository: IncrementalSyncRepository = InMemoryIncrementalSyncRepository()

        @Mock
        var clientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val featureSupport = mock(classOf<FeatureSupport>())

        @Mock
        val registerMLSClient = mock(classOf<RegisterMLSClientUseCase>())

        @Mock
        val joinExistingMLSConversations = mock(classOf<JoinExistingMLSConversationsUseCase>())

        fun withCurrentClientId(result: Either<CoreFailure, ClientId>) = apply {
            given(clientIdProvider)
                .suspendFunction(clientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withHasRegisteredMLSClient(result: Either<CoreFailure, Boolean>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::hasRegisteredMLSClient)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withRegisterMLSClientSuccessful() = apply {
            given(registerMLSClient)
                .suspendFunction(registerMLSClient::invoke)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withJoinExistingMLSConversationsSuccessful() = apply {
            given(joinExistingMLSConversations)
                .suspendFunction(joinExistingMLSConversations::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))
        }

        fun withIsMLSSupported(supported: Boolean) = apply {
            given(featureSupport)
                .invocation { featureSupport.isMLSSupported }
                .thenReturn(supported)
        }

        fun arrange() = this to MLSClientManagerImpl(
            clientIdProvider,
            featureSupport,
            incrementalSyncRepository,
            lazy { clientRepository },
            lazy { registerMLSClient },
            lazy { joinExistingMLSConversations },
            TestKaliumDispatcher
        )
    }
}
