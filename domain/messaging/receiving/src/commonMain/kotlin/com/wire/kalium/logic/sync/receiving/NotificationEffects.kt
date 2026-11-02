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

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.newClientParameters
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.actualMLSVerificationStatus
import com.wire.kalium.logic.data.conversation.areMLSIdentitiesVerified
import com.wire.kalium.logic.data.conversation.mlsVerificationMessage
import com.wire.kalium.logic.data.conversation.toDaoChannelPermission
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.conversation.updateArchivedStatusLocally
import com.wire.kalium.logic.data.conversation.updateMutedStatusLocally
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.meeting.occurrenceGenerationUntil
import com.wire.kalium.logic.data.meeting.occurrenceOutdatedThreshold
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.matchesSystemMessage
import com.wire.kalium.logic.data.message.shouldUpdateConversationOrder
import com.wire.kalium.logic.data.message.toEntity
import com.wire.kalium.logic.data.sync.SlowSyncRepositoryImpl
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserUpdateEventHandler
import com.wire.kalium.logic.data.user.toDao
import com.wire.kalium.logic.feature.featureConfig.handler.AppLockConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ClassifiedDomainsConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.ConferenceCallingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.E2EIConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.FileSharingConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.GuestRoomConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.SelfDeletingMessagesConfigHandler
import com.wire.kalium.logic.feature.featureConfig.handler.applyMLSConfiguration
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.ReceiptModeUpdateEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.RenamedConversationEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.MessageUnpackResult
import com.wire.kalium.logic.sync.receiver.handler.AllowedGlobalOperationsHandler
import com.wire.kalium.logic.sync.receiver.handler.AssetAuditLogConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.CodeUpdateHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CodeDeletedHandlerImpl
import com.wire.kalium.logic.sync.receiver.handler.CellsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.EnableUserProfileQRCodeConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.MeetingsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.PreventAdminlessGroupsConfigHandler
import com.wire.kalium.logic.sync.receiver.handler.UserPropertiesEventHandler
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.meeting.MeetingOccurrencesGenerator.GenerationLimit
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.db.MessageEffectDAOs
import com.wire.kalium.persistence.db.UserDatabaseBuilder

