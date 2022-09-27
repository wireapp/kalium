package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

class ReactionMessageUseCase internal constructor(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val userId: UserId,
    private val slowSyncRepository: SlowSyncRepository,
    private val reactionRepository: ReactionRepository,
    private val messageSender: MessageSender
) {
        suspend operator fun invoke(
            conversationId: ConversationId,
            messageId: String,
            reactions: String
        ): Either<CoreFailure, Unit> {
            slowSyncRepository.slowSyncStatus.first {
                it is SlowSyncStatus.Complete
            }
            val date = Clock.System.now().toString()
            return reactionRepository
                .persistReaction(messageId, conversationId, senderUserId = userId, date = date, emoji = reactions)
                .flatMap { currentClientIdProvider() }
                .flatMap { clientId ->

                    val regularMessage = Message.Regular(
                        id = uuid4().toString(),
                        content =  MessageContent.Reaction(messageId = messageId, emoji = reactions),
                        conversationId = conversationId,
                        date = date,
                        senderUserId = userId,
                        senderClientId = clientId,
                        status = Message.Status.PENDING,
                        editStatus = Message.EditStatus.NotEdited,
                    )
                    messageSender.sendMessage(regularMessage)
                }
                .flatMapLeft {
                    reactionRepository.deleteReaction(messageId, conversationId, userId)
                }
        }
}
