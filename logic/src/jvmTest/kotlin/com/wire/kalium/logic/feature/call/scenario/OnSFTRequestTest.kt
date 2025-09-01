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
package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Memory
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.AvsSFTError
import com.wire.kalium.logic.feature.call.CallManagerImpl
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class OnSFTRequestTest {
    private val testScope = TestScope()

    @Test
    fun givenSuccess_whenCallingOnSFTRequest_thenReturnResult() = testScope.runTest {
        val networkStateFlow = MutableStateFlow<NetworkState>(NetworkState.ConnectedWithInternet)
        val result = "result".toByteArray()
        val (arrangement, onSFTRequest) = Arrangement(testScope)
            .withNetworkStateFlow(networkStateFlow)
            .withConnectToSFTResult(result.right())
            .arrange()

        val url = "url"
        val content = "calling_content"
        val memory = Memory((content.length + 1).toLong())
        memory.setString(0, content, CallManagerImpl.UTF8_ENCODING)

        onSFTRequest.onSFTRequest(ctx = null, url = url, data = memory.share(0), length = Size_t(memory.size()), arg = null)
        advanceTimeBy(1.seconds)

        verify {
            arrangement.calling.wcall_sft_resp(
                inst = eq(arrangement.handle),
                error = eq(AvsSFTError.NONE.value),
                data = matches { it.isNotEmpty() && it.contentEquals(result) },
                length = eq(result.size),
                ctx = any()
            )
        }.wasInvoked(1)
    }

    @Test
    fun givenFailure_whenCallingOnSFTRequest_thenReturnNull() = testScope.runTest {
        val networkStateFlow = MutableStateFlow<NetworkState>(NetworkState.ConnectedWithInternet)
        val (arrangement, onSFTRequest) = Arrangement(testScope)
            .withNetworkStateFlow(networkStateFlow)
            .withConnectToSFTResult(CoreFailure.Unknown(Throwable("error")).left())
            .arrange()

        val url = "url"
        val content = "calling_content"
        val memory = Memory((content.length + 1).toLong())
        memory.setString(0, content, CallManagerImpl.UTF8_ENCODING)

        onSFTRequest.onSFTRequest(ctx = null, url = url, data = memory.share(0), length = Size_t(memory.size()), arg = null)
        advanceTimeBy(1.seconds)

        verify {
            arrangement.calling.wcall_sft_resp(
                inst = eq(arrangement.handle),
                error = eq(AvsSFTError.NO_RESPONSE_DATA.value),
                data = matches { it.isEmpty() },
                length = eq(0),
                ctx = any()
            )
        }.wasInvoked(1)
    }

    @Test
    fun givenNoNetwork_whenCallingOnSFTRequest_andNetworkConnectsBackWithinTimeout_thenReturnResult() = testScope.runTest {
        val networkStateFlow = MutableStateFlow<NetworkState>(NetworkState.NotConnected)
        val result = "result".toByteArray()
        val (arrangement, onSFTRequest) = Arrangement(testScope)
            .withNetworkStateFlow(networkStateFlow)
            .withConnectToSFTResult(result.right())
            .arrange()

        val url = "url"
        val content = "calling_content"
        val memory = Memory((content.length + 1).toLong())
        memory.setString(0, content, CallManagerImpl.UTF8_ENCODING)

        onSFTRequest.onSFTRequest(ctx = null, url = url, data = memory.share(0), length = Size_t(memory.size()), arg = null)
        advanceTimeBy(1.seconds)

        networkStateFlow.value = NetworkState.ConnectedWithInternet
        advanceTimeBy(1.seconds)

        coVerify {
            arrangement.callRepository.connectToSFT(url = url, data = content)
        }.wasInvoked(1)
        verify {
            arrangement.calling.wcall_sft_resp(
                inst = eq(arrangement.handle),
                error = eq(AvsSFTError.NONE.value),
                data = matches { it.isNotEmpty() && it.contentEquals(result) },
                length = eq(result.size),
                ctx = any()
            )
        }.wasInvoked(1)
    }

    @Test
    fun givenNoNetwork_whenCallingOnSFTRequest_andNetworkDoesNotConnectBackWithinTimeout_thenReturnNull() = testScope.runTest {
        val networkStateFlow = MutableStateFlow<NetworkState>(NetworkState.NotConnected)
        val (arrangement, onSFTRequest) = Arrangement(testScope)
            .withNetworkStateFlow(networkStateFlow)
            .arrange()

        val url = "url"
        val content = "calling_content"
        val memory = Memory((content.length + 1).toLong())
        memory.setString(0, content, CallManagerImpl.UTF8_ENCODING)

        onSFTRequest.onSFTRequest(
            ctx = null,
            url = url,
            data = memory.share(0),
            length = Size_t(memory.size()),
            arg = null,
        )
        advanceTimeBy(arrangement.timeout + 1.seconds)

        coVerify {
            arrangement.callRepository.connectToSFT(url = url, data = content)
        }.wasInvoked(0)
        verify {
            arrangement.calling.wcall_sft_resp(
                inst = eq(arrangement.handle),
                error = eq(AvsSFTError.NO_RESPONSE_DATA.value),
                data = matches { it.isEmpty() },
                length = eq(0),
                ctx = any()
            )
        }.wasInvoked(1)
    }

    private class Arrangement(val testScope: TestScope) {
        val calling = mock(Calling::class)
        val callRepository: CallRepository = mock(CallRepository::class)
        val networkStateObserver = mock(NetworkStateObserver::class)
        val handle = Handle(42)
        val timeout = 15.seconds

        init {
            withNetworkStateFlow(MutableStateFlow(NetworkState.ConnectedWithInternet))
        }

        fun arrange() = this to OnSFTRequest(
            handle = testScope.async { handle },
            calling = calling,
            callRepository = callRepository,
            callingScope = testScope,
            networkStateObserver = networkStateObserver,
            waitUntilConnectedTimeout = timeout,
        )

        fun withNetworkStateFlow(networkStateFlow: StateFlow<NetworkState>): Arrangement = apply {
            every {
                networkStateObserver.observeNetworkState()
            }.returns(networkStateFlow)
        }

        suspend fun withConnectToSFTResult(result: Either<CoreFailure, ByteArray>): Arrangement = apply {
            coEvery {
                callRepository.connectToSFT(url = any(), data = any())
            }.returns(result)
        }
    }
}
