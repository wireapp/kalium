package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.connection.ObserveConnectionListUseCase
import com.wire.kalium.logic.feature.connection.ObserveConnectionListUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager

@Suppress("LongParameterList")
class ConversationScope(
    private val conversationRepository: ConversationRepository,
    private val connectionRepository: ConnectionRepository,
    private val userRepository: UserRepository,
    private val callRepository: CallRepository,
    private val syncManager: SyncManager,
    private val mlsConversationRepository: MLSConversationRepository,
    private val clientRepository: ClientRepository
) {
    val getConversations: GetConversationsUseCase
        get() = GetConversationsUseCase(conversationRepository, syncManager)

    val getConversationDetails: GetConversationDetailsUseCase
        get() = GetConversationDetailsUseCase(conversationRepository, syncManager)

    val observeConversationListDetails: ObserveConversationListDetailsUseCase
        get() = ObserveConversationListDetailsUseCaseImpl(conversationRepository, syncManager, callRepository)

    val observeConversationsAndConnectionListUseCase: ObserveConversationsAndConnectionsUseCase
        get() = ObserveConversationsAndConnectionsUseCaseImpl(syncManager, observeConversationListDetails, observeConnectionList)

    val observeConversationMembers: ObserveConversationMembersUseCase
        get() = ObserveConversationMembersUseCase(conversationRepository, userRepository, syncManager)

    val observeUserListById: ObserveUserListByIdUseCase
        get() = ObserveUserListByIdUseCase(userRepository, syncManager)

    val observeConversationDetails: ObserveConversationDetailsUseCase
        get() = ObserveConversationDetailsUseCase(conversationRepository, syncManager)

    val syncConversations: SyncConversationsUseCase
        get() = SyncConversationsUseCase(conversationRepository)

    val createGroupConversation: CreateGroupConversationUseCase
        get() = CreateGroupConversationUseCase(conversationRepository, syncManager, clientRepository)

    val addMemberToConversationUseCase: AddMemberToConversationUseCase
        get() = AddMemberToConversationUseCaseImpl(conversationRepository, mlsConversationRepository)

    val getOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase
        get() = GetOrCreateOneToOneConversationUseCase(conversationRepository)

    val updateConversationMutedStatus: UpdateConversationMutedStatusUseCase
        get() = UpdateConversationMutedStatusUseCaseImpl(conversationRepository)

    val observeConnectionList: ObserveConnectionListUseCase
        get() = ObserveConnectionListUseCaseImpl(connectionRepository, syncManager)
}
