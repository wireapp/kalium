package com.wire.kalium.logic.feature.call

import android.content.Context
import com.waz.call.FlowManager
import com.waz.log.LogHandler
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.util.PlatformView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

actual class FlowManagerServiceImpl(
    appContext: Context,
    private val dispatcher: CoroutineDispatcher
) : FlowManagerService {

    private val flowManager: FlowManager = FlowManager(
        appContext
    ) { manager, path, method, ctype, content, ctx ->
        // TODO(Calling) Not yet implemented
        callingLogger.i("FlowManager -> RequestHandler -> $path : $method")
        0
    }.also {
        it.setEnableLogging(true)
        it.setLogHandler(object : LogHandler {
            override fun append(msg: String?) {
                callingLogger.i("FlowManager -> Logger -> Append -> $msg")
            }

            override fun upload() {
                callingLogger.i("FlowManager -> Logger -> upload")
            }
        })
    }

    override suspend fun setVideoPreview(conversationId: ConversationId, platformView: PlatformView) {
        withContext(dispatcher) {
            flowManager.setVideoPreview(conversationId.toString(), platformView.view)
        }
    }

    override fun setUIRotation(rotation: Int) {
        flowManager.setUIRotation(rotation)
    }
}
