package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.CallConfigRequestHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.AvsCallBackError
import com.wire.kalium.logic.functional.fold
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
                    callingLogger.i("[OnConfigRequest] - Error: $it")
                    // TODO: Add a better way to handle the Core Failure?
                }, { config ->
                    calling.wcall_config_update(
                        inst = inst,
                        error = 0, // TODO(calling): http error from internal json
                        jsonString = config
                    )
                    callingLogger.i("[OnConfigRequest] - wcall_config_update()")
                })
        }

        return AvsCallBackError.NONE.value
    }
}
