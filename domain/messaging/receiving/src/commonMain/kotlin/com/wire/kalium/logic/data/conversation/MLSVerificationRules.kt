/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.CredentialType
import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.E2EIConversationState
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.data.conversation.Conversation.VerificationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import kotlin.time.Instant

fun E2EIConversationState.toModel(): VerificationStatus = when (this) {
    E2EIConversationState.VERIFIED -> VerificationStatus.VERIFIED
    E2EIConversationState.NOT_VERIFIED, E2EIConversationState.NOT_ENABLED -> VerificationStatus.NOT_VERIFIED
}

/** A valid certificate must also identify the profile the user currently sees. */
fun areMLSIdentitiesVerified(identities: List<WireIdentity>, name: String?, handle: String?): Boolean = identities.none {
    it.status != CryptoCertificateStatus.VALID || it.credentialType != CredentialType.X509 ||
        it.x509Identity == null || it.x509Identity?.displayName != name || it.x509Identity?.handle?.handle != handle
}

/** Losing verification stays degraded until the conversation is verified again. */
fun actualMLSVerificationStatus(newStatusFromCC: VerificationStatus, currentStatus: VerificationStatus): VerificationStatus =
    if (newStatusFromCC == VerificationStatus.NOT_VERIFIED && currentStatus != VerificationStatus.NOT_VERIFIED)
        VerificationStatus.DEGRADED
    else newStatusFromCC

fun mlsVerificationMessage(id: String, conversationId: ConversationId, selfUserId: UserId, date: Instant, status: VerificationStatus) =
    Message.System(
        id = id,
        content = if (status == VerificationStatus.VERIFIED)
            MessageContent.ConversationVerifiedMLS
        else MessageContent.ConversationDegradedMLS,
        conversationId = conversationId,
        date = date,
        senderUserId = selfUserId,
        status = Message.Status.Sent,
        visibility = Message.Visibility.VISIBLE,
        expirationData = null,
    )

/** The device no longer has the epoch needed to receive; app recovery owns rejoining. */
fun mlsEpochWarningMessage(id: String, conversationId: ConversationId, selfUserId: UserId, date: Instant) = Message.System(
    id,
    MessageContent.MLSWrongEpochWarning,
    conversationId,
    date,
    selfUserId,
    Message.Status.Read(0),
    Message.Visibility.VISIBLE,
    expirationData = null,
)
