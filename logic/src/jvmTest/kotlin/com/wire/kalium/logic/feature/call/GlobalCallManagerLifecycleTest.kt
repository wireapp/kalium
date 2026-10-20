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

package com.wire.kalium.logic.feature.call

import com.wire.kalium.calling.JvmCallingProvider
import com.wire.kalium.calling.LifecycleCalling
import com.wire.kalium.calling.callbacks.ReadyHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.mapper.CallMapperImpl
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.logic.feature.call.usecase.EpochInfoUpdater
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.util.DummyCallManager
import com.wire.kalium.logic.util.PlatformContext
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.network.CurrentNetwork
import com.wire.kalium.network.NetworkStateObserver
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

internal class GlobalCallManagerLifecycleTest {
    @Test
    fun disabledCallingAndNetworkChangesDoNotInitializeAndStartupFailurePropagates() = runTest {
        var initializationCount = 0
        val startupFailure = IllegalStateException("Fake startup failure")
        every { native.initialize() } calls {
            initializationCount++
            throw startupFailure
        }
        val observer = mock<NetworkStateObserver>()
        every { observer.observeCurrentNetwork() } returns MutableStateFlow<CurrentNetwork?>(null)
        JvmCallingProvider.register(native)
        JvmCallingProvider.register(native)
        assertFailsWith<IllegalStateException> { JvmCallingProvider.register(mock<LifecycleCalling>()) }
        val global = GlobalCallManager(backgroundScope, observer, PlatformContext())
        global.networkChanged()
        assertIs<DummyCallManager>(global.manager(observer, enableCalling = false))
        global.networkChanged()
        assertEquals(0, initializationCount)
        assertSame(startupFailure, assertFailsWith<IllegalStateException> { global.manager(observer, enableCalling = true) })
        global.networkChanged()
        assertEquals(1, initializationCount)
        global.removeInMemoryCallingManagerForUser(UserId("self", "domain"))
    }

    @Test
    fun failedNativeReleaseKeepsTheGlobalHolderUntilRetrySucceeds() = runBlocking {
        every { media.readiness } returns MutableStateFlow(JvmMediaReadiness.Ready)
        JvmCallMediaProvider.register(media)
        every { native.initialize() } returns Unit
        every {
            native.wcall_create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } calls { args ->
            (args.args[2] as ReadyHandler).onReady(1, null)
            Handle(42)
        }
        var releaseAttempts = 0
        everySuspend { native.releaseHandle(any()) } calls {
            releaseAttempts++
            if (releaseAttempts == 1) error("Fake native shutdown timeout")
        }
        JvmCallingProvider.register(native)
        val observer = mock<NetworkStateObserver>()
        every { observer.observeCurrentNetwork() } returns MutableStateFlow<CurrentNetwork?>(null)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val global = GlobalCallManager(scope, observer, PlatformContext())
            val manager = global.manager(observer, enableCalling = true) as CallManagerImpl
            manager.waitUntilInitialized()
            val userId = UserId("self", "domain")
            assertFailsWith<IllegalStateException> { global.removeInMemoryCallingManagerForUser(userId) }
            assertSame(manager, global.manager(observer, enableCalling = true))
            global.removeInMemoryCallingManagerForUser(userId)
            assertEquals(2, releaseAttempts)
            assertNotSame(manager, global.manager(observer, enableCalling = true))
            global.removeInMemoryCallingManagerForUser(userId)
        } finally {
            scope.cancel()
        }
    }

    private fun GlobalCallManager.manager(observer: NetworkStateObserver, enableCalling: Boolean): CallManager {
        val qualifiedIdMapper = mock<QualifiedIdMapper>()
        val currentClientIdProvider = mock<CurrentClientIdProvider>()
        everySuspend { currentClientIdProvider() } returns ClientId("client").right()
        val federatedIdMapper = mock<FederatedIdMapper>()
        everySuspend { federatedIdMapper.parseToFederatedId(any<QualifiedID>()) } returns "self@domain"
        return getCallManagerForClient(
            userId = QualifiedID("self", "domain"),
            callRepository = mock<CallRepository>(),
            currentClientIdProvider = currentClientIdProvider,
            selfConversationIdProvider = mock<SelfConversationIdProvider>(),
            conversationRepository = mock(),
            userConfigRepository = mock<UserConfigRepository>(),
            messageSender = mock<MessageSender>(),
            callMapper = CallMapperImpl(qualifiedIdMapper),
            federatedIdMapper = federatedIdMapper,
            qualifiedIdMapper = qualifiedIdMapper,
            videoStateChecker = mock<VideoStateChecker>(),
            conversationClientsInCallUpdater = mock<ConversationClientsInCallUpdater>(),
            epochInfoUpdater = mock<EpochInfoUpdater>(),
            getCallConversationType = mock<GetCallConversationTypeProvider>(),
            networkStateObserver = observer,
            kaliumConfigs = KaliumConfigs(enableCalling = enableCalling),
            createAndPersistRecentlyEndedCallMetadata = mock<CreateAndPersistRecentlyEndedCallMetadataUseCase>()
        )
    }

    private companion object {
        val native = mock<LifecycleCalling>(MockMode.autoUnit)
        val media = mock<JvmCallMedia>(MockMode.autoUnit)
    }
}
