/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.common.error

import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.api.model.MLSErrorResponse
import com.wire.kalium.network.exceptions.AdminlessConversationError
import com.wire.kalium.network.exceptions.KaliumException

/** Normalize a transport exception before it crosses a public failure boundary. */
fun serverMiscommunication(cause: Throwable): NetworkFailure.ServerMiscommunication {
    val exception = cause as? KaliumException ?: KaliumException.GenericError(cause)
    val reason = when (exception) {
        is KaliumException.InvalidRequestError -> ServerError.InvalidRequest(
            exception.errorResponse.toServerErrorResponse(),
            exception.retryAfterSeconds
        )
        is KaliumException.RedirectError -> ServerError.Redirect(exception.errorResponse.toServerErrorResponse())
        is KaliumException.ServerError -> ServerError.Server(exception.errorResponse.toServerErrorResponse())
        is KaliumException.Unauthorized -> ServerError.Unauthorized(exception.errorCode)
        is AdminlessConversationError -> with(exception.errorResponse) {
            ServerError.AdminlessConversation(
                ServerError.Response(code, message, label),
                eligibleMembers.map { UserId(it.value, it.domain) }
            )
        }
        else -> ServerError.Other(exception.message)
    }
    return NetworkFailure.ServerMiscommunication(reason, exception)
}

private fun GenericAPIErrorResponse.toServerErrorResponse(): ServerError.Response = ServerError.Response(code, message, label)

/** Keep a stable crypto classification while retaining the original cause for diagnostics. */
// Keep the mapping exhaustive so new native codes require an explicit contract decision.
@Suppress("CyclomaticComplexMethod")
fun proteusFailure(exception: ProteusException): ProteusFailure = ProteusFailure(
    code = when (exception.code) {
        ProteusException.Code.SESSION_NOT_FOUND -> ProteusFailure.Code.SESSION_NOT_FOUND
        ProteusException.Code.REMOTE_IDENTITY_CHANGED -> ProteusFailure.Code.REMOTE_IDENTITY_CHANGED
        ProteusException.Code.INVALID_SIGNATURE -> ProteusFailure.Code.INVALID_SIGNATURE
        ProteusException.Code.INVALID_MESSAGE -> ProteusFailure.Code.INVALID_MESSAGE
        ProteusException.Code.DUPLICATE_MESSAGE -> ProteusFailure.Code.DUPLICATE_MESSAGE
        ProteusException.Code.TOO_DISTANT_FUTURE -> ProteusFailure.Code.TOO_DISTANT_FUTURE
        ProteusException.Code.OUTDATED_MESSAGE -> ProteusFailure.Code.OUTDATED_MESSAGE
        ProteusException.Code.DECODE_ERROR -> ProteusFailure.Code.DECODE_ERROR
        ProteusException.Code.STORAGE_ERROR -> ProteusFailure.Code.STORAGE_ERROR
        ProteusException.Code.IDENTITY_ERROR -> ProteusFailure.Code.IDENTITY_ERROR
        ProteusException.Code.PREKEY_NOT_FOUND -> ProteusFailure.Code.PREKEY_NOT_FOUND
        ProteusException.Code.PANIC -> ProteusFailure.Code.PANIC
        ProteusException.Code.UNKNOWN_ERROR -> ProteusFailure.Code.UNKNOWN_ERROR
        ProteusException.Code.LOCAL_FILES_NOT_FOUND -> ProteusFailure.Code.LOCAL_FILES_NOT_FOUND
    },
    intCode = exception.intCode,
    rootCause = exception
)

// Preserve the wire labels explicitly without adding a transport dependency to the contract.
@Suppress("CyclomaticComplexMethod")
internal fun MLSErrorResponse.toMlsErrorDetails(): MlsErrorDetails = MlsErrorDetails(
    label = when (this) {
        is MLSErrorResponse.SelfRemovalNotAllowed -> "mls-self-removal-not-allowed"
        is MLSErrorResponse.ProtocolError -> "mls-protocol-error"
        is MLSErrorResponse.NotEnabled -> "mls-not-enabled"
        is MLSErrorResponse.InvalidLeafNodeIndex -> "mls-invalid-leaf-node-index"
        is MLSErrorResponse.InvalidLeafNodeSignature -> "mls-invalid-leaf-node-signature"
        is MLSErrorResponse.GroupConversationMismatch -> "mls-group-conversation-mismatch"
        is MLSErrorResponse.CommitMissingReferences -> "mls-commit-missing-references"
        is MLSErrorResponse.ClientSenderUserMismatch -> "mls-client-sender-user-mismatch"
        is MLSErrorResponse.SubconversationJoinParentMissing -> "mls-subconv-join-parent-missing"
        is MLSErrorResponse.ProposalNotFound -> "mls-proposal-not-found"
        is MLSErrorResponse.StaleMessage -> "mls-stale-message"
        is MLSErrorResponse.ClientMismatch -> "mls-client-mismatch"
        is MLSErrorResponse.UnsupportedProposal -> "mls-unsupported-proposal"
        is MLSErrorResponse.UnsupportedMessage -> "mls-unsupported-message"
        is MLSErrorResponse.WelcomeMismatch -> "mls-welcome-mismatch"
        is MLSErrorResponse.MissingGroupInfo -> "mls-missing-group-info"
        is MLSErrorResponse.GroupOutOfSync -> "mls-group-out-of-sync"
    },
    message = message
)
