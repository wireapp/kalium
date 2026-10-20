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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.util.PlatformContext
import com.wire.kalium.logic.util.PlatformRotation
import com.wire.kalium.logic.util.PlatformView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class JvmCallMediaProviderTest {
    @Test
    fun registrationIsIdempotentOnlyForSameProviderInstance() {
        val registry = JvmCallMediaRegistry()
        val provider = FakeMedia()
        registry.register(provider)
        registry.register(provider)
        assertSame(provider, registry.requireMedia())
        assertFailsWith<IllegalStateException> { registry.register(FakeMedia()) }
        assertSame(provider, registry.requireMedia())
    }

    @Test
    fun scopedRegistriesDoNotShareProviders() {
        val first = JvmCallMediaRegistry()
        val second = JvmCallMediaRegistry()
        first.register(FakeMedia())
        assertFailsWith<IllegalStateException> { second.requireMedia() }
    }

    @Test
    fun unregisteredServicesFailWithActionableRegistrationInstruction() = runTest {
        val registry = JvmCallMediaRegistry()
        val media = MediaManagerServiceImpl(PlatformContext(), backgroundScope, registry)
        val flow = FlowManagerServiceImpl(PlatformContext(), backgroundScope, registry)
        val failure = assertFailsWith<IllegalStateException> { media.startMediaManager() }
        assertTrue(failure.message.orEmpty().contains("JvmCallMediaProvider.register"))
        assertFailsWith<IllegalStateException> { flow.startFlowManager() }
        assertFailsWith<IllegalStateException> { media.observeSpeaker() }
    }

    @Test
    fun managerStartupFailuresPropagateWithoutChangingReadiness() = runTest {
        val provider = FakeMedia()
        val failure = IllegalStateException("Actual device startup failed")
        provider.startupFailure = failure
        val registry = registry(provider)
        val media = MediaManagerServiceImpl(PlatformContext(), backgroundScope, registry)
        val flow = FlowManagerServiceImpl(PlatformContext(), backgroundScope, registry)
        assertSame(failure, assertFailsWith<IllegalStateException> { media.startMediaManager() })
        assertSame(failure, assertFailsWith<IllegalStateException> { flow.startFlowManager() })
        assertEquals(JvmMediaReadiness.Unprepared, provider.readiness.value)
        assertEquals(listOf("media", "flow"), provider.starts)
    }

    @Test
    fun accountRuntimeStartupDoesNotRequireOrManufactureDeviceReadiness() = runTest {
        val provider = FakeMedia()
        val registry = registry(provider)
        val media = MediaManagerServiceImpl(PlatformContext(), backgroundScope, registry)
        val flow = FlowManagerServiceImpl(PlatformContext(), backgroundScope, registry)
        for (state in listOf(JvmMediaReadiness.Unprepared, JvmMediaReadiness.Prepared)) {
            provider.startResult = state
            media.startMediaManager()
            flow.startFlowManager()
            assertEquals(state, provider.readiness.value)
        }
    }

    @Test
    fun servicesDelegateReadyStartupAndSurfaceCommandsToProvider() = runTest {
        val provider = FakeMedia()
        val registry = registry(provider)
        val media = MediaManagerServiceImpl(PlatformContext(), backgroundScope, registry)
        val flow = FlowManagerServiceImpl(PlatformContext(), backgroundScope, registry)
        val conversation = ConversationId("conversation", "wire.com")
        val rotation = PlatformRotation()
        media.startMediaManager()
        flow.startFlowManager()
        media.turnLoudSpeakerOn()
        media.turnLoudSpeakerOff()
        flow.setVideoPreview(conversation, PlatformView(17))
        flow.setVideoPreview(conversation, PlatformView(0))
        flow.setUIRotation(rotation)
        flow.flipToFrontCamera(conversation)
        flow.flipToBackCamera(conversation)
        assertEquals(listOf("media", "flow"), provider.starts)
        assertEquals(listOf(true, false), provider.speakerRequests)
        assertEquals(listOf(conversation to 17, conversation to 0), provider.previews)
        assertSame(rotation, provider.rotation)
        assertEquals(listOf(conversation to true, conversation to false), provider.cameraRequests)
    }

    @Test
    fun negativeSurfaceTokenFailsBeforeProviderInvocation() = runTest {
        val provider = FakeMedia()
        val flow = FlowManagerServiceImpl(PlatformContext(), backgroundScope, registry(provider))
        assertFailsWith<IllegalArgumentException> {
            flow.setVideoPreview(ConversationId("conversation", "wire.com"), PlatformView(-1))
        }
        assertTrue(provider.previews.isEmpty())
    }

    @Test
    fun speakerObservationEmitsOnlyConfirmedHardwareRoutesAndDistinctValues() = runTest {
        val provider = FakeMedia()
        val media = MediaManagerServiceImpl(PlatformContext(), backgroundScope, registry(provider))
        val observed = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            media.observeSpeaker().collect { observed.add(it) }
        }
        assertTrue(observed.isEmpty())
        provider.route.value = JvmAudioRouteState.Unsupported
        assertTrue(observed.isEmpty())
        provider.route.value = JvmAudioRouteState.Confirmed("device-a", true)
        provider.route.value = JvmAudioRouteState.Confirmed("device-b", true)
        provider.route.value = JvmAudioRouteState.Unknown
        provider.route.value = JvmAudioRouteState.Confirmed("device-a", false)
        assertEquals(listOf(true, false), observed)
    }

    @Test
    fun unsupportedRequestedSpeakerRouteCannotEmitConfirmedFalse() = runTest {
        val provider = FakeMedia().apply { speakerUnsupported = true }
        val media = MediaManagerServiceImpl(PlatformContext(), backgroundScope, registry(provider))
        val observed = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            media.observeSpeaker().collect { observed.add(it) }
        }
        assertFailsWith<UnsupportedOperationException> { media.turnLoudSpeakerOff() }
        assertFailsWith<UnsupportedOperationException> { media.turnLoudSpeakerOn() }
        assertEquals(JvmAudioRouteState.Unsupported, provider.route.value)
        assertTrue(observed.isEmpty())
    }

    @Test
    fun unsupportedCameraSelectionPropagatesExplicitly() = runTest {
        val provider = FakeMedia().apply { cameraUnsupported = true }
        val flow = FlowManagerServiceImpl(PlatformContext(), backgroundScope, registry(provider))
        val conversation = ConversationId("conversation", "wire.com")
        assertFailsWith<UnsupportedOperationException> { flow.flipToFrontCamera(conversation) }
        assertFailsWith<UnsupportedOperationException> { flow.flipToBackCamera(conversation) }
        assertTrue(provider.cameraRequests.isEmpty())
    }

    private fun registry(provider: JvmCallMedia) = JvmCallMediaRegistry().apply { register(provider) }

    private class FakeMedia : JvmCallMedia {
        override val readiness = MutableStateFlow<JvmMediaReadiness>(JvmMediaReadiness.Unprepared)
        override val route = MutableStateFlow<JvmAudioRouteState>(JvmAudioRouteState.Unknown)
        var startResult: JvmMediaReadiness = JvmMediaReadiness.Ready
        var startupFailure: IllegalStateException? = null
        var speakerUnsupported = false
        var cameraUnsupported = false
        val starts = mutableListOf<String>()
        val speakerRequests = mutableListOf<Boolean>()
        val previews = mutableListOf<Pair<ConversationId, Int>>()
        val cameraRequests = mutableListOf<Pair<ConversationId, Boolean>>()
        var rotation: PlatformRotation? = null

        override suspend fun startMediaManager() = start("media")
        override suspend fun startFlowManager() = start("flow")

        private fun start(manager: String) {
            starts.add(manager)
            startupFailure?.let { throw it }
            readiness.value = startResult
        }

        override suspend fun setSpeaker(enabled: Boolean) {
            if (speakerUnsupported) {
                route.value = JvmAudioRouteState.Unsupported
                throw UnsupportedOperationException("This backend cannot confirm hardware speaker routing")
            }
            speakerRequests.add(enabled)
        }

        override suspend fun setVideoPreview(conversationId: ConversationId, surfaceToken: Int) {
            previews.add(conversationId to surfaceToken)
        }

        override suspend fun setUIRotation(rotation: PlatformRotation) {
            this.rotation = rotation
        }

        override suspend fun flipCamera(conversationId: ConversationId, front: Boolean) {
            if (cameraUnsupported) throw UnsupportedOperationException("Camera selection is unsupported")
            cameraRequests.add(conversationId to front)
        }
    }
}
