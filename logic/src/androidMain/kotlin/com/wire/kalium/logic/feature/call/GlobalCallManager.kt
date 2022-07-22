package com.wire.kalium.logic.feature.call

import android.content.Context
import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.ENVIRONMENT_DEFAULT
import com.wire.kalium.calling.callbacks.LogHandler
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import kotlinx.coroutines.Dispatchers

actual class GlobalCallManager(
    private val appContext: Context,
    private val federatedIdMapper: FederatedIdMapper
) {

    private val callManagerHolder = hashMapOf<QualifiedID, CallManager>()

    private val calling by lazy {
        Calling.INSTANCE.apply {
            wcall_init(env = ENVIRONMENT_DEFAULT)
            wcall_set_log_handler(
                logHandler = LogHandlerImpl,
                arg = null
            )
            callingLogger.i("GlobalCallManager -> wcall_init")
        }
    }

    /**
     * Get a [CallManager] for a session, shouldn't be instantiated more than one CallManager for a single session.
     */
    @Suppress("LongParameterList")
    actual fun getCallManagerForClient(
        userId: QualifiedID,
        callRepository: CallRepository,
        userRepository: UserRepository,
        clientRepository: ClientRepository,
        conversationRepository: ConversationRepository,
        messageSender: MessageSender,
        callMapper: CallMapper
    ): CallManager {
        return callManagerHolder[userId] ?: CallManagerImpl(
            calling = calling,
            callRepository = callRepository,
            userRepository = userRepository,
            clientRepository = clientRepository,
            callMapper = callMapper,
            messageSender = messageSender,
            conversationRepository = conversationRepository,
            federatedIdMapper = federatedIdMapper
        ).also {
            callManagerHolder[userId] = it
        }
    }

    // Initialize it eagerly, so it's already initialized when `calling` is initialized
    private val flowManager = FlowManagerServiceImpl(appContext, Dispatchers.Default)

    actual fun getFlowManager(): FlowManagerService = flowManager

    // Initialize it eagerly, so it's already initialized when `calling` is initialized
    private val mediaManager = MediaManagerServiceImpl(appContext)
    actual fun getMediaManager(): MediaManagerService = mediaManager
}

object LogHandlerImpl : LogHandler {
    override fun onLog(level: Int, message: String, arg: Pointer?) {
        when (level) {
            0 -> callingLogger.d(message)
            1 -> callingLogger.i(message)
            2 -> callingLogger.w(message)
            3 -> callingLogger.e(message)
        }
    }
}
