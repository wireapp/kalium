package com.wire.kalium.logic.feature.user

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.client.GetOtherUserClientsUseCaseImpl
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.message.MessageTarget
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.util.DateTimeUtil

/**
 * Updates the current user's [UserAvailabilityStatus] status.
 * @see [UserAvailabilityStatus]
 */
class UpdateSelfAvailabilityStatusUseCase internal constructor(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val messageSender: MessageSender,
    private val provideClientId: CurrentClientIdProvider,
    private val selfUserId: QualifiedID,
) {
    /**
     * @param status the new [UserAvailabilityStatus] status.
     */
    suspend operator fun invoke(status: UserAvailabilityStatus) {
        provideClientId().flatMap { selfClientId ->
            conversationRepository.getConversationIdsByUserId(selfUserId)
                .flatMap { conversationIds ->
                    userRepository.getTeamRecipients()
                        .flatMap { recipients ->
                            val message = Message.Signaling(
                                id = uuid4().toString(),
                                content = MessageContent.Availability(status),
                                conversationId = conversationIds[0],
                                date = DateTimeUtil.currentIsoDateTimeString(),
                                senderUserId = selfUserId,
                                senderClientId = selfClientId,
                                status = Message.Status.SENT
                            )

                            val recipientsFiltered = recipients
                                .subList(0, 501)
                                .map { it.copy(clients = it.clients.filter { clientId -> clientId != selfClientId }) }

                            messageSender.sendMessage(message, MessageTarget.Client(recipientsFiltered))
                        }
                }
        }
        // TODO: Handle possibility of being offline. Storing the broadcast to be sent when Sync is done.
        // For now, we don't need Sync, as we do not broadcast the availability to other devices or users.
        userRepository.updateSelfUserAvailabilityStatus(status)
    }
}
