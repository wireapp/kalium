package com.wire.kalium.logic.feature.call

import android.content.Context
import com.sun.jna.Pointer
import com.waz.call.FlowManager
import com.waz.media.manager.MediaManager
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.ENVIRONMENT_DEFAULT
import com.wire.kalium.calling.callbacks.LogHandler
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.kaliumLogger
import com.waz.log.LogHandler as NativeLogHandler

actual class GlobalCallManager(
    private val appContext: Context
) {

    private lateinit var mediaManager: MediaManager
    private lateinit var flowManager: FlowManager
    private val callManagerImplHolder = hashMapOf<QualifiedID, CallManagerImpl>()

    private val calling by lazy {
        initiateMediaManager()
        initiateFlowManager()
        Calling.INSTANCE.apply {
            wcall_init(env = ENVIRONMENT_DEFAULT)
            wcall_set_log_handler(
                logHandler = LogHandlerImpl,
                arg = null
            )
            kaliumLogger.i("GlobalCallManager -> wcall_init")
        }
    }

    /**
     * Get a [CallManagerImpl] for a session, shouldn't be instantiated more than one CallManager for a single session.
     */
    actual fun getCallManagerForClient(
        userId: QualifiedID,
        callRepository: CallRepository,
        userRepository: UserRepository,
        clientRepository: ClientRepository,
        messageSender: MessageSender
    ): CallManager {
        return callManagerImplHolder[userId] ?: CallManagerImpl(
            calling = calling,
            callRepository = callRepository,
            userRepository = userRepository,
            clientRepository = clientRepository,
            messageSender = messageSender
        ).also {
            callManagerImplHolder[userId] = it
        }
    }

    private fun initiateMediaManager() {
        mediaManager = MediaManager.getInstance(appContext)
    }

    private fun initiateFlowManager() {
        flowManager = FlowManager(
            appContext
        ) { manager, path, method, ctype, content, ctx ->
            // TODO("Not yet implemented")
            kaliumLogger.i("FlowManager -> RequestHandler -> $path : $method")
            0
        }.also {
            it.setEnableLogging(true)
            it.setLogHandler(object : NativeLogHandler {
                override fun append(msg: String?) {
                    kaliumLogger.i("FlowManager -> Logger -> Append -> $msg")
                }

                override fun upload() {
                    kaliumLogger.i("FlowManager -> Logger -> upload")
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
