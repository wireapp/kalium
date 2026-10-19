/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.share

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.map
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.toDao
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.RegularMessageMapper
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.message.withGroupReceiptMode
import com.wire.kalium.logic.data.message.SessionEstablisherImpl
import com.wire.kalium.logic.data.prekey.ProteusSessionCreator
import com.wire.kalium.logic.failure.LegalHoldEnabledForConversationFailure
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntime
import com.wire.kalium.logic.sync.receiving.AppleCryptoTransactions
import com.wire.kalium.messaging.sending.MLSMessageCreatorImpl
import com.wire.kalium.messaging.sending.MessageEnvelopeCreatorImpl
import com.wire.kalium.messaging.sending.MessageSendFailureHandlerImpl
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.messaging.sending.MessageSenderImpl
import com.wire.kalium.messaging.sending.MessageTransportImpl
import com.wire.kalium.messaging.sending.RemoteProteusRecipients
import com.wire.kalium.messaging.sending.SendingSession
import com.wire.kalium.messaging.sending.StoredOutgoingMessages
import com.wire.kalium.messaging.sending.StoredSendingConversations
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationViewEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One message of a share: one uploaded file, or the text. */
internal sealed interface ShareItem {
    data class Text(val text: String) : ShareItem
    data class File(val upload: UploadedFile) : ShareItem
}

/** How a share ended. Content-free: [reason] is a diagnostic code. */
internal data class ShareOutcome(val status: ShareStatus, val reason: String? = null, val messageId: String? = null)

internal enum class ShareStatus { SENT, FAILED, OFFLINE, OPEN_APP }

/** Sending goes on after a sent message until the deadline asks to stop. */
private fun ShareOutcome.sentInTime(shouldStop: () -> Boolean) = status == ShareStatus.SENT && !shouldStop()

/** Work only the app can do. The message stays failed, and the user retries it in Wire. */
internal class AppRequired(val reason: String) : CoreFailure.FeatureFailure()

/**
 * Sends shared text and uploaded files from an owned account through the app's sender (ADR 12), composed over the account's
 * stores. The caller holds the account live and processes stored events first: a stale MLS epoch or client
 * list fails the send, and processing events inside a send's crypto transaction would deadlock.
 */
