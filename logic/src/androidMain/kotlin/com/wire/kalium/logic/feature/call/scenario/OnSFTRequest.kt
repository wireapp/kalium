package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.SFTRequestHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.AvsCallBackError
import com.wire.kalium.logic.feature.call.AvsSFTError
import com.wire.kalium.logic.feature.call.CallManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

//TODO create unit test
class OnSFTRequest(
    private val handle: Deferred<Handle>,
    private val calling: Calling,
    private val callRepository: CallRepository
) : SFTRequestHandler {
    override fun onSFTRequest(ctx: Pointer?, url: String, data: Pointer?, length: Size_t, arg: Pointer?): Int {
        //TODO use the same coroutine job of call manager
        CoroutineScope(Dispatchers.IO).launch {
            val dataString = data?.getString(0, CallManagerImpl.UTF8_ENCODING)
            dataString?.let {
                val responseData = callRepository.connectToSFT(
                    url = url,
                    data = dataString
                ).fold({
                    callingLogger.i("Could not connect to SFT server.")
                    null
                }, {
                    callingLogger.i("Connected to SFT server.")
                    it
                })

                onSFTResponse(data = responseData, context = ctx)
            }
        }

        callingLogger.i("${CallManagerImpl.TAG} -> sftRequestHandler")
        return AvsCallBackError.NONE.value
    }

    private suspend fun onSFTResponse(data: ByteArray?, context: Pointer?) {
        val responseData = data ?: byteArrayOf()
        calling.wcall_sft_resp(
            inst = handle.await(),
            error = data?.let { AvsSFTError.NONE.value } ?: AvsSFTError.NO_RESPONSE_DATA.value,
            data = responseData,
            length = responseData.size,
            ctx = context
        )
        callingLogger.i("SFT Response sent.")
    }

}
