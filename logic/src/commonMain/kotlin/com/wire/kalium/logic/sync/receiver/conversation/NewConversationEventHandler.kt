package com.wire.kalium.logic.sync.receiver.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode as NetworkReceiptMode

interface NewConversationEventHandler {
    suspend fun handle(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit>
}

internal class NewConversationEventHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val persistMessage: PersistMessageUseCase,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val isSelfATeamMember: IsSelfATeamMemberUseCase
) : NewConversationEventHandler {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit> = conversationRepository
        .persistConversations(listOf(event.conversation), selfTeamIdProvider().getOrNull()?.value, originatedFromEvent = true)
        .flatMap { conversationRepository.updateConversationModifiedDate(event.conversationId, DateTimeUtil.currentIsoDateTimeString()) }
        .flatMap {
            userRepository.fetchUsersIfUnknownByIds(event.conversation.members.otherMembers.map { it.id.toModel() }
                .toSet())
        }
        .onSuccess {
            if (event.conversation.type == ConversationResponse.Type.GROUP && isSelfATeamMember()) {
                val message = Message.System(
                    uuid4().toString(),
                    MessageContent.NewConversationReceiptMode(
                        receiptMode = event.conversation.receiptMode == NetworkReceiptMode.ENABLED
                    ),
                    event.conversation.id.toModel(),
                    DateTimeUtil.currentIsoDateTimeString(),
                    qualifiedIdMapper.fromStringToQualifiedID(event.conversation.creator),
                    Message.Status.SENT,
                    Message.Visibility.VISIBLE
                )
                persistMessage(message)
            }
        }
        .onFailure { logger.e("failure on new conversation event: $it") }

}
