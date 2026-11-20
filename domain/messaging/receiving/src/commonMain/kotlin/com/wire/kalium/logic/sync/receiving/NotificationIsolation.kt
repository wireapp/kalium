/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.normalizeFederatedBackendConflict
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.exceptions.isRetryableServerResponse

/** Only known, independent message work may cross a gap. Everything else is an ordering barrier. */
internal sealed interface NotificationDependency {
    data class Conversation(val id: QualifiedID) : NotificationDependency
    data class ProteusSession(val sender: QualifiedID, val client: String) : NotificationDependency
    data class MLSGroup(val id: String) : NotificationDependency
}

@Suppress("ReturnCount") // Unknown payloads stop dependency discovery immediately.
internal fun notificationDependencies(payload: List<EventContentDTO>): Set<NotificationDependency>? {
    if (payload.isEmpty()) return null
    val dependencies = mutableSetOf<NotificationDependency>()
    for (content in payload) {
        val conversation = when (content) {
            is EventContentDTO.Conversation.NewMessageDTO -> {
                dependencies += NotificationDependency.ProteusSession(content.qualifiedFrom, content.data.sender)
                content.qualifiedConversation
            }
            is EventContentDTO.Conversation.NewMLSMessageDTO -> content.qualifiedConversation
            is EventContentDTO.Conversation.NewMLSTargetedMessageDTO -> content.qualifiedConversation
            is EventContentDTO.Conversation.NewMLSTransientMessageDTO -> content.qualifiedConversation
            is EventContentDTO.Conversation.NewMLSTransientTargetedMessageDTO -> content.qualifiedConversation
            else -> return null
        }
        dependencies += NotificationDependency.Conversation(conversation)
    }
    return dependencies
}

/** Caught only outside the native transactions. Never wrap SQL writes or native commit failures. */
internal class DeferredNotification(val failure: KaliumSyncException) : Exception("Notification dependency deferred", failure)
internal class NotificationOrderingBarrier : Exception("Notification requires the complete event prefix")

internal fun CoreFailure.canDeferNotification(): Boolean = isTransientNetworkFailure() || when (this) {
    MLSFailure.WrongEpoch, MLSFailure.UnmergedPendingGroup, MLSFailure.CommitForMissingProposal,
    MLSFailure.ConversationNotFound, MLSFailure.OrphanWelcome -> true
    else -> false
}

/** No connection, a federated backend failure, a server error or rate limiting: the same request may succeed later. */
// A commit keeps a conflicting-backends rejection as an MLS failure; it is the same retryable federation failure.
internal fun CoreFailure.isTransientNetworkFailure(): Boolean = when (val failure = normalizeFederatedBackendConflict()) {
    is NetworkFailure.NoNetworkConnection, MLSFailure.TransportUnavailable, is NetworkFailure.FederatedBackendFailure -> true
    is NetworkFailure.ServerMiscommunication -> failure.kaliumException.isRetryableServerResponse()
    else -> false
}

/** Decrypted self messages can affect other conversations; the outer conversation ID is insufficient. */
internal fun ReceivedPayload.canCrossNotificationGap(): Boolean = when (this) {
    is ReceivedPayload.Application -> when (message.content.messageContent) {
        is MessageContent.Text, is MessageContent.Knock, is MessageContent.Location,
        is MessageContent.Asset, is MessageContent.Composite, is MessageContent.Poll, is MessageContent.Multipart,
        is MessageContent.Ignored, is MessageContent.Unknown -> true
        else -> false
    }
    is ReceivedPayload.Creation, is ReceivedPayload.System, is ReceivedPayload.Profiles,
    is ReceivedPayload.DecryptionFailure, is ReceivedPayload.Proposal, is ReceivedPayload.ClearProposalTimer,
    is ReceivedPayload.MLSEstablished -> true
    else -> false
}
