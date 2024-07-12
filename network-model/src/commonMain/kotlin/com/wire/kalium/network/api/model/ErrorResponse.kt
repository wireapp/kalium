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

@Serializable
data class ErrorResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String,
    @SerialName("label") val label: String,
    @SerialName("data") val cause: Cause? = null,
) {
    fun isFederationError() = cause?.type == "federation" || label.contains("federation")
}

@Serializable
data class Cause(
    @SerialName("type") val type: String,
    @Deprecated("deprecated in favour for `domains`", replaceWith = ReplaceWith("domains"))
    @SerialName("domain") val domain: String,
    @SerialName("domains") val domains: List<String> = emptyList(),
    @SerialName("path") val path: String,
)

@Serializable
data class FederationConflictResponse(
    @SerialName("non_federating_backends") val nonFederatingBackends: List<String>
)

@Serializable
data class FederationUnreachableResponse(
    @SerialName("unreachable_backends") val unreachableBackends: List<String> = emptyList()
)
