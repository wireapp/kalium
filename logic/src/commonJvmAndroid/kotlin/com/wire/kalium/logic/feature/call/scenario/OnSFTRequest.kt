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

import com.wire.kalium.calling.AvsSftRequestHandler
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.SFTRequestHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.common.functional.nullableFold
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.AvsCallBackError
import com.wire.kalium.logic.feature.call.AvsSFTError
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val DEFAULT_WAIT_UNTIL_CONNECTED_TIMEOUT = 15.seconds // equal to AVS connect_timeout

@Suppress("FunctionNaming", "LongParameterList")
internal fun OnSFTRequest(
    handle: Deferred<Handle>,
    calling: Calling,
    callRepository: CallRepository,
    callingScope: CoroutineScope,
    networkStateObserver: NetworkStateObserver,
    waitUntilConnectedTimeout: Duration = DEFAULT_WAIT_UNTIL_CONNECTED_TIMEOUT,
): SFTRequestHandler = AvsSftRequestHandler(
        scope = callingScope,
        connect = { url, payload ->
            callingLogger.i("[OnSFTRequest] -> Start")
            callingLogger.i("[OnSFTRequest] -> Waiting until connected to internet (timeout: $waitUntilConnectedTimeout)")
            val connected = withTimeoutOrNull(waitUntilConnectedTimeout) {
                networkStateObserver.observeNetworkState().firstOrNull { it is NetworkState.ConnectedWithInternet }
            } != null
            if (!connected) {
                callingLogger.e("[OnSFTRequest] -> Not connected to the Internet within timeout, cannot proceed with SFT request")
                null
            } else {
                val dataString = payload.decodeToString()
                callingLogger.i("[OnSFTRequest] -> Connecting to SFT Server: $url")
                callRepository.connectToSFT(url, dataString).nullableFold(
                    {
                        callingLogger.i("[OnSFTRequest] -> Could not connect to SFT Server: $url")
                        null
                    },
                    {
                        callingLogger.i("[OnSFTRequest] -> Connected to SFT Server: $url")
                        it
                    },
                )
            }
        },
        respond = { context, response ->
            callingLogger.i("[OnSFTRequest] -> Sending SFT Response (${response?.size} bytes)")
            val responseData = response ?: byteArrayOf()
            calling.wcall_sft_resp(
                inst = handle.await(),
                error = response?.let { AvsSFTError.NONE.value } ?: AvsSFTError.NO_RESPONSE_DATA.value,
                data = responseData,
                length = responseData.size,
                ctx = context,
            )
            callingLogger.i("[OnSFTRequest] -> wcall_sft_resp() called")
        },
        callbackResult = AvsCallBackError.NONE.value,
    )
