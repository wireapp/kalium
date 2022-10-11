package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.ConversationDAO

interface MemberLeaveEventHandler {
    suspend fun handle(event: Event.Conversation.MemberLeave): Either<CoreFailure, Unit>
}

internal class MemberLeaveEventHandlerImpl(
    private val conversationDAO: ConversationDAO,
    private val userRepository: UserRepository,
    private val persistMessage: PersistMessageUseCase,
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : MemberLeaveEventHandler {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.MemberLeave) =
        deleteMembers(event.removedList, event.conversationId)
            .flatMap {
                // fetch required unknown users that haven't been persisted during slow sync, e.g. from another team
                // and keep them to properly show this member-leave message
                userRepository.fetchUsersIfUnknownByIds(event.removedList.toSet())
            }
            .onSuccess {
                val message = Message.System(
                    id = event.id,
                    content = MessageContent.MemberChange.Removed(members = event.removedList),
                    conversationId = event.conversationId,
                    date = event.timestampIso,
                    senderUserId = event.removedBy,
                    status = Message.Status.SENT,
                    visibility = Message.Visibility.VISIBLE
                )
                persistMessage(message)
            }
            .onFailure { logger.e("failure on member leave event: $it") }

    private suspend fun deleteMembers(
        userIDList: List<UserId>,
        conversationID: ConversationId
    ): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.deleteMembersByQualifiedID(
                userIDList.map { idMapper.toDaoModel(it) },
                idMapper.toDaoModel(conversationID)
            )
        }
}
