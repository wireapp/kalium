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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface APIErrorResponseBody

@Deprecated("Use GenericErrorResponse instead")
typealias ErrorResponse = GenericAPIErrorResponse

@Serializable
data class GenericAPIErrorResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String,
    @SerialName("label") val label: String,
) : APIErrorResponseBody

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
        @SerialName("label") val label: String,
        @SerialName("data") val cause: Cause?,
    ) : FederationErrorResponse {
        companion object {
            const val FEDERATION_FAILURE = "federation-remote-error"
            const val FEDERATION_DENIED = "federation-denied"
            const val FEDERATION_NOT_ENABLED = "federation-not-enabled"
            const val FEDERATION_UNREACHABLE_DOMAINS = "federation-unreachable-domains-error"
        }

        fun isFederationFailure() = label == FEDERATION_FAILURE
        fun isFederationDenied() = label == FEDERATION_DENIED
        fun isFederationNotEnabled() = label == FEDERATION_NOT_ENABLED
        fun isFederationUnreachableDomains() = label == FEDERATION_UNREACHABLE_DOMAINS
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
