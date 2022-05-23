package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.UserTypeMapperImpl
import com.wire.kalium.logic.sync.SyncManager

class ConversationScope(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val syncManager: SyncManager
) {
    val getConversations: GetConversationsUseCase
        get() = GetConversationsUseCase(conversationRepository, syncManager)

    val getConversationDetails: GetConversationDetailsUseCase
        get() = GetConversationDetailsUseCase(conversationRepository, syncManager)

    val observeConversationListDetails: ObserveConversationListDetailsUseCase
        get() = ObserveConversationListDetailsUseCase(conversationRepository, syncManager)

    val observeConversationMembers: ObserveConversationMembersUseCase
        get() = ObserveConversationMembersUseCase(conversationRepository, userRepository, syncManager, UserTypeMapperImpl())

    val observeConversationDetails: ObserveConversationDetailsUseCase
        get() = ObserveConversationDetailsUseCase(conversationRepository, syncManager)

    val syncConversations: SyncConversationsUseCase
        get() = SyncConversationsUseCase(conversationRepository)

    val createGroupConversation: CreateGroupConversationUseCase
        get() = CreateGroupConversationUseCase(conversationRepository, syncManager)

    val getOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase
        get() = GetOrCreateOneToOneConversationUseCase(conversationRepository)

    val updateConversationMutedStatus: UpdateConversationMutedStatusUseCase
        get() = UpdateConversationMutedStatusUseCaseImpl(conversationRepository)
}
