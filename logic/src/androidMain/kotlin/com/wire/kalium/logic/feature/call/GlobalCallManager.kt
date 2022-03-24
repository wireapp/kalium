package com.wire.kalium.logic.feature.call

import android.content.Context
import com.sun.jna.Pointer
import com.waz.call.FlowManager
import com.waz.call.RequestHandler
import com.waz.media.manager.MediaManager
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.ENVIRONMENT_DEFAULT
import com.wire.kalium.calling.callbacks.LogHandler
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.kaliumLogger
import com.waz.log.LogHandler as NativeLogHandler

actual class GlobalCallManager(
    private val appContext: Context
) {

    private lateinit var mediaManager: MediaManager
    private lateinit var flowManager: FlowManager

    private val calling by lazy {
        initiateMediaManager()
        initiateFlowManager()
        Calling.INSTANCE.apply {
            wcall_init(env = ENVIRONMENT_DEFAULT)
            wcall_set_log_handler(
                logHandler = LogHandlerImpl,
                arg = null
            )
        }
    }

    // TODO have a storage to make sure we have only one instance of CallManger per user client combination
    actual fun getCallManagerForClient(
        callRepository: CallRepository,
        userRepository: UserRepository,
        clientRepository: ClientRepository
    ): CallManager = CallManager(
        calling = calling,
        callRepository = callRepository,
        userRepository = userRepository,
        clientRepository = clientRepository
    )

    private fun initiateMediaManager() {
        mediaManager = MediaManager.getInstance(appContext)
    }

    private fun initiateFlowManager() {
        flowManager = FlowManager(
            appContext
        ) { manager, path, method, ctype, content, ctx ->
            // TODO("Not yet implemented")
            kaliumLogger.d("FlowManager -> RequestHandler -> $path : $method")
            0
        }.also {
            it.setEnableLogging(true)
            it.setLogHandler(object : NativeLogHandler {
                override fun append(msg: String?) {
                    // TODO("Not yet implemented")
                    kaliumLogger.d("FlowManager -> Logger -> Append -> $msg")
                }

                override fun upload() {
                    // TODO("Not yet implemented")
                    kaliumLogger.d("FlowManager -> Logger -> upload")
                }
            })
        }
    }
}

object LogHandlerImpl : LogHandler {
    override fun onLog(level: Int, message: String, arg: Pointer?) {
        when (level) {
            0 -> kaliumLogger.d(message)
            1 -> kaliumLogger.i(message)
            2 -> kaliumLogger.w(message)
            3 -> kaliumLogger.e(message)
        }
    }
}
