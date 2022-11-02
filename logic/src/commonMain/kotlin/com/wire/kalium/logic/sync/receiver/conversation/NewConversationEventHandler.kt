package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import kotlinx.datetime.Clock

interface NewConversationEventHandler {
    suspend fun handle(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit>
}

internal class NewConversationEventHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
) : NewConversationEventHandler {
    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handle(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit> = conversationRepository
        .persistConversations(listOf(event.conversation), selfTeamIdProvider().getOrNull()?.value, originatedFromEvent = true)
        .flatMap { conversationRepository.updateConversationModifiedDate(event.conversationId, Clock.System.now().toString()) }
        .flatMap {
            val members = memberMapper.fromApiModel(event.conversation.members)
            conversationRepository.persistMembers(
                listOf(members.self)
                    .plus(members.otherMembers), event.conversationId
            )
        }
        .flatMap {
            userRepository.fetchUsersIfUnknownByIds(event.conversation.members.otherMembers.map { idMapper.fromApiModel(it.id) }
                .toSet())
        }
        .onFailure { logger.e("failure on new conversation event: $it") }

}
