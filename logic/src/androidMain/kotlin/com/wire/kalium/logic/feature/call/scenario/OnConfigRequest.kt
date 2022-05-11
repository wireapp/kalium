package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.CallConfigRequestHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.AvsCallBackError
import com.wire.kalium.logic.feature.call.CallManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

//TODO create unit test
class OnConfigRequest(
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val callingScope: CoroutineScope
) : CallConfigRequestHandler {
    override fun onConfigRequest(inst: Handle, arg: Pointer?): Int {
        callingLogger.i("${CallManagerImpl.TAG} - onConfigRequest")

        callingScope.launch {
            val config = callRepository.getCallConfigResponse(limit = null)
                .fold({
                    TODO("")
                }, {
                    it
                })

            calling.wcall_config_update(
                inst = inst,
                error = 0, // TODO: http error from internal json
                jsonString = config
            )

            callingLogger.i("${CallManagerImpl.TAG} - onConfigRequest")
        }

        return AvsCallBackError.NONE.value
    }
}
