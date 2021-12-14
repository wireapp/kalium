package com.wire.kalium.logic

import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.message.MessageScope

class UserSessionScope(
    private val userSession: AuthSession,
    private val authenticatedDataSourceSet: AuthenticatedDataSourceSet
) {

    private val conversationMapper: ConversationMapper get() = ConversationMapper()

    private val conversationRepository: ConversationRepository
        get() = ConversationRepository(authenticatedDataSourceSet.authenticatedNetworkContainer.conversationApi, conversationMapper)

    private val messageRepository: MessageRepository
        get() = MessageRepository(authenticatedDataSourceSet.authenticatedNetworkContainer.messageApi)

    val conversations: ConversationScope get() = ConversationScope(conversationRepository)
    val messages: MessageScope get() = MessageScope(messageRepository)
}
