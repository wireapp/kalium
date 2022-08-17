package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.connection.MarkConnectionRequestAsNotifiedUseCase
import com.wire.kalium.logic.feature.connection.MarkConnectionRequestAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.connection.ObserveConnectionListUseCase
import com.wire.kalium.logic.feature.connection.ObserveConnectionListUseCaseImpl
import com.wire.kalium.logic.feature.conversation.keyingmaterials.UpdateKeyingMaterialsUseCase
import com.wire.kalium.logic.feature.conversation.keyingmaterials.UpdateKeyingMaterialsUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager

@Suppress("LongParameterList")
class ConversationScope(
    private val conversationRepository: ConversationRepository,
    private val connectionRepository: ConnectionRepository,
    private val userRepository: UserRepository,
    private val callRepository: CallRepository,
    private val syncManager: SyncManager,
    private val mlsConversationRepository: MLSConversationRepository,
    private val clientRepository: ClientRepository,
    private val selfUserId: UserId,
    private val persistMessage: PersistMessageUseCase
) {
    val getConversations: GetConversationsUseCase
        get() = GetConversationsUseCase(conversationRepository)

    val getConversationDetails: GetConversationUseCase
        get() = GetConversationUseCase(conversationRepository)

    val observeConversationListDetails: ObserveConversationListDetailsUseCase
        get() = ObserveConversationListDetailsUseCaseImpl(conversationRepository, callRepository)

    val observeConversationsAndConnectionListUseCase: ObserveConversationsAndConnectionsUseCase
        get() = ObserveConversationsAndConnectionsUseCaseImpl(observeConversationListDetails, observeConnectionList)

    val observeConversationMembers: ObserveConversationMembersUseCase
        get() = ObserveConversationMembersUseCase(conversationRepository, userRepository)

    val observeUserListById: ObserveUserListByIdUseCase
        get() = ObserveUserListByIdUseCase(userRepository)

    val observeConversationDetails: ObserveConversationDetailsUseCase
        get() = ObserveConversationDetailsUseCase(conversationRepository)

    val createGroupConversation: CreateGroupConversationUseCase
        get() = CreateGroupConversationUseCase(conversationRepository, syncManager, clientRepository)

    val addMemberToConversationUseCase: AddMemberToConversationUseCase
        get() = AddMemberToConversationUseCaseImpl(conversationRepository, mlsConversationRepository)

    val getOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase
        get() = GetOrCreateOneToOneConversationUseCase(conversationRepository)

    val updateConversationMutedStatus: UpdateConversationMutedStatusUseCase
        get() = UpdateConversationMutedStatusUseCaseImpl(conversationRepository)

    val observeConnectionList: ObserveConnectionListUseCase
        get() = ObserveConnectionListUseCaseImpl(connectionRepository)

    val markConnectionRequestAsNotified: MarkConnectionRequestAsNotifiedUseCase
        get() = MarkConnectionRequestAsNotifiedUseCaseImpl(connectionRepository)

    val updateConversationReadDateUseCase: UpdateConversationReadDateUseCase
        get() = UpdateConversationReadDateUseCase(conversationRepository)

    val updateConversationAccess: UpdateConversationAccessRoleUseCase
        get() = UpdateConversationAccessRoleUseCase(conversationRepository)

    val updateConversationMemberRole: UpdateConversationMemberRoleUseCase
        get() = UpdateConversationMemberRoleUseCaseImpl(conversationRepository)

    val removeMemberFromConversation: RemoveMemberFromConversationUseCase
        get() = RemoveMemberFromConversationUseCaseImpl(conversationRepository, selfUserId, persistMessage)

    val updateMLSGroupsKeyingMaterials: UpdateKeyingMaterialsUseCase
        get() = UpdateKeyingMaterialsUseCaseImpl(mlsConversationRepository)

}
