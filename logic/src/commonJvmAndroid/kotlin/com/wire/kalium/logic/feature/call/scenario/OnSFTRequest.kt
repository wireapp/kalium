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

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.SFTRequestHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.AvsCallBackError
import com.wire.kalium.logic.feature.call.AvsSFTError
import com.wire.kalium.logic.feature.call.CallManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import com.wire.kalium.common.functional.nullableFold
import kotlinx.coroutines.launch

// TODO(testing): create unit test
class OnSFTRequest(
    private val handle: Deferred<Handle>,
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val callingScope: CoroutineScope
) : SFTRequestHandler {
    override fun onSFTRequest(ctx: Pointer?, url: String, data: Pointer?, length: Size_t, arg: Pointer?): Int {
        callingLogger.i("[OnSFTRequest] -> Start")

        val dataString = data?.getString(0, CallManagerImpl.UTF8_ENCODING)
        callingLogger.i("[OnSFTRequest] -> Connecting to SFT Server: $url")
        callingLogger.i("[OnSFTRequest] -> Connecting to SFT Server with data: $dataString")

        callingScope.launch {
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

}
