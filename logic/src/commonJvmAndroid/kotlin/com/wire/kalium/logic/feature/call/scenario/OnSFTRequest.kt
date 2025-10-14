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

@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.SFTRequestHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import com.wire.kalium.common.functional.nullableFold
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.AvsCallBackError
import com.wire.kalium.logic.feature.call.AvsSFTError
import com.wire.kalium.logic.feature.call.CallManagerImpl
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// TODO(testing): create unit test
internal class OnSFTRequest(
    private val handle: Deferred<Handle>,
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val callingScope: CoroutineScope,
    private val networkStateObserver: NetworkStateObserver,
    private val waitUntilConnectedTimeout: Duration = DEFAULT_WAIT_UNTIL_CONNECTED_TIMEOUT,
) : SFTRequestHandler {
    override fun onSFTRequest(ctx: Pointer?, url: String, data: Pointer?, length: Size_t, arg: Pointer?): Int {
        callingLogger.i("[OnSFTRequest] -> Start")

        val dataString = data?.getString(0, CallManagerImpl.UTF8_ENCODING)

        callingScope.launch {
            callingLogger.i("[OnSFTRequest] -> Waiting until connected to internet (timeout: $waitUntilConnectedTimeout)")
            val connected = withTimeoutOrNull(waitUntilConnectedTimeout) {
                networkStateObserver.observeNetworkState().firstOrNull { it is NetworkState.ConnectedWithInternet }
            } != null
            if (!connected) {
                callingLogger.e("[OnSFTRequest] -> Not connected to the Internet within timeout, cannot proceed with SFT request")
                onSFTResponse(data = null, context = ctx)
                return@launch
            }

            callingLogger.i("[OnSFTRequest] -> Connecting to SFT Server: $url")
            callingLogger.i("[OnSFTRequest] -> Connecting to SFT Server with data: $dataString")

            dataString?.let {
                val responseData = callRepository.connectToSFT(
                    url = url,
                    data = dataString
                ).nullableFold({
                    callingLogger.i("[OnSFTRequest] -> Could not connect to SFT Server: $url")
                    null
                }, {
                    callingLogger.i("[OnSFTRequest] -> Connected to SFT Server: $url")
                    it
                })

                onSFTResponse(data = responseData, context = ctx)
            }
        }

        callingLogger.i("[OnSFTRequest] -> sftRequestHandler called")
        return AvsCallBackError.NONE.value
    }

    private suspend fun onSFTResponse(data: ByteArray?, context: Pointer?) {
        callingLogger.i("[OnSFTRequest] -> Sending SFT Response")
        val responseData = data ?: byteArrayOf()
        calling.wcall_sft_resp(
            inst = handle.await(),
            error = data?.let { AvsSFTError.NONE.value } ?: AvsSFTError.NO_RESPONSE_DATA.value,
            data = responseData,
            length = responseData.size,
            ctx = context
        )
        callingLogger.i("[OnSFTRequest] -> wcall_sft_resp() called")
    }

    companion object {
        private val DEFAULT_WAIT_UNTIL_CONNECTED_TIMEOUT = 15.seconds // equal to AVS connect_timeout
    }
}
