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

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.sending.outgoingSelfDeletionTimer
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationFilterEntity
import com.wire.kalium.persistence.dao.conversation.ConversationViewEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** Why an extension declines to send to a conversation. Each needs the app: the user sends from Wire. */
internal enum class ShareBlock {
    /** The app would not let the user write here: not a member, a removed or blocked user, or no conversation. */
    UNAVAILABLE,

    /** Legal hold changed and the user has not consented to sending under it. */
    LEGAL_HOLD,

    /** Verification degraded since the user last acknowledged it, and the app asks before sending. */
    VERIFICATION,

    /** This client has no established MLS group; joining or resetting it is app work. */
    MLS_GROUP,
}

/**
 * The extension's sending rule for [conversation], or null when it may send. Mirrors the app's interaction
 * availability, and declines what needs the user's consent or app-only work (ADR 12).
 */
internal suspend fun UserDatabaseBuilder.shareBlock(conversation: ConversationViewEntity): ShareBlock? =
    conversation.shareBlock() ?: when {
        legalHoldNeedsConsent(conversation) -> ShareBlock.LEGAL_HOLD
        unacknowledgedDegradation(conversation) -> ShareBlock.VERIFICATION
        else -> null
    }

/**
 * The app asks for consent when legal hold is enabled and the user was not told yet. A legal hold device among
 * the recipients of a conversation stored without legal hold means the app has not applied the change yet,
 * for example after an extension found the device while repairing recipients.
 */
private suspend fun UserDatabaseBuilder.legalHoldNeedsConsent(conversation: ConversationViewEntity): Boolean =
    when (conversation.legalHoldStatus) {
        ConversationEntity.LegalHoldStatus.ENABLED -> !conversationDAO.observeLegalHoldStatusChangeNotified(conversation.id).first()
        ConversationEntity.LegalHoldStatus.DISABLED -> clientDAO.conversationRecipient(conversation.id).values.flatten()
            .any { it.deviceType == DeviceTypeEntity.LegalHold }
        ConversationEntity.LegalHoldStatus.DEGRADED -> true
    }

/**
 * Stored verification statuses alone do not decide: Proteus conversations with unverified clients are stored
 * as degraded. The app asks only when verification degraded and the user has not acknowledged it yet.
 */
internal suspend fun UserDatabaseBuilder.unacknowledgedDegradation(conversation: ConversationViewEntity): Boolean =
    conversation.proteusVerificationStatus == ConversationEntity.VerificationStatus.DEGRADED &&
        !conversationDAO.observeDegradedConversationNotified(conversation.id).first() ||
        conversation.mlsVerificationStatus == ConversationEntity.VerificationStatus.DEGRADED &&
        !conversationMetaDataDAO.isInformedAboutDegradedMLSVerification(conversation.id)

private fun ConversationViewEntity.shareBlock(): ShareBlock? = when {
    type.isGroup -> if (isMember) null else ShareBlock.UNAVAILABLE
    type == ConversationEntity.Type.ONE_ON_ONE ->
        if (userDeleted == true || userDefederated == true || connectionStatus == ConnectionEntity.State.BLOCKED) ShareBlock.UNAVAILABLE
        else null
    else -> ShareBlock.UNAVAILABLE
} ?: when {
    legalHoldStatus == ConversationEntity.LegalHoldStatus.DEGRADED -> ShareBlock.LEGAL_HOLD
    protocolInfo.isUnestablishedMLS() -> ShareBlock.MLS_GROUP
    else -> null
}

private fun ConversationEntity.ProtocolInfo.isUnestablishedMLS() =
    this is ConversationEntity.ProtocolInfo.MLS && groupState != ConversationEntity.GroupState.ESTABLISHED

/** The self-deletion timer the app would give a message here; cells conversations have none. */
internal fun ConversationViewEntity.shareSelfDeletionTimer(teamTimer: TeamSelfDeleteTimer?): SelfDeletionTimer =
    if (wireCell != null) SelfDeletionTimer.Disabled
    else outgoingSelfDeletionTimer(
        teamTimer = teamTimer,
        groupTimer = messageTimer?.milliseconds,
        userTimer = userMessageTimer?.milliseconds,
        considerSelfUserSettings = true,
    )

/** A shared text as the app would compose it: pending, from this client, with the conversation's timer. */
@Suppress("LongParameterList")
internal fun sharedTextMessage(
    id: String,
    text: String,
    conversationId: ConversationId,
    selfUserId: UserId,
    selfClientId: ClientId,
    now: Instant,
    timer: SelfDeletionTimer,
    expectsReadConfirmation: Boolean,
) = Message.Regular(
    id = id,
    content = MessageContent.Text(value = text),
    expectsReadConfirmation = expectsReadConfirmation,
    conversationId = conversationId,
    date = now,
    senderUserId = selfUserId,
    senderClientId = selfClientId,
    status = Message.Status.Pending,
    editStatus = Message.EditStatus.NotEdited,
    expirationData = timer.duration?.let { Message.ExpirationData(it) },
    isSelfMessage = true,
)

/** Unarchived conversations an extension may send to, most recently active first. */
internal suspend fun UserDatabaseBuilder.shareableConversations(): List<ConversationViewEntity> =
    conversationDAO.getAllConversationDetails(fromArchive = false, filter = ConversationFilterEntity.ALL).first()
        .filter { shareBlock(it) == null }
        .sortedByDescending { it.lastModifiedDate }
