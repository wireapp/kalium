/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.commonizeMLSException
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.tools.KtxSerializer
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

/** Validated rejoin output; callers persist canonical metadata and the lost-epoch warning before acknowledgement. */
data class MLSReceiveRecoveryResult(
    val response: ConversationResponse,
    val groupId: String,
    val epoch: ULong,
    val warningId: String,
)

/**
 * Rejoins, by external commit in the caller's transaction, an MLS group that CoreCrypto lacks or is behind on.
 * Nothing is stored: the native transaction, SQL and the queue acknowledgement commit separately, and every
 * kill between them is safe to replay. Until the MLS commit lands, CoreCrypto is still missing or behind, so
 * the replay joins again; openmls removes our previous leaf in that second external commit. Once it lands,
 * CoreCrypto is current, no second commit is sent, and the trigger is skipped as a past-epoch message.
 * The join callback must explicitly join by external commit; a generic already-joined success is insufficient.
 */
class MLSReceiveRecovery(
    private val conversationApi: ConversationApi,
    private val selfUserId: UserId,
    private val join: suspend (MlsCoreCryptoContext, String, ByteArray) -> ULong,
) {
    suspend fun recover(
        context: MlsCoreCryptoContext,
        event: Event.Conversation.NewMLSMessage,
        expectedGroupId: String,
    ): MLSReceiveRecoveryResult? = skippingUnrecoverable(context, expectedGroupId) {
        rejoinIfBehind(context, event, expectedGroupId)
    }

    private suspend fun rejoinIfBehind(
        context: MlsCoreCryptoContext,
        event: Event.Conversation.NewMLSMessage,
        expectedGroupId: String,
    ): MLSReceiveRecoveryResult? {
        check(event.subconversationId == null) { "Subconversation recovery is not supported" }
        val details = fetchDetails(event)
        // A conversation this account can no longer read keeps the no-rejoin policy.
        if (details is Either.Left && details.value.isConversationUnavailable()) return null
        val response = details.validated(event, expectedGroupId)
        val remoteEpoch = checkNotNull(response.epoch) { "MLS recovery requires a remote epoch" }
        val current = context.conversationExists(expectedGroupId) && context.conversationEpoch(expectedGroupId) >= remoteEpoch
        return if (current) null else MLSReceiveRecoveryResult(
            response = response,
            groupId = expectedGroupId,
            epoch = rejoin(context, response, expectedGroupId),
            warningId = warningId(event, expectedGroupId),
        )
    }

    @Suppress("TooGenericExceptionCaught") // Any failure but cancellation or the network is final for this event.
    private suspend fun skippingUnrecoverable(
        context: MlsCoreCryptoContext,
        groupId: String,
        recovery: suspend () -> MLSReceiveRecoveryResult?,
    ): MLSReceiveRecoveryResult? = try {
        recovery()
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: Exception) {
        val cause = failure.coreFailure()
        if (cause.isTransientNetworkFailure() || context.conversationExists(groupId)) throw failure
        kaliumLogger.w("Skipping an MLS message whose missing group failed to rejoin: ${cause::class.simpleName}")
        null
    }

    private fun Exception.coreFailure(): CoreFailure =
        (this as? KaliumSyncException)?.coreFailureCause ?: commonizeMLSException(this).failure

    private suspend fun fetchDetails(event: Event.Conversation.NewMLSMessage) =
        wrapApiRequest { conversationApi.fetchConversationDetails(event.conversationId.toApi()) }

    private fun Either<NetworkFailure, ConversationResponse>.validated(
        event: Event.Conversation.NewMLSMessage,
        groupId: String,
    ): ConversationResponse {
        val response = orThrow()
        check(response.id == event.conversationId.toApi()) { "Recovery response belongs to another conversation" }
        check(response.protocol == ConvProtocol.MLS || response.protocol == ConvProtocol.MIXED) { "Recovery requires MLS" }
        check(!response.groupId.isNullOrBlank() && response.groupId == groupId) { "Recovery response changed the MLS group" }
        check(response.members.self?.id == selfUserId.toApi()) { "Recovery requires current self membership" }
        return response
    }

    /** Returns the joined epoch. */
    @Suppress("TooGenericExceptionCaught") // Both join callbacks throw untyped exceptions; only a stale commit is retried.
    private suspend fun rejoin(context: MlsCoreCryptoContext, response: ConversationResponse, groupId: String): ULong {
        val joinedEpoch = try {
            joinCurrentGroupInfo(context, response, groupId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            // Another commit moved the group between reading its group info and our POST. Join the newer one, once.
            if (failure.coreFailure() != STALE_COMMIT) throw failure
            joinCurrentGroupInfo(context, response, groupId)
        }
        check(context.conversationExists(groupId)) { "External join did not establish the MLS group" }
        val epoch = context.conversationEpoch(groupId)
        check(epoch > 0uL && epoch == joinedEpoch) { "External join did not return the current native epoch" }
        return epoch
    }

    private suspend fun joinCurrentGroupInfo(context: MlsCoreCryptoContext, response: ConversationResponse, groupId: String): ULong {
        val groupInfo = wrapApiRequest { conversationApi.fetchGroupInfo(response.id) }.orThrow()
        check(groupInfo.isNotEmpty()) { "MLS recovery requires group info" }
        return join(context, groupId, groupInfo)
    }

    /** Identifies the trigger as the former recovery store did, so a replay across versions adds no second warning. */
    private fun warningId(event: Event.Conversation.NewMLSMessage, groupId: String): String {
        val trigger = WarningTrigger(event.id, groupId, Base64.decode(event.content).toByteString().sha256().hex())
        return "mls-recovery:" + KtxSerializer.json.encodeToString(trigger).encodeUtf8().sha256().hex()
    }

    @Serializable
    private data class WarningTrigger(
        @SerialName("eventId") val eventId: String,
        @SerialName("groupId") val groupId: String,
        @SerialName("ciphertextHash") val ciphertextHash: String,
    )

    private companion object {
        val STALE_COMMIT = MLSFailure.MessageRejected(NetworkFailure.MlsMessageRejectedFailure.StaleMessage)
    }
}
