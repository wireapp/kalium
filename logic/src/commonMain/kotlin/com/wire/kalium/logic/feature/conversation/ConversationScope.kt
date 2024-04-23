/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.conversation

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreatorImpl
import com.wire.kalium.logic.data.conversation.TypingIndicatorIncomingRepositoryImpl
import com.wire.kalium.logic.data.conversation.TypingIndicatorOutgoingRepositoryImpl
import com.wire.kalium.logic.data.conversation.TypingIndicatorSenderHandler
import com.wire.kalium.logic.data.conversation.TypingIndicatorSenderHandlerImpl
import com.wire.kalium.logic.data.conversation.UpdateKeyingMaterialThresholdProvider
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.UserStorage
import com.wire.kalium.logic.feature.connection.MarkConnectionRequestAsNotifiedUseCase
import com.wire.kalium.logic.feature.connection.MarkConnectionRequestAsNotifiedUseCaseImpl
import com.wire.kalium.logic.feature.connection.ObserveConnectionListUseCase
import com.wire.kalium.logic.feature.connection.ObservePendingConnectionRequestsUseCase
import com.wire.kalium.logic.feature.connection.ObservePendingConnectionRequestsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.guestroomlink.CanCreatePasswordProtectedLinksUseCase
import com.wire.kalium.logic.feature.conversation.guestroomlink.GenerateGuestRoomLinkUseCase
import com.wire.kalium.logic.feature.conversation.guestroomlink.GenerateGuestRoomLinkUseCaseImpl
import com.wire.kalium.logic.feature.conversation.guestroomlink.ObserveGuestRoomLinkUseCase
import com.wire.kalium.logic.feature.conversation.guestroomlink.ObserveGuestRoomLinkUseCaseImpl
import com.wire.kalium.logic.feature.conversation.guestroomlink.RevokeGuestRoomLinkUseCase
import com.wire.kalium.logic.feature.conversation.guestroomlink.RevokeGuestRoomLinkUseCaseImpl
import com.wire.kalium.logic.feature.conversation.keyingmaterials.UpdateKeyingMaterialsUseCase
import com.wire.kalium.logic.feature.conversation.keyingmaterials.UpdateKeyingMaterialsUseCaseImpl
import com.wire.kalium.logic.feature.conversation.messagetimer.UpdateMessageTimerUseCase
import com.wire.kalium.logic.feature.conversation.messagetimer.UpdateMessageTimerUseCaseImpl
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.message.SendConfirmationUseCase
import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessagesAfterEndDateUseCase
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.feature.team.DeleteTeamConversationUseCase
import com.wire.kalium.logic.feature.team.DeleteTeamConversationUseCaseImpl
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdateHandlerImpl
import kotlinx.coroutines.CoroutineScope

