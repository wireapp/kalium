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

package com.wire.kalium.network.api.model

import com.wire.kalium.network.api.model.GenericAPIErrorResponse.Companion.ERROR_DISCRIMINATOR_FIELD_NAME
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

sealed interface APIErrorResponseBody

@Serializable
data class GenericAPIErrorResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String,
    @SerialName("label") val label: String,
) : APIErrorResponseBody {
    companion object {
        const val ERROR_DISCRIMINATOR_FIELD_NAME = "label"
    }
}

@Serializable
sealed interface FederationErrorResponse : APIErrorResponseBody {
    @Serializable
    data class Conflict(
        @SerialName("non_federating_backends") val nonFederatingBackends: List<String>
    ) : FederationErrorResponse

    @Serializable
    data class Unreachable(
        @SerialName("unreachable_backends") val unreachableBackends: List<String> = emptyList()
    ) : FederationErrorResponse

    @Serializable
    data class Generic(
        @SerialName("code") val code: Int,
        @SerialName("message") val message: String,
        @SerialName(ERROR_DISCRIMINATOR_FIELD_NAME) val label: String,
        @SerialName("data") val cause: Cause?,
    ) : FederationErrorResponse {
        companion object {
            const val FEDERATION_DENIED = "federation-denied"
            const val FEDERATION_NOT_ENABLED = "federation-not-enabled"
        }

        fun isFederationDenied() = label == FEDERATION_DENIED
        fun isFederationNotEnabled() = label == FEDERATION_NOT_ENABLED
    }
}

@Serializable
data class Cause(
    @SerialName("type") val type: String,
    @Deprecated("deprecated in favour for `domains`", replaceWith = ReplaceWith("domains"))
    @SerialName("domain") val domain: String = "",
    @SerialName("domains") val domains: List<String> = emptyList(),
    @SerialName("path") val path: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator(ERROR_DISCRIMINATOR_FIELD_NAME)
sealed interface MLSErrorResponse : APIErrorResponseBody {
    @SerialName("message")
    val message: String

    @Serializable
    @SerialName("mls-self-removal-not-allowed")
    data class SelfRemovalNotAllowed(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-protocol-error")
    data class ProtocolError(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-not-enabled")
    data class NotEnabled(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-invalid-leaf-node-index")
    data class InvalidLeafNodeIndex(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-invalid-leaf-node-signature")
    data class InvalidLeafNodeSignature(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-group-conversation-mismatch")
    data class GroupConversationMismatch(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-commit-missing-references")
    data class CommitMissingReferences(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-client-sender-user-mismatch")
    data class ClientSenderUserMismatch(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-subconv-join-parent-missing")
    data class SubconversationJoinParentMissing(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-proposal-not-found")
    data class ProposalNotFound(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-stale-message")
    data class StaleMessage(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-client-mismatch")
    data class ClientMismatch(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-unsupported-proposal")
    data class UnsupportedProposal(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-unsupported-message")
    data class UnsupportedMessage(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-welcome-mismatch")
    data class WelcomeMismatch(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-missing-group-info")
    data class MissingGroupInfo(@SerialName("message") override val message: String) : MLSErrorResponse

    @Serializable
    @SerialName("mls-group-out-of-sync")
    data class GroupOutOfSync(
        @SerialName("missing_users") val missingUsers: List<UserId>,
        @SerialName("message") override val message: String,
    ) : MLSErrorResponse
}
