package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.flatMap
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class UpdateConversationReadDateUseCase(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
//     private val messageSender: MessageSender
) {

    suspend operator fun invoke(conversationId: QualifiedID, time: Instant) {
        conversationRepository.updateConversationReadDate(conversationId, time.toString())

//         val selfUser = userRepository.observeSelfUser().first()
//         val generatedMessageUuid = uuid4().toString()
//         clientRepository.currentClientId().flatMap { currentClientId ->
//             val regularMessage = Message.Regular(
//                 id = generatedMessageUuid,
//                 content = MessageContent.LastRead(
//                     messageId,
//                     conversationId = conversationId.value,
//                     qualifiedConversationId = idMapper.toProtoModel(conversationId)
//                 ),
//                 conversationId = conversationId,
//                 date = Clock.System.now().toString(),
//                 senderUserId = selfUser.id,
//                 senderClientId = currentClientId,
//                 status = Message.Status.PENDING,
//                 editStatus = Message.EditStatus.NotEdited,
//             )
//             messageSender.sendMessage(regularMessage)
        }

}