@Suppress("LongParameterList")
class ConversationScope internal constructor(
    private val conversationRepository: ConversationRepository,
    private val conversationGroupRepository: ConversationGroupRepository,
    private val connectionRepository: ConnectionRepository,
    private val userRepository: UserRepository,
    private val syncManager: SyncManager,
    private val mlsConversationRepository: MLSConversationRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val messageSender: MessageSender,
    private val teamRepository: TeamRepository,
    private val selfUserId: UserId,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val persistMessage: PersistMessageUseCase,
    private val updateKeyingMaterialThresholdProvider: UpdateKeyingMaterialThresholdProvider,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val sendConfirmation: SendConfirmationUseCase,
    private val renamedConversationHandler: RenamedConversationEventHandler,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val serverConfigRepository: ServerConfigRepository,
    private val userStorage: UserStorage,
    userPropertyRepository: UserPropertyRepository,
    private val deleteEphemeralMessageEndDate: DeleteEphemeralMessagesAfterEndDateUseCase,
    private val oneOnOneResolver: OneOnOneResolver,
    private val scope: CoroutineScope,
    private val kaliumLogger: KaliumLogger,
    private val refreshUsersWithoutMetadata: RefreshUsersWithoutMetadataUseCase
) {

    val getConversations: GetConversationsUseCase
        get() = GetConversationsUseCase(conversationRepository)

    val getConversationDetails: GetConversationUseCase
        get() = GetConversationUseCase(conversationRepository)

    val getOneToOneConversation: GetOneToOneConversationUseCase
        get() = GetOneToOneConversationUseCase(conversationRepository)

    val observeConversationListDetails: ObserveConversationListDetailsUseCase
        get() = ObserveConversationListDetailsUseCaseImpl(conversationRepository)

    val observeConversationMembers: ObserveConversationMembersUseCase
        get() = ObserveConversationMembersUseCaseImpl(conversationRepository, userRepository)

    val getMembersToMention: MembersToMentionUseCase
        get() = MembersToMentionUseCase(observeConversationMembers, userRepository)

    val observeUserListById: ObserveUserListByIdUseCase
        get() = ObserveUserListByIdUseCase(userRepository)

    val observeConversationDetails: ObserveConversationDetailsUseCase
        get() = ObserveConversationDetailsUseCase(conversationRepository)

    val notifyConversationIsOpen: NotifyConversationIsOpenUseCase
        get() = NotifyConversationIsOpenUseCaseImpl(
            oneOnOneResolver,
            conversationRepository,
            deleteEphemeralMessageEndDate,
            kaliumLogger
        )

    val observeIsSelfUserMemberUseCase: ObserveIsSelfUserMemberUseCase
        get() = ObserveIsSelfUserMemberUseCaseImpl(conversationRepository, selfUserId)

    val observeConversationInteractionAvailabilityUseCase: ObserveConversationInteractionAvailabilityUseCase
        get() = ObserveConversationInteractionAvailabilityUseCase(conversationRepository, userRepository)

    val deleteTeamConversation: DeleteTeamConversationUseCase
        get() = DeleteTeamConversationUseCaseImpl(selfTeamIdProvider, teamRepository, conversationRepository)

    val createGroupConversation: CreateGroupConversationUseCase
        get() = CreateGroupConversationUseCase(
            conversationRepository,
            conversationGroupRepository,
            syncManager,
            currentClientIdProvider,
            newGroupConversationSystemMessagesCreator,
            refreshUsersWithoutMetadata
        )

    internal val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator
        get() = NewGroupConversationSystemMessagesCreatorImpl(
            persistMessage,
            selfTeamIdProvider,
            qualifiedIdMapper,
            selfUserId
        )

    val addMemberToConversationUseCase: AddMemberToConversationUseCase
        get() = AddMemberToConversationUseCaseImpl(conversationGroupRepository, userRepository, refreshUsersWithoutMetadata)

    val addServiceToConversationUseCase: AddServiceToConversationUseCase
        get() = AddServiceToConversationUseCase(groupRepository = conversationGroupRepository)

    val getOrCreateOneToOneConversationUseCase: GetOrCreateOneToOneConversationUseCase
        get() = GetOrCreateOneToOneConversationUseCaseImpl(
            conversationRepository,
            userRepository,
            oneOnOneResolver
        )

    val isOneToOneConversationCreatedUseCase: IsOneToOneConversationCreatedUseCase
        get() = IsOneToOneConversationCreatedUseCaseImpl(conversationRepository)

    val updateConversationMutedStatus: UpdateConversationMutedStatusUseCase
        get() = UpdateConversationMutedStatusUseCaseImpl(conversationRepository)

    val updateConversationArchivedStatus: UpdateConversationArchivedStatusUseCase
        get() = UpdateConversationArchivedStatusUseCaseImpl(conversationRepository)

    @Deprecated(
        "Name is misleading, and this field will be removed",
        ReplaceWith("observePendingConnectionRequests")
    )
    val observeConnectionList: ObserveConnectionListUseCase
        get() = observePendingConnectionRequests

    val observePendingConnectionRequests: ObservePendingConnectionRequestsUseCase
        get() = ObservePendingConnectionRequestsUseCaseImpl(connectionRepository)

    val markConnectionRequestAsNotified: MarkConnectionRequestAsNotifiedUseCase
        get() = MarkConnectionRequestAsNotifiedUseCaseImpl(connectionRepository)

    val updateConversationReadDateUseCase: UpdateConversationReadDateUseCase
        get() = UpdateConversationReadDateUseCase(
            conversationRepository,
            messageSender,
            currentClientIdProvider,
            selfUserId,
            selfConversationIdProvider,
            sendConfirmation,
            scope
        )

    val updateConversationAccess: UpdateConversationAccessRoleUseCase
        get() = UpdateConversationAccessRoleUseCase(conversationRepository, conversationGroupRepository, syncManager)

    val updateConversationMemberRole: UpdateConversationMemberRoleUseCase
        get() = UpdateConversationMemberRoleUseCaseImpl(conversationRepository)

    val removeMemberFromConversation: RemoveMemberFromConversationUseCase
        get() = RemoveMemberFromConversationUseCaseImpl(conversationGroupRepository)

    val leaveConversation: LeaveConversationUseCase
        get() = LeaveConversationUseCaseImpl(conversationGroupRepository, selfUserId)

    val renameConversation: RenameConversationUseCase
        get() = RenameConversationUseCaseImpl(
            conversationRepository,
            persistMessage,
            renamedConversationHandler,
            selfUserId
        )

    val updateMLSGroupsKeyingMaterials: UpdateKeyingMaterialsUseCase
        get() = UpdateKeyingMaterialsUseCaseImpl(mlsConversationRepository, updateKeyingMaterialThresholdProvider)

    val clearConversationContent: ClearConversationContentUseCase
        get() = ClearConversationContentUseCaseImpl(
            conversationRepository,
            messageSender,
            selfUserId,
            currentClientIdProvider,
            selfConversationIdProvider
        )

    val joinConversationViaCode: JoinConversationViaCodeUseCase
        get() = JoinConversationViaCodeUseCase(conversationGroupRepository, selfUserId)

    val checkIConversationInviteCode: CheckConversationInviteCodeUseCase
        get() = CheckConversationInviteCodeUseCase(
            conversationGroupRepository,
            conversationRepository,
            selfUserId
        )

    val updateConversationReceiptMode: UpdateConversationReceiptModeUseCase
        get() = UpdateConversationReceiptModeUseCaseImpl(
            conversationRepository = conversationRepository,
            persistMessage = persistMessage,
            selfUserId = selfUserId
        )

    val generateGuestRoomLink: GenerateGuestRoomLinkUseCase
        get() = GenerateGuestRoomLinkUseCaseImpl(
            conversationGroupRepository,
            CodeUpdateHandlerImpl(userStorage.database.conversationDAO)
        )

    val revokeGuestRoomLink: RevokeGuestRoomLinkUseCase
        get() = RevokeGuestRoomLinkUseCaseImpl(
            conversationGroupRepository
        )

    val observeGuestRoomLink: ObserveGuestRoomLinkUseCase
        get() = ObserveGuestRoomLinkUseCaseImpl(
            conversationGroupRepository
        )

    val updateMessageTimer: UpdateMessageTimerUseCase
        get() = UpdateMessageTimerUseCaseImpl(
            conversationGroupRepository
        )

    val getConversationUnreadEventsCountUseCase: GetConversationUnreadEventsCountUseCase
        get() = GetConversationUnreadEventsCountUseCaseImpl(conversationRepository)

    val refreshConversationsWithoutMetadata: RefreshConversationsWithoutMetadataUseCase
        get() = RefreshConversationsWithoutMetadataUseCaseImpl(
            conversationRepository = conversationRepository
        )

    val canCreatePasswordProtectedLinks: CanCreatePasswordProtectedLinksUseCase
        get() = CanCreatePasswordProtectedLinksUseCase(
            serverConfigRepository,
            selfUserId
        )

    val observeArchivedUnreadConversationsCount: ObserveArchivedUnreadConversationsCountUseCase
        get() = ObserveArchivedUnreadConversationsCountUseCaseImpl(conversationRepository)

    private val typingIndicatorSenderHandler: TypingIndicatorSenderHandler =
        TypingIndicatorSenderHandlerImpl(conversationRepository = conversationRepository, userSessionCoroutineScope = scope)

    internal val typingIndicatorIncomingRepository =
        TypingIndicatorIncomingRepositoryImpl(
            ConcurrentMutableMap(),
            userPropertyRepository
        )

    internal val typingIndicatorOutgoingRepository =
        TypingIndicatorOutgoingRepositoryImpl(
            typingIndicatorSenderHandler,
            userPropertyRepository
        )

    val sendTypingEvent: SendTypingEventUseCase
        get() = SendTypingEventUseCaseImpl(typingIndicatorOutgoingRepository)

    val observeUsersTyping: ObserveUsersTypingUseCase
        get() = ObserveUsersTypingUseCaseImpl(typingIndicatorIncomingRepository, userRepository)

    val clearUsersTypingEvents: ClearUsersTypingEventsUseCase
        get() = ClearUsersTypingEventsUseCaseImpl(typingIndicatorIncomingRepository)

    val setUserInformedAboutVerificationBeforeMessagingUseCase: SetUserInformedAboutVerificationUseCase
        get() = SetUserInformedAboutVerificationUseCaseImpl(conversationRepository)
    val observeInformAboutVerificationBeforeMessagingFlagUseCase: ObserveDegradedConversationNotifiedUseCase
        get() = ObserveDegradedConversationNotifiedUseCaseImpl(conversationRepository)
    val setNotifiedAboutConversationUnderLegalHold: SetNotifiedAboutConversationUnderLegalHoldUseCase
        get() = SetNotifiedAboutConversationUnderLegalHoldUseCaseImpl(conversationRepository)
    val observeConversationUnderLegalHoldNotified: ObserveConversationUnderLegalHoldNotifiedUseCase
        get() = ObserveConversationUnderLegalHoldNotifiedUseCaseImpl(conversationRepository)

}
