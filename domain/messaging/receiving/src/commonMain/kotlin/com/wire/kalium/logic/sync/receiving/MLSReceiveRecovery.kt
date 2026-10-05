/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import kotlin.io.encoding.Base64

/** Validated recovery output; callers persist canonical metadata and warning effects before acknowledgement. */
data class MLSReceiveRecoveryResult(
    val response: ConversationResponse,
    val operation: PendingMLSReceiveRecovery,
    val epoch: ULong,
) {
    val warningId: String get() = "mls-recovery:${operation.id}"
}

/**
 * Shared by account owners that hold the receive lease. All native work, including the completion
 * checkpoint, uses the caller's transaction. The join callback must explicitly join by external
 * commit and verify the returned group identity; a generic already-joined success is insufficient.
 * Completion covers this recovery operation only. The caller must separately preserve replay of
 * earlier successful MLS payloads when a pending join replaces an already caught-up group.
 */
class MLSReceiveRecovery(
    private val store: MLSReceiveRecoveryStore,
    private val conversationApi: ConversationApi,
    private val selfUserId: UserId,
    private val join: suspend (MlsCoreCryptoContext, String, ByteArray) -> ULong,
) {
    /** Check before decrypting; only exact MLS payloads with committed effects can be bypassed. */
    suspend fun wasProcessed(context: MlsCoreCryptoContext, event: Event.Conversation.NewMLSMessage): Boolean =
        store.wasProcessed(context, event)

    /** Call after every payload effect has finished, before the enclosing native transaction commits. */
    suspend fun recordProcessed(
        context: MlsCoreCryptoContext,
        events: List<Event.Conversation.NewMLSMessage>,
        payloads: List<ReceivedPayloadIdentity> = emptyList(),
    ) = store.recordProcessed(context, events, payloads)

    suspend fun wereEffectsProcessed(context: MlsCoreCryptoContext, payload: ReceivedPayloadIdentity): Boolean =
        store.wereEffectsProcessed(context, payload)

    /** Ordinary receives only consult the owner's cached operation index; ciphertext decoding is lazy. */
    suspend fun resume(context: MlsCoreCryptoContext, event: Event.Conversation.NewMLSMessage): MLSReceiveRecoveryResult? {
        val operation = store.find(event.id) { Base64.decode(event.content) } ?: return null
        return resume(context, event, operation)
    }

    private suspend fun resume(
        context: MlsCoreCryptoContext,
        event: Event.Conversation.NewMLSMessage,
        operation: PendingMLSReceiveRecovery,
    ): MLSReceiveRecoveryResult {
        check(event.subconversationId == null) { "Subconversation recovery is not supported" }
        val completed = store.isCompleted(context, operation)
        val response = fetchValidated(event, operation.groupId)
        return if (completed) {
            MLSReceiveRecoveryResult(response, operation, context.conversationEpoch(operation.groupId))
        } else {
            // An outstanding intent requires an explicit join even if the group now looks current.
            rejoin(context, response, operation)
        }
    }

    /** Called only after WrongEpoch; current native groups keep the caller's existing no-rejoin policy. */
    @Suppress("ReturnCount") // Pending, current, and completed operations each bypass a new join.
    suspend fun recover(
        context: MlsCoreCryptoContext,
        event: Event.Conversation.NewMLSMessage,
        expectedGroupId: String,
    ): MLSReceiveRecoveryResult? {
        store.find(event.id) { Base64.decode(event.content) }?.let { operation ->
            check(operation.groupId == expectedGroupId) { "Pending recovery belongs to another MLS group" }
            return resume(context, event, operation)
        }
        check(event.subconversationId == null) { "Subconversation recovery is not supported" }
        val response = fetchValidated(event, expectedGroupId)
        val remoteEpoch = checkNotNull(response.epoch) { "MLS recovery requires a remote epoch" }
        if (context.conversationExists(expectedGroupId) && context.conversationEpoch(expectedGroupId) >= remoteEpoch) return null

        val operation = store.begin(event.id, expectedGroupId, Base64.decode(event.content))
        // This also validates the native checkpoint format before a new remote mutation.
        if (store.isCompleted(context, operation)) {
            return MLSReceiveRecoveryResult(response, operation, context.conversationEpoch(expectedGroupId))
        }
        return rejoin(context, response, operation)
    }

    /** Must run before deleting canonical metadata; this does not mutate native state. */
    suspend fun prepareDeletion(
        payload: ReceivedPayloadIdentity,
        groupId: String?,
    ): Boolean = store.prepareDeletion(payload, groupId)

    /** Replays an exact pending deletion when SQL has committed but native state may not have. */
    suspend fun resumeDeletion(context: MlsCoreCryptoContext, payload: ReceivedPayloadIdentity): Boolean =
        store.resumeDeletion(context, payload)

    suspend fun acknowledge(eventIds: List<String>) = store.acknowledge(eventIds)

    private suspend fun fetchValidated(event: Event.Conversation.NewMLSMessage, groupId: String): ConversationResponse {
        val response = wrapApiRequest { conversationApi.fetchConversationDetails(event.conversationId.toApi()) }.orThrow()
        check(response.id == event.conversationId.toApi()) { "Recovery response belongs to another conversation" }
        check(response.protocol == ConvProtocol.MLS || response.protocol == ConvProtocol.MIXED) { "Recovery requires MLS" }
        check(!response.groupId.isNullOrBlank() && response.groupId == groupId) { "Recovery response changed the MLS group" }
        check(response.members.self?.id == selfUserId.toApi()) { "Recovery requires current self membership" }
        return response
    }

    private suspend fun rejoin(
        context: MlsCoreCryptoContext,
        response: ConversationResponse,
        operation: PendingMLSReceiveRecovery,
    ): MLSReceiveRecoveryResult {
        val groupInfo = wrapApiRequest { conversationApi.fetchGroupInfo(response.id) }.orThrow()
        check(groupInfo.isNotEmpty()) { "MLS recovery requires group info" }
        val joinedEpoch = join(context, operation.groupId, groupInfo)
        check(context.conversationExists(operation.groupId)) { "External join did not establish the MLS group" }
        val epoch = context.conversationEpoch(operation.groupId)
        check(epoch > 0uL && epoch == joinedEpoch) { "External join did not return the current native epoch" }
        store.complete(context, operation)
        return MLSReceiveRecoveryResult(response, operation, epoch)
    }
}
