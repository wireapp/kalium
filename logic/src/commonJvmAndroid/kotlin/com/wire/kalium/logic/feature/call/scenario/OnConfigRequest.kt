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
import com.wire.kalium.calling.callbacks.CallConfigRequestHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.AvsCallBackError
import com.wire.kalium.common.functional.fold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// TODO(testing): create unit test
class OnConfigRequest(
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val callingScope: CoroutineScope
) : CallConfigRequestHandler {
    override fun onConfigRequest(inst: Handle, arg: Pointer?): Int {
        callingLogger.i("[OnConfigRequest] - STARTED")
        callingScope.launch {
            callRepository.getCallConfigResponse(limit = null)
                .fold({
                    callingLogger.w("[OnConfigRequest] - Error: $it")
                    // We can call config_update with an error if there was a connectivity issue
                    // AVS will eventually ask us again for the config
                    // TODO(improvement): We can retry it ourselves and improve the app responsiveness.
                    //                    Maybe add a retry mechanism that listens for the network state
                    //                    Caches the config string and exposes a "invalidate" function
                    //                    That we could call when AVS requests new config.
                    calling.wcall_config_update(
                        inst = inst,
                        error = 1,
                        jsonString = ""
                    )
                }, { config ->
                    calling.wcall_config_update(
                        inst = inst,
                        error = 0,
                        jsonString = config
                    )
                    callingLogger.i("[OnConfigRequest] - wcall_config_update()")
                })
        }

        return AvsCallBackError.NONE.value
    }
}
