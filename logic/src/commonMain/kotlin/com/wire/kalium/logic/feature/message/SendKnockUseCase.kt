package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Sending a ping/knock message to a conversation
 */
class SendKnockUseCase internal constructor(
    private val persistMessage: PersistMessageUseCase,
    private val userRepository: UserRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {

    /**
     * Operation to send a ping or knock message to a conversation
     *
     * @param conversationId the id of the conversation to send the ping to
     * @param hotKnock whether to send this as a hot knock or not @see [MessageContent.Knock]
     * @return [Either] [CoreFailure] or [Unit] //fixme: we should not return [Either]
     */
    suspend operator fun invoke(conversationId: ConversationId, hotKnock: Boolean): Either<CoreFailure, Unit> =
        withContext(dispatchers.default) {
            slowSyncRepository.slowSyncStatus.first {
                it is SlowSyncStatus.Complete
            }

            val selfUser = userRepository.observeSelfUser().first()

            val generatedMessageUuid = uuid4().toString()

            currentClientIdProvider().flatMap { currentClientId ->
                val message = Message.Regular(
                    id = generatedMessageUuid,
                    content = MessageContent.Knock(hotKnock),
                    conversationId = conversationId,
                    date = DateTimeUtil.currentIsoDateTimeString(),
                    senderUserId = selfUser.id,
                    senderClientId = currentClientId,
                    status = Message.Status.PENDING,
                    editStatus = Message.EditStatus.NotEdited
                )
                persistMessage(message)
            }.flatMap {
                messageSender.sendPendingMessage(conversationId, generatedMessageUuid)
            }.onFailure {
                if (it is CoreFailure.Unknown) {
                    kaliumLogger.e("There was an unknown error trying to send the message $it", it.rootCause)
                    it.rootCause?.printStackTrace()
                } else {
                    kaliumLogger.e("There was an error trying to send the message $it")
                }
            }
        }
}
