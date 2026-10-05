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

import com.wire.kalium.cryptography.RequireConfirmedCryptoRollback
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.logic.data.event.conversationAccessUpdate
import com.wire.kalium.logic.data.event.conversationCodeUpdated
import com.wire.kalium.logic.data.event.conversationCodeDeleted
import com.wire.kalium.logic.data.event.conversationChannelPermissionUpdate
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.conversationMemberJoin
import com.wire.kalium.logic.data.event.conversationMemberLeave
import com.wire.kalium.logic.data.event.conversationMessageTimerUpdate
import com.wire.kalium.logic.data.event.conversationMemberUpdate
import com.wire.kalium.logic.data.event.conversationReceiptModeUpdate
import com.wire.kalium.logic.data.event.conversationRenamed
import com.wire.kalium.logic.data.event.deleteUserProperties
import com.wire.kalium.logic.data.event.featureConfig
import com.wire.kalium.logic.data.event.metadataSystemMessageId
import com.wire.kalium.logic.data.event.newMLSMessage
import com.wire.kalium.logic.data.event.newMeetingConversation
import com.wire.kalium.logic.data.event.newConversation
import com.wire.kalium.logic.data.event.newMessage
import com.wire.kalium.logic.data.event.updateUserProperties
import com.wire.kalium.logic.data.event.userUpdate
import com.wire.kalium.logic.data.featureConfig.FeatureConfigMapperImpl
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import kotlin.time.Clock
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageFailureHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageFailureResolution
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpackerImpl
import com.wire.kalium.logic.sync.receiver.handler.SessionRefreshSuggestedEventHandler
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.persistence.dao.event.EventEntity
import com.wire.kalium.persistence.dao.event.NewEventEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** First bounded server-ordered path. The app retains its gathered-batch transaction boundary. */
@Suppress("LongParameterList")
class NotificationProcessor(
    private val database: UserDatabaseBuilder,
    private val notificationApi: NotificationApi,
    private val clientId: String,
    private val selfUserId: UserId,
    private val proteus: ProteusClient,
    private val mls: MLSClient?,
    private val options: ReceivingOptions = ReceivingOptions(),
    private val serverConfigLinks: com.wire.kalium.logic.configuration.server.ServerConfig.Links? = null,
    private val sessionRefresh: SessionRefreshSuggestedEventHandler? = null,
    private val userDetailsApi: com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi? = null,
    private val conversationApi: com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi? = null,
    private val meetingApi: com.wire.kalium.network.api.base.authenticated.meeting.MeetingApi? = null,
    private val teamsApi: com.wire.kalium.network.api.base.authenticated.TeamsApi? = null,
    private val deleteFile: (String) -> Unit = { error("Local file cleanup is unavailable") },
    assetApi: com.wire.kalium.network.api.base.authenticated.asset.AssetApi? = null,
    assetDirectory: String? = null,
    private val clientApi: com.wire.kalium.network.api.base.authenticated.client.ClientApi? = null,
    private val apiVersion: Int = 6,
    private val selfApi: com.wire.kalium.network.api.base.authenticated.self.SelfApi? = null,
    private val hasMLSCredential: suspend (com.wire.kalium.cryptography.MLSCiphersuite) -> Boolean = { false },
    private val invalidateReceiving: suspend () -> Unit = { error("Account invalidation is unavailable") },
    private val evaluateCalls: (suspend (List<ReceivedCallEvent>) -> List<ReceivedCallResult>)? = null,
    private val diagnostics: ReceivingDiagnostics? = null,
    private val recovery: MLSReceiveRecovery? = null,
    // Opted in only by the Apple NSE factory. Existing consumers retain prefix processing.
    private val isolateConversations: Boolean = false,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private var accountInvalidated = false
    private val unpacker = ProteusMessageUnpackerImpl(selfUserId)
    private val events = database.notificationEventStore
    private val previewImages = ReceivedLinkPreviewImages(options.linkPreviewEnabled, assetApi, assetDirectory)

    /** Only the target can return content. Unsupported earlier events remain unprocessed. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun process(targetId: String): ReceivedNotificationResult = try {
        if (isolateConversations) processIsolatedEvents(targetId) else processEvents(targetId)
    } catch (failure: Throwable) {
        if (failure !is kotlinx.coroutines.CancellationException) diagnostics?.failed(failure)
        throw failure
    }

    @Suppress("ReturnCount")
    private suspend fun processEvents(targetId: String): ReceivedNotificationResult {
        require(targetId.isNotBlank())
        diagnostics?.stage = "LOAD_QUEUE"
        terminalAccountResult()?.let { return it }
        cleanupConversations()
        previewImages.cleanupAbandoned(database.assetDAO)
        if (database.eventDAO.getEventById(targetId)?.isProcessed == true) return withPendingCalls(null, "ALREADY_PROCESSED")
        database.eventDAO.setAllUnprocessedEventsAsPending()
        val targetMessage: ReceivedMessageResult?
        var canFetch = true
        while (true) {
            currentCoroutineContext().ensureActive()
            val next = events.nextPending()
            if (next != null) {
                diagnostics?.beginEvent(next.eventId == targetId, next.eventId)
                val effects = processNotification(next)
                currentCoroutineContext().ensureActive()
                diagnostics?.stage = "MARK_PROCESSED"
                CallSignalJournal(database).committed(effects.calls)
                cleanupConversations()
                if (recovery == null) database.eventDAO.markEventAsProcessed(next.eventId)
                else recovery.acknowledge(listOf(next.eventId))
                if (next.eventId == targetId) {
                    targetMessage = effects.message
                    break
                }
            } else {
                check(canFetch) { "Target notification was not found" }
                diagnostics?.fetching()
                val cursor = events.cursor()
                val page = wrapApiRequest {
                    if (cursor == null) notificationApi.getAllNotifications(PAGE_SIZE, clientId)
                    else notificationApi.notificationsByBatch(PAGE_SIZE, clientId, cursor)
                }.orThrow()
                check(!page.hasMore || page.notifications.isNotEmpty()) { "Notification page did not advance" }
                check(page.notifications.lastOrNull()?.id != cursor || page.notifications.isEmpty()) {
                    "Notification cursor did not advance"
                }
                diagnostics?.stage = "STORE_PAGE"
                events.storePage(cursor, page.notifications.map { NewEventEntity(it.id, it.payload, it.transient, false) })
                terminalAccountResult()?.let { return it }
                canFetch = page.hasMore
            }
        }
        return withPendingCalls(targetMessage)
    }

    private suspend fun terminalAccountResult(): ReceivedNotificationResult? {
        if (!accountInvalidated && pendingAccountInvalidation(database, selfUserId, clientId) == null) return null
        if (!accountInvalidated) {
            invalidateReceiving()
            accountInvalidated = true
            com.wire.kalium.common.logger.kaliumLogger.i("NSE_EVENT stage=account_invalidated")
        }
        return ReceivedNotificationResult(
            null,
            presentationReason = "APP_WORK_PENDING",
            appWorkPending = NotificationAppWork.ACCOUNT_INVALIDATION
        )
    }

    /** NSE-only scan: a deferred row remains pending while unrelated messages may pass it. */
    @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    private suspend fun processIsolatedEvents(targetId: String): ReceivedNotificationResult {
        require(targetId.isNotBlank())
        diagnostics?.stage = "LOAD_QUEUE"
        terminalAccountResult()?.let { return it }
        cleanupConversations()
        previewImages.cleanupAbandoned(database.assetDAO)
        if (database.eventDAO.getEventById(targetId)?.isProcessed == true) {
            return if (events.nextPending() != null) withoutPresentation(null, "DEFERRED_ORDERING_BARRIER")
            else withPendingCalls(null, "ALREADY_PROCESSED")
        }
        database.eventDAO.setAllUnprocessedEventsAsPending()
        var scannedId = 0L
        var canFetch = true
        val deferred = mutableSetOf<NotificationDependency>()
        while (true) {
            currentCoroutineContext().ensureActive()
            val next = events.nextPendingAfter(scannedId)
            if (next == null) {
                check(canFetch) { "Target notification was not found" }
                diagnostics?.fetching()
                val cursor = events.cursor()
                val page = wrapApiRequest {
                    if (cursor == null) notificationApi.getAllNotifications(PAGE_SIZE, clientId)
                    else notificationApi.notificationsByBatch(PAGE_SIZE, clientId, cursor)
                }.orThrow()
                check(!page.hasMore || page.notifications.isNotEmpty()) { "Notification page did not advance" }
                check(page.notifications.lastOrNull()?.id != cursor || page.notifications.isEmpty()) {
                    "Notification cursor did not advance"
                }
                events.storePage(cursor, page.notifications.map { NewEventEntity(it.id, it.payload, it.transient, false) })
                terminalAccountResult()?.let { return it }
                canFetch = page.hasMore
                continue
            }
            scannedId = next.id
            val payload = EventResponseToStore(next.eventId, next.payload, next.transient).toEventResponse().payload
            check(!payload.isNullOrEmpty()) { "A payload is required" }
            val dependencies = dependencies(payload)
            if (deferred.isNotEmpty() && dependencies == null) return withoutPresentation(null, "DEFERRED_ORDERING_BARRIER")
            val retryIn = (events.deferredUntil(next.eventId) ?: 0L) - nowEpochMillis()
            val coolingDown = retryIn in 1..DEFERRED_RETRY_MILLIS
            if (dependencies != null && (coolingDown || dependencies.any { it in deferred })) {
                deferred += dependencies
                if (next.eventId == targetId) return withoutPresentation(null, "DEFERRED_CONVERSATION")
                continue
            }
            diagnostics?.beginEvent(next.eventId == targetId, next.eventId)
            val effects = try {
                processIsolatedNotification(next, dependencies != null, deferred.isNotEmpty())
            } catch (_: DeferredNotification) {
                // The strict native boundary restores this control signal only after confirmed rollback.
                // A commit or rollback failure stops the whole receive attempt.
                currentCoroutineContext().ensureActive()
                events.defer(next.eventId, nowEpochMillis() + DEFERRED_RETRY_MILLIS)
                deferred += checkNotNull(dependencies)
                if (next.eventId == targetId) return withoutPresentation(null, "DEFERRED_CONVERSATION")
                continue
            } catch (_: NotificationOrderingBarrier) {
                return withoutPresentation(null, "DEFERRED_ORDERING_BARRIER")
            }
            currentCoroutineContext().ensureActive()
            diagnostics?.stage = "MARK_PROCESSED"
            CallSignalJournal(database).committed(effects.calls)
            cleanupConversations()
            if (recovery == null) events.markProcessed(listOf(next.eventId), emptyMap())
            else recovery.acknowledge(listOf(next.eventId))
            if (next.eventId == targetId) {
                return if (deferred.isEmpty()) withPendingCalls(effects.message)
                else withoutPresentation(effects.message, "DEFERRED_PRESENTATION_POLICY")
            }
        }
    }

    /** An opaque MLS payload may hide a buffered self-control (availability or call rejection).
     * Independent canonical writes are safe; alerts and AVS evaluation need the complete prefix.
     */
    private suspend fun withoutPresentation(message: ReceivedMessageResult?, reason: String) = ReceivedNotificationResult(
        message = message?.copy(shouldNotify = false, mayPreview = false, text = null, suppressionReason = reason),
        presentationReason = reason,
        callSignalsPending = CallSignalJournal(database).signals().any { it.submittedGeneration == null },
    )

    private suspend fun processIsolatedNotification(
        event: EventEntity,
        canDefer: Boolean,
        crossesGap: Boolean,
    ): NotificationEffectsResult = withContext(RequireConfirmedCryptoRollback) {
        try {
            processNotification(event, canDefer, crossesGap)
        } catch (failure: ProteusException) {
            // ProteusClient wraps non-Proteus callback errors exactly once. Do not traverse
            // arbitrary cause chains: a native failure must never become a local deferral.
            val callback = failure.cause
            val wrappedCallback = failure.code == ProteusException.Code.UNKNOWN_ERROR && failure.intCode == null
            if (wrappedCallback && callback is DeferredNotification) throw callback
            if (wrappedCallback && callback is NotificationOrderingBarrier) throw callback
            throw failure
        }
    }

    @Suppress("ReturnCount") // Every unknown dependency fails closed before any speculative transaction.
    private suspend fun dependencies(payload: List<EventContentDTO>): Set<NotificationDependency>? {
        val dependencies = notificationDependencies(payload)?.toMutableSet() ?: return null
        dependencies.filterIsInstance<NotificationDependency.Conversation>().forEach { dependency ->
            val conversation = database.conversationDAO.getConversationById(dependency.id.toModel().toDao()) ?: return null
            // Unknown and self conversations cannot prove independence from other conversations.
            if (conversation.type == ConversationEntity.Type.SELF) return null
            (conversation.protocolInfo as? ConversationEntity.ProtocolInfo.MLSCapable)?.let {
                dependencies += NotificationDependency.MLSGroup(it.groupId)
            }
        }
        return dependencies
    }

    private suspend fun cleanupConversations() {
        val cleanup = PendingConversationCleanup(database, deleteFile)
        cleanup.discardLegacyRequests()
        if (database.pendingActionDAO.getByActionType(
                PendingActionType.WIPE_MLS_GROUP
            ).isEmpty()
        ) return
        val pending = if (mls != null) mls.transaction("nse-local-conversation-cleanup") { cleanup.wipePending(it) }
            else cleanup.wipePending(null)
        cleanup.committed(pending)
    }

    private suspend fun withPendingCalls(message: ReceivedMessageResult?, reason: String? = null): ReceivedNotificationResult {
        diagnostics?.stage = "PRESENTATION"
        if (evaluateCalls == null) return ReceivedNotificationResult(message, presentationReason = reason)
        val pending = CallSignalJournal(database).signals().filter { it.submittedGeneration == null }
        val events = pending.map {
            ReceivedCallEvent(
                it.payload,
                com.wire.kalium.persistence.dao.QualifiedIDEntity(it.conversationValue, it.conversationDomain),
                com.wire.kalium.persistence.dao.QualifiedIDEntity(it.senderValue, it.senderDomain),
                it.senderClientId,
                kotlin.time.Instant.fromEpochMilliseconds(it.timeMillis),
                it.isGroup,
                it.isMLS,
                it.messageId
            )
        }
        val results = if (events.isEmpty()) emptyList() else evaluateCalls.invoke(events)
        return ReceivedNotificationResult(message, results, reason, pending.isNotEmpty())
    }

    private suspend fun processNotification(
        event: EventEntity,
        canDefer: Boolean = false,
        crossesGap: Boolean = false,
    ): NotificationEffectsResult {
        check(!event.isLive) { "Notification must be pending before processing" }
        val payload = EventResponseToStore(event.eventId, event.payload, event.transient).toEventResponse().payload
        check(!payload.isNullOrEmpty()) { "A payload is required" }
        val receivingContext = currentCoroutineContext()
        // Match the app's transaction order over both prepared stores.
        // SQL commits once; replay handles each native store committing separately.
        return proteus.transaction("nse-notification") { proteusContext ->
            if (mls != null) mls.transaction("nse-notification") { mlsContext ->
                // CoreCrypto 10.4 makes its whole transaction NonCancellable. Restore the owned
                // attempt for our work; cancellation aborts SQL and then unwinds both native stores.
                withContext(receivingContext) {
                    decodeAndPersist(event.eventId, payload, proteusContext, mlsContext, canDefer, crossesGap)
                }
            } else withContext(receivingContext) {
                decodeAndPersist(event.eventId, payload, proteusContext, canDefer = canDefer, crossesGap = crossesGap)
            }
        }
    }

    @Suppress("LongMethod", "ThrowsCount") // Keep the pre-persistence failure boundary separate from writes and native commit.
    private suspend fun decodeAndPersist(
        eventId: String,
        payload: List<EventContentDTO>,
        proteusContext: ProteusCoreCryptoContext? = null,
        mlsContext: MlsCoreCryptoContext? = null,
        canDefer: Boolean = false,
        crossesGap: Boolean = false,
    ): NotificationEffectsResult {
        val users = MembershipUserLoader(database.userDAO, userDetailsApi, teamsApi, selfUserId)
        val conversations = ReceivedConversationHandler(database, selfUserId, users, conversationApi)
        val mlsHandler = ReceivedMLSHandler(database, selfUserId, conversations, recovery)
        val oneOnOnes = ReceivedOneOnOneHandler(selfUserId, conversations, users, mlsHandler)
        val configuration = ReceivedMLSConfigurationHandler(
            database = database,
            users = users,
            self = selfUserId,
            clientId = com.wire.kalium.logic.data.conversation.ClientId(clientId),
            clientApi = clientApi,
            selfApi = selfApi,
            oneOnOnes = oneOnOnes,
            mlsSupported = com.wire.kalium.logic.feature.user.isMLSApiSupported(apiVersion),
            hasCredential = hasMLSCredential,
        )
        var deferrable = canDefer
        val (decoded, verifications) = try {
            val decoded = decode(
                eventId, payload, mlsHandler, users, conversations, oneOnOnes, configuration, proteusContext, mlsContext,
            ) { effects ->
                if (effects.any { !it.canCrossNotificationGap() }) {
                    deferrable = false
                    if (crossesGap) throw NotificationOrderingBarrier()
                }
            }.toMutableList()
            diagnostics?.stage = "MLS_VERIFICATION"
            val verifications = mlsHandler.verificationUpdates()
            diagnostics?.stage = "LOAD_PROFILES"
            decoded += ReceivedPayload.Profiles(users.load(verifications.flatMap { it.identities.keys }.toSet()))
            decoded to verifications
        } catch (failure: KaliumSyncException) {
            if (deferrable && failure.coreFailureCause.canDeferNotification()) throw DeferredNotification(failure)
            throw failure
        }
        diagnostics?.stage = "PERSIST"
        return try {
            NotificationEffects(
                database = database,
                selfUserId = selfUserId,
                mlsEnabled = mls != null,
                options = options,
                deleteFile = deleteFile,
                previewImages = previewImages,
                serverConfigLinks = serverConfigLinks,
                callingEnabled = evaluateCalls != null,
            ).persist(decoded, verifications).also {
                if (mlsContext != null && recovery != null) {
                    // SQL can commit before native rollback. The durable identity survives that gap.
                    payload.filter {
                        it is EventContentDTO.Conversation.DeletedConversationDTO ||
                            it is EventContentDTO.Conversation.DeletedMeetingConversationDTO
                    }.forEach { recovery.resumeDeletion(mlsContext, ReceivedPayloadIdentity(eventId, it)) }
                    recovery.recordProcessed(
                        mlsContext,
                        payload.filterIsInstance<EventContentDTO.Conversation.NewMLSMessageDTO>()
                            .map { message -> newMLSMessage(eventId, message) },
                        payload.map { ReceivedPayloadIdentity(eventId, it) },
                    )
                }
            }
        } finally {
            // SQL may have committed even when withContext reports cancellation. Preserve all
            // canonical references before deleting files from a failed or interrupted attempt.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { previewImages.cleanupCreated(database.assetDAO) }
        }
    }

    private suspend fun prepareDeletion(
        eventId: String,
        content: EventContentDTO,
        conversationId: ConversationId,
        context: MlsCoreCryptoContext?,
    ): String? {
        if (context == null || recovery == null) return null
        val protocol = database.conversationDAO.getConversationById(conversationId.toDao())?.protocolInfo
        val groupId = (protocol as? ConversationEntity.ProtocolInfo.MLSCapable)?.groupId
        return if (recovery.prepareDeletion(ReceivedPayloadIdentity(eventId, content), groupId)) groupId else null
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList")
    private suspend fun decode(
        eventId: String,
        payload: List<EventContentDTO>,
        mlsHandler: ReceivedMLSHandler,
        users: MembershipUserLoader,
        conversations: ReceivedConversationHandler,
        oneOnOnes: ReceivedOneOnOneHandler,
        configuration: ReceivedMLSConfigurationHandler,
        proteusContext: ProteusCoreCryptoContext? = null,
        mlsContext: MlsCoreCryptoContext? = null,
        onDecoded: (List<ReceivedPayload>) -> Unit = {},
    ): List<ReceivedPayload> = payload.flatMapIndexed { index, content ->
        currentCoroutineContext().ensureActive()
        diagnostics?.decoding(content)
        val effectsProcessed = mlsContext != null && recovery?.wereEffectsProcessed(
            mlsContext, ReceivedPayloadIdentity(eventId, content)
        ) == true
        // The outer Proteus transaction can roll back after MLS commits. Advance it even on effects replay.
        if (effectsProcessed && content !is EventContentDTO.Conversation.NewMessageDTO) return@flatMapIndexed emptyList()
        val metadataId = when (content) {
            is EventContentDTO.Conversation.ConversationRenameDTO -> content.qualifiedConversation
            is EventContentDTO.Conversation.ReceiptModeUpdate -> content.qualifiedConversation
            is EventContentDTO.Conversation.MessageTimerUpdate -> content.qualifiedConversation
            is EventContentDTO.Conversation.MemberLeaveDTO -> content.qualifiedConversation
            is EventContentDTO.Conversation.MemberUpdateDTO -> when (
                conversationMemberUpdate(metadataSystemMessageId(eventId, index), content)
            ) {
                is Event.Conversation.MemberChanged.IgnoredMemberChanged,
                is Event.Conversation.MemberChanged.MemberChangedRole -> null
                else -> content.qualifiedConversation
            }
            else -> null
        }
        val requiredMetadata = if (metadataId == null) emptyList() else {
            diagnostics?.stage = "EVENT_FETCH_CONVERSATION"
            conversations.fetchMissingConversation(metadataId.toModel(), mlsContext)
                ?: return@flatMapIndexed emptyList()
        }
        val decoded = requiredMetadata + when (content) {
            is EventContentDTO.Conversation.NewConversationDTO -> {
                val event = newConversation(eventId, content, index)
                conversations.create(event) + oneOnOnes.resolve(event.conversationId, metadataSystemMessageId(eventId, index), mlsContext)
            }
            is EventContentDTO.Conversation.NewMeetingConversationDTO -> {
                val event = newMeetingConversation(eventId, content, index)
                conversations.create(event) + oneOnOnes.resolve(event.conversationId, metadataSystemMessageId(eventId, index), mlsContext)
            }
            is EventContentDTO.Conversation.MLSWelcomeDTO -> {
                val id = content.qualifiedConversation.toModel()
                diagnostics?.stage = "WELCOME_FETCH_CONVERSATION"
                val metadata = conversations.fetchForWelcome(id, mlsHandler.hasJoined(id))
                    ?: return@flatMapIndexed emptyList()
                val accepted = mlsHandler.welcome(checkNotNull(mlsContext), content, metadataSystemMessageId(eventId, index))
                metadata + if (accepted) oneOnOnes.resolve(id, metadataSystemMessageId(eventId, index), mlsContext) else emptyList()
            }
            is EventContentDTO.Conversation.NewMessageDTO -> {
                check(content.data.recipient == clientId) { "Notification belongs to another client" }
                val event = newMessage(eventId, content)
                val result = unpacker.unpackProteusMessage(checkNotNull(proteusContext), event) { it }
                if (effectsProcessed) return@flatMapIndexed emptyList()
                when (result) {
                    is Either.Right -> listOf(ReceivedPayload.Application(result.value))
                    is Either.Left -> if (
                        ProteusMessageFailureHandler.handleFailure(result.value) == ProteusMessageFailureResolution.Ignore
                    ) {
                        emptyList()
                    } else listOf(receivedProteusDecryptionFailure(event, result.value, selfUserId))
                }
            }
            is EventContentDTO.Conversation.NewMLSMessageDTO -> {
                diagnostics?.stage = "MLS_FETCH_CONVERSATION"
                val context = checkNotNull(mlsContext)
                val event = newMLSMessage(eventId, content)
                mlsHandler.resume(context, event)?.let { return@flatMapIndexed it }
                val metadata = conversations.fetchMissingConversation(content.qualifiedConversation.toModel(), context)
                    ?: return@flatMapIndexed emptyList()
                metadata + mlsHandler.decrypt(context, event, metadataSystemMessageId(eventId, index))
            }
            is EventContentDTO.Conversation.DeletedConversationDTO -> listOf(
                ReceivedPayload.Deletion(
                    Event.Conversation.DeletedConversation(
                        metadataSystemMessageId(eventId, index),
                        content.qualifiedConversation.toModel(),
                        content.qualifiedFrom.toModel(),
                        kotlin.time.Instant.parse(content.time)
                    ),
                    preparedDeletionGroupId = prepareDeletion(eventId, content, content.qualifiedConversation.toModel(), mlsContext),
                )
            )
            is EventContentDTO.Conversation.DeletedMeetingConversationDTO -> listOf(
                ReceivedPayload.Deletion(
                    Event.Conversation.DeletedConversation(
                        metadataSystemMessageId(eventId, index),
                        content.qualifiedConversation.toModel(),
                        content.qualifiedFrom.toModel(),
                        kotlin.time.Instant.parse(content.time)
                    ),
                    preparedDeletionGroupId = prepareDeletion(eventId, content, content.qualifiedConversation.toModel(), mlsContext),
                )
            )
            is EventContentDTO.Conversation.MlsResetConversationDTO ->
                listOf(mlsHandler.reset(checkNotNull(mlsContext), content))
            is EventContentDTO.Conversation.ProtocolUpdate ->
                conversations.updateProtocol(content, metadataSystemMessageId(eventId, index), mlsContext)
            is EventContentDTO.Conversation.MemberJoinDTO -> {
                val event = conversationMemberJoin(eventId, content, index)
                diagnostics?.stage = "JOIN_REFRESH_CONVERSATION"
                val metadata = conversations.refreshForJoin(event.conversationId, mlsContext)
                    ?: return@flatMapIndexed emptyList()
                checkNotNull(
                    conversations.conversation(event.conversationId)
                ) { "Conversation must already exist" }
                mlsHandler.join(event)
                metadata + ReceivedPayload.Membership(event, users.load(event.members.map { it.id }.toSet()))
            }
            is EventContentDTO.Conversation.MemberLeaveDTO -> {
                val event = conversationMemberLeave(eventId, content, index)
                val profiles = users.load(event.removedList.toSet())
                mlsHandler.leave(mlsContext, event)
                listOf(ReceivedPayload.Membership(event, profiles))
            }
            is EventContentDTO.User.NewLegalHoldRequestDTO,
            is EventContentDTO.User.LegalHoldEnabledDTO,
            is EventContentDTO.User.LegalHoldDisabledDTO -> emptyList()
            // Match the app event mapper: an event type introduced by a newer backend is
            // acknowledged without effects so it cannot hold up later supported events.
            is EventContentDTO.Unknown -> emptyList()
            // Typing is transient app UI state; it must not hold up durable receiving.
            is EventContentDTO.Conversation.ConversationTypingDTO -> emptyList()
            is EventContentDTO.Conversation.AccessUpdate ->
                listOf(ReceivedPayload.Metadata(conversationAccessUpdate(metadataSystemMessageId(eventId, index), content)))
            is EventContentDTO.Conversation.CodeUpdated -> listOf(ReceivedPayload.Metadata(conversationCodeUpdated(eventId, content)))
            is EventContentDTO.Conversation.CodeDeleted -> listOf(ReceivedPayload.Metadata(conversationCodeDeleted(eventId, content)))
            is EventContentDTO.Conversation.ChannelAddPermissionUpdate ->
                listOf(ReceivedPayload.Metadata(conversationChannelPermissionUpdate(eventId, content)))
            is EventContentDTO.Conversation.ConversationRenameDTO ->
                listOf(ReceivedPayload.Metadata(conversationRenamed(eventId, content, index)))
            is EventContentDTO.Conversation.ReceiptModeUpdate ->
                listOf(ReceivedPayload.Metadata(conversationReceiptModeUpdate(eventId, content, index)))
            is EventContentDTO.Conversation.MessageTimerUpdate ->
                listOf(ReceivedPayload.Metadata(conversationMessageTimerUpdate(eventId, content, index)))
            is EventContentDTO.Conversation.MemberUpdateDTO -> {
                val event = conversationMemberUpdate(metadataSystemMessageId(eventId, index), content)
                when (event) {
                    is Event.Conversation.MemberChanged.MemberChangedRole -> {
                        diagnostics?.stage = "EVENT_FETCH_CONVERSATION"
                        val metadata = conversations.fetchMissingConversation(event.conversationId, mlsContext)
                            ?: return@flatMapIndexed emptyList()
                        // Compare the old role before the fetched member list is persisted.
                        listOf(ReceivedPayload.MemberRole(event, (metadata.singleOrNull() as? ReceivedPayload.Creation)?.creation))
                    }
                    is Event.Conversation.MemberChanged.IgnoredMemberChanged -> emptyList()
                    else -> listOf(ReceivedPayload.Metadata(event))
                }
            }
            is EventContentDTO.User.UpdateDTO -> {
                val event = userUpdate(selfUserId, eventId, content)
                users.update(event)
                listOf(ReceivedPayload.UserUpdate(event))
            }
            is EventContentDTO.User.NewClientDTO -> {
                val client = com.wire.kalium.logic.data.client.clientFromDTO(content.client)
                if (client.id.value == clientId) emptyList() else listOf(ReceivedPayload.NewClient(client))
            }
            is EventContentDTO.User.NewConnectionDTO -> {
                val connection = content.connection.toEntity()
                val peer = content.connection.qualifiedToId.toModel()
                val id = metadataSystemMessageId(eventId, index)
                val profiles = users.connectionPeer(peer)
                val lifecycle = when (connection.status) {
                    com.wire.kalium.persistence.dao.ConnectionEntity.State.PENDING -> {
                        conversations.pendingConnection(connection)
                        emptyList()
                    }
                    com.wire.kalium.persistence.dao.ConnectionEntity.State.SENT ->
                        conversations.sentConnection(connection.qualifiedConversationId.toModel(), mlsContext)
                    com.wire.kalium.persistence.dao.ConnectionEntity.State.ACCEPTED -> {
                        conversations.acceptedConnection(connection)
                        oneOnOnes.resolvePeer(peer, id, mlsContext)
                    }
                    else -> emptyList()
                }
                listOf(ReceivedPayload.Connection(connection, profiles, "$id:accepted")) + lifecycle
            }
            is EventContentDTO.User.SessionRefreshSuggestedDTO -> {
                checkNotNull(sessionRefresh) { "Session refresh requires owned authenticated transport" }
                    .handle(Event.User.SessionRefreshSuggested(eventId)).orThrow()
                emptyList()
            }
            is EventContentDTO.User.ClientRemoveDTO -> {
                check(content.client.clientId != clientId) { "Current-client removal requires account invalidation" }
                emptyList()
            }
            is EventContentDTO.User.UserDeleteDTO -> {
                val id = content.userId.toModel()
                check(id != selfUserId) { "Self-account deletion requires account invalidation" }
                users.markDeleted(id)
                listOf(ReceivedPayload.UserDeleted(id))
            }
            is EventContentDTO.Team.MemberLeave -> {
                val event = Event.Team.MemberLeave(
                    metadataSystemMessageId(eventId, index),
                    content.teamId,
                    content.teamMember.nonQualifiedUserId,
                    content.time
                )
                users.markDeleted(UserId(event.memberId, selfUserId.domain))
                listOf(ReceivedPayload.TeamLeave(event))
            }
            is EventContentDTO.Federation.FederationDeleteDTO -> listOf(
                ReceivedPayload.Federation(Event.Federation.Delete(metadataSystemMessageId(eventId, index), content.domain))
            )
            is EventContentDTO.Federation.FederationConnectionRemovedDTO -> listOf(
                ReceivedPayload.Federation(Event.Federation.ConnectionRemoved(metadataSystemMessageId(eventId, index), content.domains))
            )
            is EventContentDTO.Meeting.MeetingCreateDTO -> fetchReceivedMeeting(content.qualifiedMeetingId, meetingApi, users)
            is EventContentDTO.Meeting.MeetingUpdateDTO -> fetchReceivedMeeting(content.qualifiedMeetingId, meetingApi, users)
            is EventContentDTO.Meeting.MeetingMemberAddDTO -> fetchReceivedMeeting(content.qualifiedMeetingId, meetingApi, users)
            is EventContentDTO.Meeting.MeetingDeleteDTO -> listOf(ReceivedPayload.MeetingDelete(content.qualifiedMeetingId.toModel()))
            is EventContentDTO.UserProperty.PropertiesSetDTO -> listOfNotNull(
                (updateUserProperties(selfUserId, eventId, content) as? Event.UserProperty)?.let { ReceivedPayload.UserProperty(it) }
            )
            is EventContentDTO.UserProperty.PropertiesDeleteDTO -> listOfNotNull(
                (deleteUserProperties(eventId, content) as? Event.UserProperty)?.let { ReceivedPayload.UserProperty(it) }
            )
            is EventContentDTO.FeatureConfig.FeatureConfigUpdatedDTO -> {
                if (content.data is com.wire.kalium.network.api.authenticated.featureConfigs.FeatureConfigData.Legalhold) {
                    return@flatMapIndexed emptyList()
                }
                val event = featureConfig(FeatureConfigMapperImpl(), eventId, content)
                if (event is Event.FeatureConfig.UnknownFeatureUpdated) return@flatMapIndexed emptyList()
                listOf(
                    when (event) {
                        is Event.FeatureConfig.MLSUpdated ->
                            configuration.handle(event, metadataSystemMessageId(eventId, index), mlsContext)
                        is Event.FeatureConfig.MLSMigrationUpdated ->
                            configuration.handle(event, metadataSystemMessageId(eventId, index), mlsContext)
                        else -> ReceivedPayload.Configuration(event)
                    }
                )
            }
            else -> {
                diagnostics?.unsupportedEvent()
                error("Notification contains unsupported required work")
            }
        }
        onDecoded(decoded)
        decoded
    }

    private companion object {
        const val PAGE_SIZE = 100
        private const val DEFERRED_RETRY_MILLIS = 30_000L
    }
}
