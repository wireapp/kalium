/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.ConversationResponseMapper
import com.wire.kalium.logic.data.conversation.ConversationRoleMapperImpl
import com.wire.kalium.logic.data.conversation.MemberMapperImpl
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreatorImpl
import com.wire.kalium.logic.data.conversation.createForEvent
import com.wire.kalium.logic.data.conversation.pendingConversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserProfiles
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isConversationNotFound
import com.wire.kalium.network.exceptions.isAccessDenied
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.db.MessageEffectDAOs
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.util.DateTimeUtil
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first

/** Metadata needed by later payloads is staged here until the notification's atomic SQL write. */
@Suppress("TooManyFunctions")
internal class ReceivedConversationHandler(
    private val database: UserDatabaseBuilder,
    private val selfUserId: UserId,
    private val users: MembershipUserLoader,
    private val api: ConversationApi?,
) {
    private val staged = linkedMapOf<ConversationId, ConversationCreation>()

    // Decode runs before its SQL write. Reuse canonical lookups within this notification,
    // including the lookup that precedes MLS decryption.
    private val stored = mutableMapOf<ConversationId, ConversationEntity?>()
    private val unavailable = mutableSetOf<ConversationId>()
    private val retainedMLS = mutableMapOf<ConversationId, ConversationEntity.ProtocolInfo.MLSCapable>()

    fun pendingConnection(connection: com.wire.kalium.persistence.dao.ConnectionEntity) {
        staged[connection.qualifiedConversationId.toModel()] =
            ConversationCreation(connection.pendingConversation(), emptyList(), UserProfiles(), null, null)
    }

    suspend fun sentConnection(id: ConversationId, context: MlsCoreCryptoContext?): List<ReceivedPayload> {
        val response = fetch(id).copy(type = ConversationResponse.Type.WAIT_FOR_CONNECTION)
        return listOf(ReceivedPayload.Creation(stageFetched(response, context)))
    }

    /** Null means the conversation fetch confirmed that this event is no longer available. */
    suspend fun fetchMissingConversation(id: ConversationId, context: MlsCoreCryptoContext?): List<ReceivedPayload>? {
        return when {
            conversation(id) != null -> emptyList()
            id in unavailable -> null
            else -> {
                val result = wrapApiRequest {
                    checkNotNull(api) { "Conversation metadata requires authenticated transport" }.fetchConversationDetails(id.toApi())
                }
                if (rememberUnavailable(id, result)) null else {
                    val response = result.orThrow()
                    check(response.id.toModel() == id) { "Required conversation metadata was not returned" }
                    listOf(ReceivedPayload.Creation(stageFetched(response, context)))
                }
            }
        }
    }

    private fun rememberUnavailable(id: ConversationId, result: Either<NetworkFailure, ConversationResponse>): Boolean =
        result.isUnavailableConversationFetch().also { if (it) unavailable.add(id) }

    private suspend fun stageFetched(response: ConversationResponse, context: MlsCoreCryptoContext?): ConversationCreation {
        // Match PersistConversationsUseCase: this checks existence, without creating,
        // joining or replacing the backend epoch with the current native epoch.
        val established = response.groupId?.let { context?.conversationExists(it) } == true
        val selfTeam = database.userDAO.getUserDetailsByQualifiedID(selfUserId.toDao())?.team?.let(::TeamId)
        val mapped = ConversationResponseMapper(selfUserId).fromApiModelToDaoModel(
            response,
            if (established) ConversationEntity.GroupState.ESTABLISHED else ConversationEntity.GroupState.PENDING_JOIN,
            selfTeam,
        )
        val members = MemberMapperImpl(IdMapper(), ConversationRoleMapperImpl()).fromApiModelToDaoModel(response.members)
        // MemberDAO inserts unknown user placeholders, as in the app's SENT branch.
        val update = ConversationCreation(mapped, members, UserProfiles(), null, selfTeam, refreshMembers = true)
        staged[response.id.toModel()] = update
        return update
    }

    suspend fun recovered(response: ConversationResponse, context: MlsCoreCryptoContext): ReceivedPayload {
        val id = response.id.toModel()
        val groupId = checkNotNull(response.groupId)
        stageFetched(response, context)
        established(id, groupId, context.conversationEpoch(groupId))
        return ReceivedPayload.Creation(checkNotNull(staged[id]))
    }

    suspend fun acceptedConnection(connection: com.wire.kalium.persistence.dao.ConnectionEntity) {
        val id = connection.qualifiedConversationId.toModel()
        val current = conversation(id) ?: return
        val members = staged[id]?.members?.takeIf { it.isNotEmpty() }
            ?: database.memberDAO.observeConversationMembers(connection.qualifiedConversationId).first()
        val peer = MemberEntity(connection.qualifiedToId, MemberEntity.Role.Member)
        val updated = (staged[id] ?: ConversationCreation(current, members, UserProfiles(), null, null)).copy(
            conversation = current.copy(type = ConversationEntity.Type.ONE_ON_ONE),
            members = members.filterNot { it.user == peer.user } + peer,
        )
        staged[id] = updated
    }

    suspend fun conversation(id: ConversationId): ConversationEntity? =
        staged[id]?.conversation ?: if (stored.containsKey(id)) stored[id] else {
            database.conversationDAO.getConversationById(id.toDao()).also { stored[id] = it }
        }

    suspend fun members(id: ConversationId): List<com.wire.kalium.persistence.dao.UserIDEntity> =
        staged[id]?.members?.takeIf { it.isNotEmpty() }?.map { it.user }
            ?: database.memberDAO.getConversationMembers(id.toDao())

    suspend fun conversationsWith(user: UserId): List<ConversationEntity> {
        val candidates = database.conversationDAO.getConversationsByUserId(user.toDao()).associateBy { it.id }.toMutableMap()
        staged.values.forEach { update ->
            if (user.toDao() in members(update.conversation.id.toModel())) candidates[update.conversation.id] = update.conversation
            else candidates.remove(update.conversation.id)
        }
        return candidates.values.toList()
    }

    suspend fun established(id: ConversationId, groupId: String, epoch: ULong) {
        val current = checkNotNull(conversation(id))
        val protocol = current.protocolInfo
        if (protocol is ConversationEntity.ProtocolInfo.MLSCapable && protocol.groupId == groupId) {
            resetGroup(ReceivedPayload.MLSReset(id, groupId, groupId, epoch, ConversationEntity.GroupState.ESTABLISHED))
        }
    }

    suspend fun conversationByGroupId(groupId: String): ConversationEntity? {
        staged.values.firstOrNull {
            (it.conversation.protocolInfo as? ConversationEntity.ProtocolInfo.MLSCapable)?.groupId == groupId
        }?.let { return it.conversation }
        val stored = database.conversationDAO.getConversationByGroupID(groupId)
        return stored?.let { conversation(it.id.toModel()) }?.takeIf {
            (it.protocolInfo as? ConversationEntity.ProtocolInfo.MLSCapable)?.groupId == groupId
        }
    }

    suspend fun resetGroup(reset: ReceivedPayload.MLSReset) {
        val current = conversation(reset.conversationId) ?: return
        val protocol = when (val old = current.protocolInfo) {
            is ConversationEntity.ProtocolInfo.MLS -> old.copy(groupId = reset.newGroupId, epoch = reset.epoch, groupState = reset.state)
            is ConversationEntity.ProtocolInfo.Mixed -> old.copy(groupId = reset.newGroupId, epoch = reset.epoch, groupState = reset.state)
            is ConversationEntity.ProtocolInfo.Proteus -> return
        }
        val updated = current.copy(protocolInfo = protocol)
        retainedMLS[reset.conversationId] = protocol
        staged[reset.conversationId] = staged[reset.conversationId]?.copy(conversation = updated)
            ?: ConversationCreation(updated, emptyList(), UserProfiles(), null, null)
    }

    fun includesSelf(id: ConversationId): Boolean = staged[id]?.members?.any { it.user == selfUserId.toDao() } == true

    suspend fun refreshForJoin(id: ConversationId, context: MlsCoreCryptoContext?): List<ReceivedPayload>? {
        if (id in unavailable) return null
        // The app attempts this refresh for every join, but still adds event members
        // when the network fetch fails. Required mapping/native/SQL effects are awaited.
        val result = wrapApiRequest {
            checkNotNull(api) { "Conversation refresh requires authenticated transport" }.fetchConversationDetails(id.toApi())
        }
        return when {
            rememberUnavailable(id, result) -> null
            result is Either.Left && conversation(id) != null -> emptyList()
            else -> {
                val response = result.orThrow()
                check(response.id.toModel() == id) { "Required conversation metadata was not returned" }
                refresh(response, context)
            }
        }
    }

    suspend fun updateProtocol(
        content: EventContentDTO.Conversation.ProtocolUpdate,
        payloadId: String,
        context: MlsCoreCryptoContext?,
    ): List<ReceivedPayload> {
        val id = content.qualifiedConversation.toModel()
        val result = wrapApiRequest { checkNotNull(api).fetchConversationDetails(content.qualifiedConversation) }
        if (result is Either.Left &&
            ((result.value as? NetworkFailure.ServerMiscommunication)?.kaliumException as? KaliumException.InvalidRequestError)
                ?.isConversationNotFound() == true
        ) return emptyList()
        val response = result.orThrow()
        check(response.id.toModel() == id) { "Required conversation metadata was not returned" }
        val current = conversation(id)
        val protocol = ConversationEntity.Protocol.valueOf(content.data.protocol.name)
        val currentProtocol = when (current?.protocolInfo) {
            is ConversationEntity.ProtocolInfo.MLS -> ConversationEntity.Protocol.MLS
            is ConversationEntity.ProtocolInfo.Mixed -> ConversationEntity.Protocol.MIXED
            is ConversationEntity.ProtocolInfo.Proteus -> ConversationEntity.Protocol.PROTEUS
            null -> null
        }
        val metadata = if (current == null || currentProtocol == protocol) refresh(response, context)
        else changeProtocol(current, response, protocol)
        val message = Message.System(
            id = payloadId,
            content = MessageContent.ConversationProtocolChanged(
                com.wire.kalium.logic.data.conversation.Conversation.Protocol.valueOf(protocol.name)
            ),
            conversationId = id,
            date = DateTimeUtil.currentInstant(),
            senderUserId = content.qualifiedFrom.toModel(),
            status = Message.Status.Sent,
            visibility = Message.Visibility.VISIBLE,
            expirationData = null,
        )
        return metadata + ReceivedPayload.System(message)
    }

    private suspend fun changeProtocol(
        current: ConversationEntity,
        response: ConversationResponse,
        protocol: ConversationEntity.Protocol,
    ): List<ReceivedPayload> {
        val id = current.id.toModel()
        val cipher = ConversationEntity.CipherSuite.fromTag(response.mlsCipherSuiteTag)
        val old = current.protocolInfo as? ConversationEntity.ProtocolInfo.MLSCapable
            ?: retainedMLS[id] ?: database.conversationDAO.getConversationMLSInfo(id.toDao())
            // A Proteus conversation created earlier in this notification has not reached
            // SQL yet. These are the existing DAO's MLS column defaults for its insert.
            ?: ConversationEntity.ProtocolInfo.MLS(
                "", ConversationEntity.GroupState.ESTABLISHED, 0uL, kotlin.time.Instant.fromEpochMilliseconds(0), cipher
            )
        retainedMLS[id] = old
        val updatedProtocol = when (protocol) {
            ConversationEntity.Protocol.PROTEUS -> ConversationEntity.ProtocolInfo.Proteus
            ConversationEntity.Protocol.MLS -> ConversationEntity.ProtocolInfo.MLS(
                groupId = response.groupId.orEmpty(),
                groupState = old.groupState,
                epoch = old.epoch,
                keyingMaterialLastUpdate = old.keyingMaterialLastUpdate,
                cipherSuite = cipher,
            )
            ConversationEntity.Protocol.MIXED -> ConversationEntity.ProtocolInfo.Mixed(
                groupId = response.groupId.orEmpty(),
                groupState = old.groupState,
                epoch = old.epoch,
                keyingMaterialLastUpdate = old.keyingMaterialLastUpdate,
                cipherSuite = cipher,
            )
        }
        val updated = current.copy(
            protocolInfo = updatedProtocol,
            receiptMode = if (protocol == ConversationEntity.Protocol.MLS && current.type != ConversationEntity.Type.ONE_ON_ONE) {
                ConversationEntity.ReceiptMode.DISABLED
            } else current.receiptMode,
        )
        staged[id] = staged[id]?.copy(conversation = updated) ?: ConversationCreation(updated, emptyList(), UserProfiles(), null, null)
        return listOf(ReceivedPayload.ProtocolUpdate(id, response.groupId, protocol, cipher))
    }

    private suspend fun refresh(response: ConversationResponse, context: MlsCoreCryptoContext?): List<ReceivedPayload> {
        val id = response.id.toModel()
        val nativeEpoch = if (response.protocol != ConvProtocol.PROTEUS) {
            val groupId = checkNotNull(response.groupId)
            check(groupId.isNotBlank() && response.mlsCipherSuiteTag != null) { "MLS metadata is incomplete" }
            check(conversationByGroupId(groupId)?.id in listOf(null, id.toDao())) { "MLS group belongs to another conversation" }
            val mls = checkNotNull(context) { "MLS was not prepared" }
            if (mls.conversationExists(groupId)) mls.conversationEpoch(groupId) else null
        } else null
        val selfTeam = database.userDAO.getUserDetailsByQualifiedID(selfUserId.toDao())?.team?.let(::TeamId)
        val mapped = ConversationResponseMapper(selfUserId).fromApiModelToDaoModel(
            if (nativeEpoch == null) response else response.copy(epoch = nativeEpoch),
            if (nativeEpoch == null) ConversationEntity.GroupState.PENDING_JOIN else ConversationEntity.GroupState.ESTABLISHED,
            selfTeam,
        )
        val members = MemberMapperImpl(IdMapper(), ConversationRoleMapperImpl()).fromApiModelToDaoModel(response.members)
        val profiles = users.load(members.map { it.user.toModel() }.toSet())
        val update = ConversationCreation(
            mapped,
            members,
            profiles,
            null,
            selfTeam,
            refreshMembers = true,
        )
        staged[id] = update
        return listOf(ReceivedPayload.Creation(update))
    }

    @Suppress("ReturnCount")
    suspend fun create(event: Event.Conversation.NewConversation): List<ReceivedPayload> {
        check(event.conversationId == event.conversation.id.toModel()) { "Creation conversation identity does not match" }
        val existing = conversation(event.conversationId)
        val selfTeam = database.userDAO.getUserDetailsByQualifiedID(selfUserId.toDao())?.team?.let(::TeamId)
        val incoming = map(event.conversation, selfTeam)
        // An established canonical conversation survives an older creation event,
        // including creation followed by protocol migration in the same notification.
        if (existing == null || existing.hasIncompleteMetadata) checkCompatible(existing, incoming)
        staged[event.conversationId]?.let { earlier ->
            check(earlier.event == null || earlier.event.senderUserId == event.senderUserId) {
                "Creation sender conflicts with stored state"
            }
            if (earlier.event != null) return emptyList()
            // A welcome may have fetched metadata earlier in this payload. Only the creation
            // messages remain; replaying its members here could undo an intervening leave.
            staged[event.conversationId] = earlier.copy(event = event)
            val messages = earlier.copy(event = event, members = emptyList(), profiles = UserProfiles(), refreshMembers = false)
            return listOf(ReceivedPayload.Creation(messages))
        }
        val members = if (existing == null) emptySet() else
            database.memberDAO.getConversationMembers(incoming.id).map { it.toModel() }.toSet()
        val expectedMembers = event.conversation.members.otherMembers.map { it.id.toModel() } + selfUserId
        if (existing != null && !existing.hasIncompleteMetadata && members.containsAll(expectedMembers)) {
            val creation = ConversationCreation(existing, emptyList(), users.load(members + event.senderUserId), event, selfTeam)
            staged[event.conversationId] = creation
            return listOf(ReceivedPayload.Creation(creation))
        }
        // An older app may have saved only part of creation. The old event cannot tell us
        // which absent members have since left. Fetch current membership before filling gaps.
        val response = if (existing == null) event.conversation else fetch(event.conversationId)
        return prepare(response, existing, event, selfTeam)
    }

    suspend fun fetchForWelcome(id: ConversationId, selfJoined: Boolean = false): List<ReceivedPayload>? {
        if (id in unavailable) return null
        val existing = conversation(id)
        val canUseCachedMetadata = when {
            existing == null || existing.hasIncompleteMetadata -> false
            selfJoined || includesSelf(id) -> true
            else -> database.memberDAO.getMemberRole(selfUserId.toDao(), id.toDao()) != null
        }
        return if (canUseCachedMetadata) emptyList() else {
            val result = wrapApiRequest {
                checkNotNull(api) { "Welcome metadata requires authenticated transport" }.fetchConversationDetails(id.toApi())
            }
            if (rememberUnavailable(id, result)) null else {
                val response = result.orThrow()
                check(response.id.toModel() == id) { "Required conversation metadata was not returned" }
                val selfTeam = database.userDAO.getUserDetailsByQualifiedID(selfUserId.toDao())?.team?.let(::TeamId)
                prepare(response, existing, null, selfTeam)
            }
        }
    }

    private suspend fun fetch(id: ConversationId): ConversationResponse = wrapApiRequest {
        checkNotNull(api) { "Conversation metadata requires authenticated transport" }.fetchConversationDetails(id.toApi())
    }.orThrow().also { check(it.id.toModel() == id) { "Required conversation metadata was not returned" } }

    private suspend fun prepare(
        response: ConversationResponse,
        existing: ConversationEntity?,
        event: Event.Conversation.NewConversation?,
        selfTeam: TeamId?,
    ): List<ReceivedPayload> {
        val mapped = map(response, selfTeam)
        checkCompatible(existing, mapped)
        val members = MemberMapperImpl(IdMapper(), ConversationRoleMapperImpl()).fromApiModelToDaoModel(response.members)
        check(members.any { it.user == selfUserId.toDao() }) { "Creation requires current self membership" }
        val profiles = users.load(members.map { it.user.toModel() }.toSet() + listOfNotNull(event?.senderUserId))
        val conversation = if (existing == null) mapped else if (!existing.hasIncompleteMetadata) existing else mapped.copy(
            // The fresh metadata repairs the placeholder, while local read/privacy state survives.
            protocolInfo = existing.protocolInfo.takeIf {
                it is ConversationEntity.ProtocolInfo.MLS && it.groupId.isNotBlank()
            } ?: mapped.protocolInfo,
            mutedStatus = if (existing.mutedTime > mapped.mutedTime) existing.mutedStatus else mapped.mutedStatus,
            mutedTime = maxOf(existing.mutedTime, mapped.mutedTime),
            archived = if ((existing.archivedInstant?.toEpochMilliseconds() ?: 0) >
                (mapped.archivedInstant?.toEpochMilliseconds() ?: 0)
            ) existing.archived else mapped.archived,
            archivedInstant = listOfNotNull(existing.archivedInstant, mapped.archivedInstant).maxOrNull(),
            userMessageTimer = existing.userMessageTimer,
        )
        val creation = ConversationCreation(conversation, members, profiles, event, selfTeam)
        staged[response.id.toModel()] = creation
        return listOf(ReceivedPayload.Creation(creation))
    }

    private fun map(response: ConversationResponse, selfTeam: TeamId?): ConversationEntity {
        if (response.protocol != ConvProtocol.PROTEUS) {
            check(!response.groupId.isNullOrBlank() && response.mlsCipherSuiteTag != null) { "MLS metadata is incomplete" }
        }
        return ConversationResponseMapper(selfUserId).fromApiModelToDaoModel(
            response,
            ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE,
            selfTeam
        ).also {
            check(
                it.type.isGroup || it.type in listOf(
                    ConversationEntity.Type.ONE_ON_ONE, ConversationEntity.Type.SELF, ConversationEntity.Type.CONNECTION_PENDING
                )
            ) {
                "Creation requires unsupported conversation lifecycle work"
            }
        }
    }

    private suspend fun checkCompatible(existing: ConversationEntity?, incoming: ConversationEntity) {
        val incomingProtocol = incoming.protocolInfo
        if (incomingProtocol is ConversationEntity.ProtocolInfo.MLSCapable) {
            val canonicalId = conversationByGroupId(incomingProtocol.groupId)?.id
            check(canonicalId in listOf(null, incoming.id)) { "MLS group belongs to another conversation" }
        }
        if (existing == null) return
        check(existing.removedBy == null) { "Creation cannot recover a removed conversation" }
        check(existing.hasIncompleteMetadata || existing.type == incoming.type) {
            "Creation cannot change the conversation lifecycle"
        }
        val protocol = existing.protocolInfo
        check(
            protocol !is ConversationEntity.ProtocolInfo.MLSCapable ||
                protocol.groupState in listOf(
                    ConversationEntity.GroupState.ESTABLISHED,
                    ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE
                )
        ) { "Creation cannot reset or recover an existing MLS group" }
        // A placeholder's default Proteus protocol is unknown. A stored MLS group ID,
        // however, still owns native state even when other metadata needs repair.
        val unknownProtocol = protocol is ConversationEntity.ProtocolInfo.Proteus ||
            protocol is ConversationEntity.ProtocolInfo.MLS && protocol.groupId.isBlank()
        if (existing.hasIncompleteMetadata && unknownProtocol) return
        check(
            protocol is ConversationEntity.ProtocolInfo.Proteus && incomingProtocol is ConversationEntity.ProtocolInfo.Proteus ||
                protocol is ConversationEntity.ProtocolInfo.MLSCapable && incomingProtocol is ConversationEntity.ProtocolInfo.MLSCapable &&
                protocol.groupId == incomingProtocol.groupId && protocol.cipherSuite == incomingProtocol.cipherSuite
        ) { "Creation cannot migrate or reset an existing conversation" }
    }
}