/** All canonical effects of one decoded notification, on one writer connection. */
@Suppress("LongParameterList")
internal class NotificationEffects(
    private val database: UserDatabaseBuilder,
    private val selfUserId: UserId,
    private val mlsEnabled: Boolean,
    private val options: ReceivingOptions,
    private val deleteFile: (String) -> Unit,
    private val previewImages: ReceivedLinkPreviewImages,
    private val serverConfigLinks: com.wire.kalium.logic.configuration.server.ServerConfig.Links,
) {
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    suspend fun persist(payload: List<ReceivedPayload>, verifications: List<MLSVerification>): NotificationEffectsResult =
        database.writeMessageEffects {
            val calls = mutableListOf<ReceivedCallEvent>()
            // Distinct settings reset the wall-clock grace deadline on every replay. The final
            // settings row cannot recover the first attempt's deadline after an intervening change.
            val e2ei = payload.filterIsInstance<ReceivedPayload.Configuration>()
                .mapNotNull { (it.event as? Event.FeatureConfig.MLSE2EIUpdated)?.model }
            check(e2ei.map { it.status to it.config.discoverUrl }.distinct().size <= 1) {
                "Multiple E2EI settings identities require replayable grace deadlines"
            }
            val userConfigRepository = UserConfigDataSource(prefs, config, lazy { options.fileRestrictionState })
            val userUpdate = UserUpdateEventHandler(users)
            val userProperties = UserPropertiesEventHandler(prefs, folders)
            val persistMessage = PersistMessageUseCase { message ->
                wrapStorageRequest {
                    check(message is Message.System)
                    val entity = message.toEntity()
                    val existing = messages.getMessageById(message.id, entity.conversationId)
                    if (existing != null) {
                        check(
                            existing.matchesSystemMessage(entity.senderUserId, entity.content)
                        ) {
                            "System message identity conflicts with stored state"
                        }
                    } else {
                        // Local system messages are not notification acknowledgements. Inserting
                        // a self-authored warning must not suppress text at the same backend time.
                        messages.insertOrIgnoreMessages(listOf(entity))
                        if (message.content.shouldUpdateConversationOrder()) {
                            conversations.updateConversationModifiedDate(entity.conversationId, entity.date)
                        }
                    }
                    Unit
                }
            }
            val messageHandler = ReceivedMessageHandler(
                data = this,
                selfUserId = selfUserId,
                mlsEnabled = mlsEnabled,
                persistMessage = persistMessage,
                userConfig = userConfigRepository,
                deleteFile = deleteFile,
                previewImages = previewImages,
                calls = calls,
            )
            val access = ReceivedAccessHandler(this, persistMessage)
            val membership = ReceivedMembershipHandler(this, persistMessage, selfUserId, options.drivePermissionsEnabled)
            val team = ReceivedTeamHandler(this, selfUserId, persistMessage)
            val federation = ReceivedFederationHandler(this, selfUserId, persistMessage, deleteFile)
            val connection = ReceivedConnectionHandler(this, selfUserId, deleteFile)
            val replayedFederation = payload.filterIsInstance<ReceivedPayload.Federation>()
                .associate { it.event to federation.previousMessages(it.event) }
            val replayedConnectionWarnings = payload.filterIsInstance<ReceivedPayload.Connection>()
                .associateWith { connection.previousWarning(it) }
            val replayedAccess = payload.filterIsInstance<ReceivedPayload.Metadata>()
                .mapNotNull { it.event as? Event.Conversation.AccessUpdate }.associateWith { access.previous(it) }
            val replayedPromotions = payload.filterIsInstance<ReceivedPayload.MemberRole>()
                .associate { it.event to membership.previousPromotion(it.event) }
            val recreatedEarlier = mutableSetOf<QualifiedIDEntity>()
            val requiredEarlier = mutableSetOf<QualifiedIDEntity>()
            // Only the last creation matters when checking whether a conversation is recreated later.
            val lastCreationIndex = payload.mapIndexedNotNull { index, received ->
                (received as? ReceivedPayload.Creation)?.let { it.creation.conversation.id to index }
            }.toMap()
            payload.forEachIndexed { index, received ->
                val after = payload.subList(index + 1, payload.size)
                when (received) {
                    is ReceivedPayload.Deletion -> messageHandler.deleteConversation(
                        received.event,
                        preparedDeletionGroupId = received.preparedDeletionGroupId,
                        requiredByEarlierPayload = received.event.conversationId.toDao() in requiredEarlier,
                        recreatedLater = (lastCreationIndex[received.event.conversationId.toDao()] ?: -1) > index
                    )
                    is ReceivedPayload.DecryptionFailure -> messageHandler.failedDecryption(received.message)
                    is ReceivedPayload.Creation -> received.creation.persist(this, selfUserId, persistMessage)
                    is ReceivedPayload.OneOnOne -> {
                        if (received.resolution.persist(this, selfUserId, persistMessage)) {
                            messageHandler.moved(received.resolution.sources, received.resolution.target)
                        }
                    }
                    is ReceivedPayload.ProtocolUpdate -> {
                        conversations.updateConversationProtocolAndCipherSuite(
                            conversationId = received.conversationId.toDao(),
                            groupID = received.groupId,
                            protocol = received.protocol,
                            cipherSuite = received.cipherSuite
                        )
                        if (received.groupId != null && received.groupState != null) {
                            conversations.updateMLSGroupIdAndState(
                                received.conversationId.toDao(),
                                received.groupId,
                                received.epoch.toLong(),
                                received.groupState
                            )
                        }
                    }
                    is ReceivedPayload.MLSReset -> {
                        conversations.clearProposalTimer(received.oldGroupId)
                        conversations.updateMLSGroupIdAndState(
                            received.conversationId.toDao(),
                            received.newGroupId,
                            received.epoch.toLong(),
                            received.state
                        )
                    }
                    is ReceivedPayload.Application -> {
                        val laterApplications = after.filterIsInstance<ReceivedPayload.Application>()
                        val previousCallCount = calls.size
                        messageHandler.handle(
                            received.message,
                            laterApplications.map { it.message },
                            replayingSelfRemoval = after.memberLeaves().any { leave ->
                                leave.conversationId == received.message.conversationId && selfUserId in leave.removedList &&
                                    messages.getMessageById(leave.systemMessageId, leave.conversationId.toDao()) != null
                            },
                            isMLS = received.isMLS,
                            requiredEarlier = requiredEarlier,
                            recreatedLater = lastCreationIndex.filterValues { it > index }.keys,
                        )
                        if (calls.size > previousCallCount) {
                            // A self-device signal may need another conversation's metadata on retry.
                            val target = calls.last().conversationId
                            if (target !in recreatedEarlier) requiredEarlier += target
                        }
                    }
                    is ReceivedPayload.System -> persistMessage(received.message).orThrow()
                    is ReceivedPayload.Proposal -> conversations.setProposalTimer(received.timer)
                    is ReceivedPayload.ClearProposalTimer -> conversations.clearProposalTimer(received.groupId)
                    is ReceivedPayload.Profiles -> received.profiles.persist(users, apps)
                    is ReceivedPayload.MemberRole -> membership.changeRole(
                        received = received,
                        previous = replayedPromotions[received.event],
                    )
                    is ReceivedPayload.Membership -> when (val event = received.event) {
                        is Event.Conversation.MemberJoin -> membership.join(
                            event,
                            received.profiles,
                            after.memberLeaves()
                        )
                        is Event.Conversation.MemberLeave -> membership.leave(event, received.profiles)
                        else -> error("Unsupported membership event")
                    }
                    is ReceivedPayload.Federation -> federation.handle(
                        received.event,
                        replayedFederation.getValue(received.event),
                        requiredEarlier,
                        lastCreationIndex.filterValues { it > index }.keys,
                    )
                    is ReceivedPayload.MeetingUpsert -> {
                        users.insertOrIgnoreIncompleteUsers(listOf(received.meeting.creatorId))
                        received.profiles.persist(users, apps)
                        meetings.upsertMeetings(
                            listOf(received.meeting),
                            GenerationLimit.Window(occurrenceOutdatedThreshold(), occurrenceGenerationUntil())
                        )
                    }
                    is ReceivedPayload.MeetingDelete -> meetings.deleteMeeting(received.meetingId.toDao())
                    is ReceivedPayload.UserUpdate -> userUpdate.handle(received.event).orThrow()
                    is ReceivedPayload.NewClient -> newClients.insertNewClient(received.client.newClientParameters(selfUserId))
                    is ReceivedPayload.Connection -> {
                        connection.persist(
                            received = received,
                            requiredEarlier = requiredEarlier,
                            recreatedLater = lastCreationIndex.filterValues { it > index }.keys,
                            previousWarning = replayedConnectionWarnings[received],
                        )
                        messageHandler.connection(received.connection)
                    }
                    is ReceivedPayload.UserDeleted -> users.markUserAsDeletedAndRemoveFromGroupConv(received.userId.toDao())
                    is ReceivedPayload.TeamLeave -> team.leave(received.event)
                    is ReceivedPayload.UserProperty -> userProperties.handle(received.event).orThrow()
                    is ReceivedPayload.LegalHoldRequest -> userConfigRepository.setLegalHoldRequest(
                        received.clientId,
                        received.lastPreKeyId,
                        received.lastPreKey
                    ).orThrow()
                    is ReceivedPayload.Configuration -> persistConfiguration(received, userConfigRepository, persistMessage, messageHandler)
                    is ReceivedPayload.Metadata -> persistMetadata(
                        received.event,
                        persistMessage,
                        access,
                        replayedAccess[received.event],
                    )
                }
                // Add this payload only after its effects: handlers above must see the strict prefix.
                recordConversationDependencies(received, recreatedEarlier, requiredEarlier)
            }
            verifications.forEach { persistVerification(this, it, persistMessage) }
            NotificationEffectsResult(messageHandler.finish(), calls)
        }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun MessageEffectDAOs.persistConfiguration(
        received: ReceivedPayload.Configuration,
        userConfigRepository: UserConfigRepository,
        persistMessage: PersistMessageUseCase,
        messageHandler: ReceivedMessageHandler,
    ) {
        suspend fun persistProtocols() {
            received.selfClients?.let { clients.insertClientsAndRemoveRedundant(it) }
            received.selfProtocols?.let { protocols ->
                users.updateUserSupportedProtocols(selfUserId.toDao(), protocols.map { it.toDao() }.toSet())
            }
            received.profiles.persist(users, apps)
            received.resolutions.forEach { resolution ->
                if (resolution.persist(this, selfUserId, persistMessage)) {
                    messageHandler.moved(resolution.sources, resolution.target)
                }
            }
        }
        when (val event = received.event) {
            is Event.FeatureConfig.MLSUpdated -> {
                userConfigRepository.applyMLSConfiguration(event.model, requirePreviousProtocols = true) {
                    persistProtocols()
                    com.wire.kalium.common.functional.Either.Right(Unit)
                }
            }
            is Event.FeatureConfig.MLSMigrationUpdated -> {
                persistProtocols()
                userConfigRepository.setMigrationConfiguration(event.model)
            }
            is Event.FeatureConfig.MLSE2EIUpdated ->
                E2EIConfigHandler(userConfigRepository).handle(event.model, requireSettings = true)
            is Event.FeatureConfig.FileSharingUpdated ->
                FileSharingConfigHandler(userConfigRepository).handle(event.model, requirePreviousState = true)
            is Event.FeatureConfig.ClassifiedDomainsUpdated ->
                ClassifiedDomainsConfigHandler(userConfigRepository).handle(event.model)
            is Event.FeatureConfig.ConferenceCallingUpdated ->
                ConferenceCallingConfigHandler(userConfigRepository).handle(event.model)
            is Event.FeatureConfig.GuestRoomLinkUpdated ->
                GuestRoomConfigHandler(userConfigRepository, options.guestRoomLink).handle(event.model, requirePreviousState = true)
            is Event.FeatureConfig.SelfDeletingMessagesConfig ->
                SelfDeletingMessagesConfigHandler(userConfigRepository, options.selfDeletingMessages)
                    .handle(event.model, requirePreviousState = true)
            is Event.FeatureConfig.AppLockUpdated ->
                AppLockConfigHandler(userConfigRepository).handle(event.model, requirePreviousState = true)
            is Event.FeatureConfig.AllowedGlobalOperationsUpdated ->
                AllowedGlobalOperationsHandler(userConfigRepository).handle(event.model)
            is Event.FeatureConfig.CellsConfigUpdated -> CellsConfigHandler(userConfigRepository).handle(event.model)
            is Event.FeatureConfig.CellsInternalConfigUpdated -> CellsConfigHandler(userConfigRepository).handle(event.model)
            is Event.FeatureConfig.EnableUserProfileQRCodeConfigUpdated ->
                EnableUserProfileQRCodeConfigHandler(userConfigRepository).handle(event.model)
            is Event.FeatureConfig.AssetAuditLogConfigUpdated ->
                AssetAuditLogConfigHandler(userConfigRepository).handle(event.model)
            is Event.FeatureConfig.PreventAdminlessGroupsConfigUpdated ->
                PreventAdminlessGroupsConfigHandler(userConfigRepository).handle(event.model)
            is Event.FeatureConfig.MeetingsConfigUpdated ->
                MeetingsConfigHandler(userConfigRepository, SlowSyncRepositoryImpl(metadata)).handle(event.model)
            else -> error("Configuration requires unsupported processing")
        }.orThrow()
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun MessageEffectDAOs.persistMetadata(
        event: Event.Conversation,
        persistMessage: PersistMessageUseCase,
        access: ReceivedAccessHandler,
        previousAccess: MessageEntity?,
    ) {
        when (event) {
            is Event.Conversation.AdminlessDeleteReminder -> wrapStorageRequest {
                conversations.insertAdminlessGroupDelete(event.conversationId.toDao(), event.deletionScheduledFor)
            }
            is Event.Conversation.AccessUpdate -> wrapStorageRequest {
                access.handle(event, previousAccess)
            }
            is Event.Conversation.CodeUpdated ->
                CodeUpdateHandlerImpl(conversations, serverConfigLinks).handle(event)
            is Event.Conversation.CodeDeleted ->
                CodeDeletedHandlerImpl(conversations).handle(event)
            is Event.Conversation.ConversationChannelAddPermission -> wrapStorageRequest {
                conversations.updateChannelAddPermission(
                    event.conversationId.toDao(),
                    event.channelAddPermission.toDaoChannelPermission()
                )
            }
            else -> {
                val conversation = checkNotNull(conversations.getConversationById(event.conversationId.toDao())) {
                    "Conversation must already exist"
                }
                check(!conversation.hasIncompleteMetadata && conversation.removedBy == null) {
                    "Conversation requires synchronization"
                }
                when (event) {
                    is Event.Conversation.RenamedConversation ->
                        RenamedConversationEventHandlerImpl(conversations, messages, persistMessage).handle(event)
                    is Event.Conversation.ConversationReceiptMode ->
                        ReceiptModeUpdateEventHandlerImpl(conversations, messages, persistMessage).handle(event)
                    is Event.Conversation.ConversationMessageTimer ->
                        ConversationMessageTimerEventHandlerImpl(conversations, messages, persistMessage).handle(event)
                    is Event.Conversation.MemberChanged.MemberMutedStatusChanged -> updateMutedStatusLocally(
                        conversations,
                        event.conversationId,
                        event.mutedConversationStatus,
                        event.mutedConversationChangedTime
                    )
                    is Event.Conversation.MemberChanged.MemberArchivedStatusChanged -> updateArchivedStatusLocally(
                        conversations,
                        event.conversationId,
                        event.isArchiving,
                        event.archivedConversationChangedTime
                    )
                    else -> error("Unsupported conversation metadata")
                }
            }
        }.orThrow()
    }

    private fun List<ReceivedPayload>.memberLeaves(): List<Event.Conversation.MemberLeave> =
        filterIsInstance<ReceivedPayload.Membership>().mapNotNull { it.event as? Event.Conversation.MemberLeave }

    @Suppress("CyclomaticComplexMethod")
    private fun recordConversationDependencies(
        effect: ReceivedPayload,
        recreated: MutableSet<QualifiedIDEntity>,
        required: MutableSet<QualifiedIDEntity>,
    ) {
        if (effect is ReceivedPayload.Connection && effect.connection.status ==
            com.wire.kalium.persistence.dao.ConnectionEntity.State.PENDING
        ) {
            recreated += effect.connection.qualifiedConversationId
        } else if (effect is ReceivedPayload.Creation && effect.creation.event != null) {
            // A replayed backend creation restores metadata before its dependent effects.
            recreated += effect.creation.conversation.id
        } else {
            val ids = when (effect) {
                is ReceivedPayload.Application -> listOf(effect.message.conversationId.toDao())
                is ReceivedPayload.DecryptionFailure -> listOf(effect.message.conversationId.toDao())
                is ReceivedPayload.Metadata -> when (effect.event) {
                    is Event.Conversation.CodeUpdated, is Event.Conversation.CodeDeleted,
                    is Event.Conversation.ConversationChannelAddPermission -> emptyList()
                    else -> listOf(effect.event.conversationId.toDao())
                }
                is ReceivedPayload.MemberRole -> listOf(effect.event.conversationId.toDao())
                is ReceivedPayload.Membership -> listOf(effect.event.conversationId.toDao())
                is ReceivedPayload.System -> listOf(effect.message.conversationId.toDao())
                is ReceivedPayload.ProtocolUpdate -> listOf(effect.conversationId.toDao())
                is ReceivedPayload.MLSReset -> listOf(effect.conversationId.toDao())
                is ReceivedPayload.Creation -> listOf(effect.creation.conversation.id)
                is ReceivedPayload.MeetingUpsert -> listOf(effect.meeting.conversationId)
                is ReceivedPayload.Connection -> if (effect.connection.status ==
                    com.wire.kalium.persistence.dao.ConnectionEntity.State.ACCEPTED
                ) listOf(effect.connection.qualifiedConversationId) else emptyList()
                is ReceivedPayload.OneOnOne -> effect.resolution.sources + effect.resolution.target
                is ReceivedPayload.Configuration -> effect.resolutions.flatMap { it.sources + it.target }
                else -> emptyList()
            }
            required += ids.filter { it !in recreated }
        }
    }

    private suspend fun persistVerification(
        data: com.wire.kalium.persistence.db.MessageEffectDAOs,
        verification: MLSVerification,
        persistMessage: PersistMessageUseCase,
    ) {
        val conversationId = verification.conversationId.toDao()
        val conversation = checkNotNull(data.conversations.getConversationById(conversationId))
        // The app's gathered epoch observer resolves the conversation by group ID.
        // A later metadata refresh may already point this conversation at a new group.
        // Ordered payload effects own cleanup; never restore an old ID or verification.
        val protocol = conversation.protocolInfo as? ConversationEntity.ProtocolInfo.MLSCapable
        if (protocol?.groupId != verification.groupId) return
        val state = if (verification.state == E2EIConversationState.VERIFIED) {
            val verified = verification.identities.all { (user, identities) ->
                val profile = data.users.getNameAndHandle(user.toDao())
                areMLSIdentitiesVerified(identities, profile?.name, profile?.handle)
            }
            if (verified) Conversation.VerificationStatus.VERIFIED else Conversation.VerificationStatus.NOT_VERIFIED
        } else verification.state.toModel()
        val current = Conversation.VerificationStatus.valueOf(conversation.mlsVerificationStatus.name)
        val updated = actualMLSVerificationStatus(state, current)
        data.conversations.updateMLSGroupIdAndState(
            conversationId,
            verification.groupId,
            verification.epoch.toLong(),
            if (verification.active) ConversationEntity.GroupState.ESTABLISHED else ConversationEntity.GroupState.PENDING_JOIN
        )
        if (!verification.active) {
            if (data.members.getMemberRole(selfUserId.toDao(), conversationId) != null) {
                SlowSyncRepositoryImpl(data.metadata).setNeedsToRecoverMLSGroups(true)
            }
        }
        if (updated == current) return
        data.conversations.updateMlsVerificationStatus(ConversationEntity.VerificationStatus.valueOf(updated.name), conversationId)
        if (updated == Conversation.VerificationStatus.VERIFIED || updated == Conversation.VerificationStatus.DEGRADED) {
            persistMessage(
                mlsVerificationMessage(verification.messageId, verification.conversationId, selfUserId, verification.date, updated)
            )
                .orThrow()
            data.conversations.updateDegradedConversationNotifiedFlag(conversationId, updated != Conversation.VerificationStatus.DEGRADED)
        }
    }
}

internal sealed interface ReceivedPayload {
    data class Connection(
        val connection: com.wire.kalium.persistence.dao.ConnectionEntity,
        val profiles: com.wire.kalium.logic.data.user.UserProfiles,
        val warningId: String,
    ) : ReceivedPayload
    data class Federation(val event: Event.Federation) : ReceivedPayload
    data class MeetingUpsert(
        val meeting: com.wire.kalium.persistence.dao.meeting.MeetingEntity,
        val profiles: com.wire.kalium.logic.data.user.UserProfiles,
    ) : ReceivedPayload
    data class MeetingDelete(val meetingId: com.wire.kalium.logic.data.id.MeetingId) : ReceivedPayload
    data class NewClient(val client: com.wire.kalium.logic.data.client.Client) : ReceivedPayload
    data class UserDeleted(val userId: UserId) : ReceivedPayload
    data class TeamLeave(val event: Event.Team.MemberLeave) : ReceivedPayload
    data class Deletion(
        val event: Event.Conversation.DeletedConversation,
        val preparedDeletionGroupId: String? = null,
    ) : ReceivedPayload
    data class DecryptionFailure(val message: Message.Regular) : ReceivedPayload
    data class OneOnOne(val resolution: OneOnOneResolution) : ReceivedPayload
    data class ProtocolUpdate(
        val conversationId: com.wire.kalium.logic.data.id.ConversationId,
        val groupId: String?,
        val protocol: ConversationEntity.Protocol,
        val cipherSuite: ConversationEntity.CipherSuite,
        val epoch: ULong,
        val groupState: ConversationEntity.GroupState?,
    ) : ReceivedPayload
    data class MLSReset(
        val conversationId: com.wire.kalium.logic.data.id.ConversationId,
        val oldGroupId: String,
        val newGroupId: String,
        val epoch: ULong,
        val state: ConversationEntity.GroupState,
    ) : ReceivedPayload
    data class Creation(val creation: ConversationCreation) : ReceivedPayload
    data class System(val message: Message.System) : ReceivedPayload
    data class Proposal(val timer: com.wire.kalium.persistence.dao.conversation.ProposalTimerEntity) : ReceivedPayload
    data class ClearProposalTimer(val groupId: String) : ReceivedPayload
    data class Profiles(val profiles: com.wire.kalium.logic.data.user.UserProfiles) : ReceivedPayload
    data class MemberRole(
        val event: Event.Conversation.MemberChanged.MemberChangedRole,
        val fetched: ConversationCreation?,
    ) : ReceivedPayload
    data class Membership(val event: Event.Conversation, val profiles: com.wire.kalium.logic.data.user.UserProfiles) : ReceivedPayload
    data class Application(val message: MessageUnpackResult.ApplicationMessage, val isMLS: Boolean = false) : ReceivedPayload
    data class Metadata(val event: Event.Conversation) : ReceivedPayload
    data class UserUpdate(val event: Event.User.Update) : ReceivedPayload
    data class UserProperty(val event: Event.UserProperty) : ReceivedPayload
    data class LegalHoldRequest(val clientId: String, val lastPreKeyId: Int, val lastPreKey: String) : ReceivedPayload
    data class Configuration(
        val event: Event.FeatureConfig,
        val selfClients: List<com.wire.kalium.persistence.dao.client.InsertClientParam>? = null,
        val selfProtocols: Set<SupportedProtocol>? = null,
        val profiles: com.wire.kalium.logic.data.user.UserProfiles = com.wire.kalium.logic.data.user.UserProfiles(),
        val resolutions: List<OneOnOneResolution> = emptyList(),
    ) : ReceivedPayload
}
