package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.MemberChangeResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map

interface AddMemberToConversationUseCase {
    suspend operator fun invoke(conversationId: ConversationId, userIdList: List<UserId>)
}

class AddMemberToConversationUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val selfUserId: UserId,
    private val persistMessage: PersistMessageUseCase
) : AddMemberToConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId, userIdList: List<UserId>) {
        // TODO: do we need to filter out self user ?
        conversationRepository.detailsById(conversationId).map { conversation ->
            when (conversation.protocol) {
                is ProtocolInfo.Proteus -> conversationRepository.addMembers(userIdList, conversationId).fold({},
                    {
                        if (it is MemberChangeResult.Changed) {
                            val message = Message.System(
                                id = uuid4().toString(), // We generate a random uuid for this new system message
                                content = MessageContent.MemberChange.Added(members = userIdList),
                                conversationId = conversationId,
                                date = it.time,
                                senderUserId = selfUserId,
                                status = Message.Status.SENT,
                                visibility = Message.Visibility.VISIBLE
                            )
                            persistMessage(message)
                        }
                    })
                is ProtocolInfo.MLS ->
                    mlsConversationRepository.addMemberToMLSGroup(conversation.protocol.groupId, userIdList)
            }
        }
    }
}
