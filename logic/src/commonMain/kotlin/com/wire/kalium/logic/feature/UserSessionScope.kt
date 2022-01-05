package com.wire.kalium.logic.feature

import com.wire.kalium.logic.AuthenticatedDataSourceSet
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.message.MessageScope

class UserSessionScope(
    private val userSession: AuthSession,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet
) {

    private val idMapper: IdMapper get() = IdMapper()
    private val memberMapper: MemberMapper get() = MemberMapper(idMapper)
    private val conversationMapper: ConversationMapper get() = ConversationMapper(idMapper, memberMapper)

    private val conversationRepository: ConversationRepository
        get() = ConversationRepository(
            authenticatedDataSourceSet.authenticatedNetworkContainer.conversationApi,
            authenticatedDataSourceSet.authenticatedNetworkContainer.clientApi, idMapper, conversationMapper
        )

    private val messageRepository: MessageRepository
        get() = MessageRepository(authenticatedDataSourceSet.authenticatedNetworkContainer.messageApi)

    val conversations: ConversationScope get() = ConversationScope(conversationRepository)
    val messages: MessageScope get() = MessageScope(messageRepository)
}