@OptIn(ExperimentalUuidApi::class)
internal class ShareSending(
    private val runtime: AppleAccountRuntime,
    crypto: AppleCryptoTransactions,
    /** The app's build file restriction; the account's file rules are read again after catching up. */
    private val restriction: BuildFileRestrictionState,
    private val newId: () -> String = { Uuid.random().toString() },
    private val clock: Clock = Clock.System,
) {
    private val database = runtime.database
    private val selfUserId = runtime.prepared.userId
    private val messageMapper = RegularMessageMapper(selfUserId)
    private val failureHandler = MessageSendFailureHandlerImpl(
        recipients = RemoteProteusRecipients(database.clientDAO, runtime.network.clientApi),
        updateMessageStatus = { status, conversationId, messageId ->
            wrapStorageRequest { database.messageDAO.updateMessageStatus(status, messageId, conversationId.toDao()) }
        },
        // The app owns retries of failed messages.
        scheduleResend = {},
    )
    private val sender: MessageSender = MessageSenderImpl(
        messages = StoredOutgoingMessages(database.messageDAO, selfUserId, messageMapper),
        transport = MessageTransportImpl(runtime.network.messageApi, runtime.network.mlsMessageApi),
        conversations = StoredSendingConversations(database.conversationDAO, database.clientDAO),
        session = HeldSession(crypto),
        messageSendFailureHandler = failureHandler,
        sessionEstablisher = SessionEstablisherImpl(
            ProteusSessionCreator(runtime.network.preKeyApi, database.clientDAO)::establishSessions
        ),
        messageEnvelopeCreator = MessageEnvelopeCreatorImpl(::legalHoldStatus, selfUserId),
        mlsMessageCreator = MLSMessageCreatorImpl(
            legalHoldStatus = ::legalHoldStatus,
            // Committing pending proposals and joining a group are app work.
            commitPendingProposals = { _, groupId ->
                if (hasPendingProposals(groupId)) Either.Left(AppRequired("MLS_PENDING_PROPOSALS")) else Either.Right(Unit)
            },
            joinConversation = { _, _ -> Either.Left(AppRequired("MLS_JOIN")) },
            encrypt = { mls, groupId, content -> wrapMLSRequest { mls.encryptMessage(groupId.toCrypto(), content) } },
            selfUserId = selfUserId,
        ),
        prepareMessage = { Either.Right(it) },
        legalHoldEnabledAfter = { conversationId, _, repairClients ->
            val before = legalHoldClients(conversationId)
            repairClients().flatMap {
                when {
                    legalHoldClients(conversationId).any { it !in before } -> Either.Right(true)
                    // Repaired clients can degrade verification; retrying would skip the user's acknowledgement.
                    degradedSinceAcknowledged(conversationId) -> Either.Left(AppRequired(ShareBlock.VERIFICATION.name))
                    else -> Either.Right(false)
                }
            }
        },
        verifyEpoch = { _, _ -> Either.Left(AppRequired("MLS_STALE_EPOCH")) },
        handleGroupOutOfSync = { _, _, _, _ -> Either.Left(AppRequired("MLS_GROUP_OUT_OF_SYNC")) },
        broadcastRecipients = { Either.Left(AppRequired("BROADCAST")) },
        // The timer starts once the message is stored as sent; see send.
        enqueueSelfDeletion = { _, _ -> },
        scope = runtime.processingScope,
    )

    /**
     * Sends [items] in order, each as its own message. Once one is not sent, or [shouldStop] asks to end, the rest
     * are kept in Wire as failed, so the user can retry them there. Returns the first outcome other than sent.
     */
    /** The first message this share stored, if any: a share its deadline cut short still reports it. */
    var firstStoredId: String? = null
        private set

    suspend fun send(conversationId: ConversationId, items: List<ShareItem>, shouldStop: () -> Boolean): ShareOutcome {
        require(items.isNotEmpty())
        val conversation = database.conversationDAO.getConversationDetailsById(conversationId.toDao())
            ?: return ShareOutcome(ShareStatus.OPEN_APP, ShareBlock.UNAVAILABLE.name)
        val block = database.shareBlock(conversation)
            ?: ShareBlock.CELLS.takeIf { conversation.wireCell != null && items.any { it is ShareItem.File } }
        return block?.let { ShareOutcome(ShareStatus.OPEN_APP, it.name) }
            ?: fileRefusal(items)?.let { ShareOutcome(ShareStatus.FAILED, it.name) }
            ?: sendInOrder(composer(conversationId, conversation), items, shouldStop)
    }

    /** Catching up can apply a change to the account's file rules, such as file sharing turned off. */
    private suspend fun fileRefusal(items: List<ShareItem>): FileRefusal? {
        val files = items.filterIsInstance<ShareItem.File>().map { it.upload.file }
        if (files.isEmpty()) return null
        val policy = database.sharePolicy(selfUserId, restriction)
        return files.firstNotNullOfOrNull { policy.refusal(it.name, it.mimeType, it.sizeInBytes) }
    }

    private suspend fun sendInOrder(composer: Composer, items: List<ShareItem>, shouldStop: () -> Boolean): ShareOutcome {
        var outcome: ShareOutcome? = null
        var next = 0
        try {
            while (next < items.size && (outcome == null || outcome.sentInTime(shouldStop))) {
                // Storing is short; the deadline must not cut it between a message and its transfer status.
                val message = withContext(NonCancellable) { composer.store(items[next], Message.Status.Pending) }
                next++
                outcome = send(message)
            }
        } catch (cancelled: CancellationException) {
            // The deadline cut sending short: the rest stay in Wire as failed, so the user can retry them there.
            withContext(NonCancellable) { items.drop(next).forEach { composer.store(it, Message.Status.Failed) } }
            throw cancelled
        }
        val kept = withContext(NonCancellable) { items.drop(next).map { composer.store(it, Message.Status.Failed) } }
        val last = checkNotNull(outcome)
        return if (last.status == ShareStatus.SENT && kept.isNotEmpty()) ShareOutcome(ShareStatus.FAILED, OUT_OF_TIME, kept.first().id)
        else last
    }

    /** Reads the settings the app gives each message once, for all items. */
    private suspend fun composer(conversationId: ConversationId, conversation: ConversationViewEntity): Composer {
        val config = UserConfigDataSource(
            database.userPrefsDAO,
            database.userConfigDAO,
            lazy { BuildFileRestrictionState.NoRestriction }
        )
        return Composer(
            conversationId = conversationId,
            timer = conversation.shareSelfDeletionTimer(config.getTeamSettingsSelfDeletionStatus().getOrNull()?.enforcedSelfDeletionTimer),
            expectsReadConfirmation = config.isReadReceiptsEnabled().first().getOrNull() ?: false,
            // The app stores the group's receipt mode and sends the user's own choice.
            groupReceiptMode = database.messageDAO.getReceiptModeFromGroupConversationByQualifiedID(conversationId.toDao())?.toModel(),
        )
    }

    private inner class Composer(
        val conversationId: ConversationId,
        val timer: SelfDeletionTimer,
        val expectsReadConfirmation: Boolean,
        val groupReceiptMode: Conversation.ReceiptMode?,
    ) {
        /** Stores [item] as the app would; a file is already uploaded, and the app shows it from a local copy. */
        suspend fun store(item: ShareItem, status: Message.Status): Message.Regular {
            val message = sharedMessage(
                id = newId(),
                content = when (item) {
                    is ShareItem.Text -> MessageContent.Text(value = item.text)
                    is ShareItem.File -> item.upload.content()
                },
                conversationId = conversationId,
                selfUserId = selfUserId,
                selfClientId = ClientId(runtime.prepared.clientId),
                now = clock.now(),
                timer = timer,
                expectsReadConfirmation = expectsReadConfirmation,
                status = status,
            )
            if (item is ShareItem.File) item.upload.keepLocalCopy(database, runtime.assetDirectory)
            val stored = groupReceiptMode?.let { message.withGroupReceiptMode(it) } ?: message
            database.messageDAO.insertOrIgnoreMessage(messageMapper.toEntity(stored), updateConversationModifiedDate = true)
            // A failed message with an uploaded asset is resent as is when the user retries it in Wire.
            if (item is ShareItem.File) {
                database.messageDAO.updateAssetTransferStatus(AssetTransferStatus.UPLOADED.toDao(), message.id, conversationId.toDao())
            }
            if (firstStoredId == null) firstStoredId = message.id
            return message
        }
    }

    private suspend fun send(message: Message.Regular): ShareOutcome {
        val sent = try {
            sender.sendMessage(message)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { finish(message, FAILED_UNEXPECTEDLY) }
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            kaliumLogger.w("Share send failed: ${failure::class.simpleName}")
            Either.Left(CoreFailure.Unknown(failure))
        }
        return withContext(NonCancellable) {
            sent.fold({ failure -> finish(message, failure) }, { finish(message, null) })
        }
    }

    /**
     * Ends a stored message: failed unless the backend took it, so it is never left pending, which only the
     * app retries. A sent self-deleting message starts its timer, as the app does once sending succeeds.
     */
    private suspend fun finish(message: Message.Regular, failure: CoreFailure?): ShareOutcome {
        val sent = database.messageDAO.getMessageById(message.id, message.conversationId.toDao())?.status == MessageEntity.Status.SENT
        if (!sent) {
            val type = if (message.content is MessageContent.Asset) "Asset" else "Text"
            failureHandler.handleFailureAndUpdateMessageStatus(failure ?: FAILED_UNEXPECTEDLY, message.conversationId, message.id, type)
            val reason = failure ?: FAILED_UNEXPECTEDLY
            return ShareOutcome(reason.shareStatus(), reason.reason(), message.id)
        }
        message.expirationData?.let { expiration ->
            database.messageDAO.updateSelfDeletionEndDate(message.conversationId.toDao(), message.id, clock.now() + expiration.expireAfter)
        }
        return ShareOutcome(ShareStatus.SENT, messageId = message.id)
    }

    private suspend fun degradedSinceAcknowledged(conversationId: ConversationId): Boolean =
        database.conversationDAO.getConversationDetailsById(conversationId.toDao())
            ?.let { database.unacknowledgedDegradation(it) } == true

    private fun ConversationEntity.ReceiptMode.toModel() = when (this) {
        ConversationEntity.ReceiptMode.ENABLED -> Conversation.ReceiptMode.ENABLED
        ConversationEntity.ReceiptMode.DISABLED -> Conversation.ReceiptMode.DISABLED
    }

    private suspend fun legalHoldStatus(conversationId: ConversationId): Either<StorageFailure, Conversation.LegalHoldStatus> =
        wrapStorageRequest {
            database.conversationDAO.getConversationDetailsById(conversationId.toDao())?.legalHoldStatus
        }.map {
            when (it) {
                ConversationEntity.LegalHoldStatus.ENABLED -> Conversation.LegalHoldStatus.ENABLED
                ConversationEntity.LegalHoldStatus.DISABLED -> Conversation.LegalHoldStatus.DISABLED
                ConversationEntity.LegalHoldStatus.DEGRADED -> Conversation.LegalHoldStatus.DEGRADED
            }
        }

    /** Legal hold devices among the conversation's recipients; a new one means legal hold was enabled. */
    private suspend fun legalHoldClients(conversationId: ConversationId) =
        database.clientDAO.conversationRecipient(conversationId.toDao()).values.flatten()
            .filter { it.deviceType == DeviceTypeEntity.LegalHold }
            .map { it.userId to it.id }
            .toSet()

    private suspend fun hasPendingProposals(groupId: GroupID) =
        database.conversationDAO.getProposalTimers().first().any { it.groupID == groupId.value }

    private fun CoreFailure.shareStatus() = when (this) {
        is AppRequired, is LegalHoldEnabledForConversationFailure -> ShareStatus.OPEN_APP
        is NetworkFailure.NoNetworkConnection -> ShareStatus.OFFLINE
        else -> ShareStatus.FAILED
    }

    private fun CoreFailure.reason() = when (this) {
        is AppRequired -> reason
        is LegalHoldEnabledForConversationFailure -> ShareBlock.LEGAL_HOLD.name
        else -> this::class.simpleName
    }

    /** Sending runs only in a live hold: the account was processed before it, and the socket stores what arrives meanwhile. */
    private class HeldSession(private val crypto: AppleCryptoTransactions) : SendingSession {
        override suspend fun waitUntilLive() = Unit

        // Reached only after verifyEpoch, which declines; waiting inside the transaction would deadlock.
        override suspend fun waitUntilLiveOrFailure(): Either<CoreFailure, Unit> = Either.Right(Unit)

        override suspend fun <R> transaction(
            name: String,
            block: suspend (transactionContext: CryptoTransactionContext) -> Either<CoreFailure, R>
        ): Either<CoreFailure, R> = crypto.transaction(name, block)
    }

    private companion object {
        const val OUT_OF_TIME = "OUT_OF_TIME"
        val FAILED_UNEXPECTEDLY = AppRequired("UNEXPECTED")
    }
}