/** Apply only to fetchConversationDetails; a status or label alone does not establish staleness. */
private fun Either<NetworkFailure, ConversationResponse>.isUnavailableConversationFetch(): Boolean {
    val failure = (this as? Either.Left)?.value as? NetworkFailure.ServerMiscommunication
    val error = failure?.kaliumException as? KaliumException.InvalidRequestError ?: return false
    return error.errorResponse.code == HttpStatusCode.NotFound.value && error.isConversationNotFound() ||
        error.errorResponse.code == HttpStatusCode.Forbidden.value && error.isAccessDenied()
}

internal data class ConversationCreation(
    val conversation: ConversationEntity,
    val members: List<MemberEntity>,
    val profiles: UserProfiles,
    val event: Event.Conversation.NewConversation?,
    val selfTeam: TeamId?,
    val refreshMembers: Boolean = false,
) {
    suspend fun persist(data: MessageEffectDAOs, selfUserId: UserId, persistMessage: PersistMessageUseCase) {
        val existing = data.conversations.getConversationById(conversation.id)
        // Preserve the app's creation-message decision, independently of metadata/member repair.
        val createMessages = when (val protocol = existing?.protocolInfo) {
            is ConversationEntity.ProtocolInfo.MLSCapable -> protocol.groupState != ConversationEntity.GroupState.ESTABLISHED
            is ConversationEntity.ProtocolInfo.Proteus -> false
            null -> true
        }
        if (refreshMembers || existing == null || existing.hasIncompleteMetadata) data.conversations.insertConversation(conversation)
        profiles.persist(data.users, data.apps)
        // In particular, never restore an older role on replay or partial-state completion.
        if (refreshMembers && conversation.type.isGroup) data.members.updateFullMemberList(members, conversation.id)
        else data.members.insertMembersWithQualifiedId(members, conversation.id)
        if (event != null && createMessages) {
            var index = 0
            val creator = NewGroupConversationSystemMessagesCreatorImpl(
                PersistMessageUseCase { message ->
                    check(message is Message.System)
                    // A duplicated server creation can have a different notification ID. Its
                    // conversation and creation time still identify the same system messages.
                    // Fail here even when the shared app creator treats a write as best effort.
                    persistMessage(
                        message.copy(id = "${event.conversationId}:${event.dateTime}:creation:${index++}", date = event.dateTime)
                    ).also { it.orThrow() }
                },
                { Either.Right(selfTeam) },
                QualifiedIdMapper(selfUserId),
                selfUserId,
            )
            creator.createForEvent(event, selfTeam).orThrow()
        }
        if (event != null) data.conversations.updateConversationModifiedDate(conversation.id, DateTimeUtil.currentInstant())
    }
}
