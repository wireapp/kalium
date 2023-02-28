/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.network.api.base.authenticated.notification.conversation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class MessageEventData(
    @SerialName("text") val text: String,
    @SerialName("sender") val sender: String,
    @SerialName("recipient") val recipient: String,
    @SerialName("data") val encryptedExternalData: String? = null
)

@Serializable
data class UnEncryptedMessageEventData(
    @SerialName("content") val text: String,
//     @SerialName("quote") val quote: QuoteDTO?,
//     @SerialName("mentions") val mentions: List<String>?,
    @SerialName("expects_read_confirmation") val expectsReadConfirmation: Boolean,
    @SerialName("legal_hold_status") val legalHoldStatus: Int?
    )

@Serializable
data class QuoteDTO(
    @SerialName("message_id") val messageId: String?,
    @SerialName("user_id") val userId: String?,
)

@Serializable
data class UnEncryptedAssetEventData(
    @SerialName("content_length") val contentLength: Int?,
    @SerialName("content_type") val contentType: String?,
    val domain: String?,
    @SerialName("expects_read_confirmation") val expectsReadConfirmation: Boolean,
    val info: AssetInfo?,
    val key: String?,
    @SerialName("legal_hold_status") val legalHoldStatus: Int,
    @SerialName("otr_key") val otrKey: JsonObject?,
    @SerialName("sha256") val sha256: JsonObject?,
    val status: String?,
    val token: String?
)

@Serializable
data class UnEncryptedKnockEventData(
    @SerialName("expects_read_confirmation") val expectsReadConfirmation: Boolean,
    @SerialName("legal_hold_status") val legalHoldStatus: Int,
)

@Serializable
data class AssetInfo(
    val height: Int?,
    val name: String?,
    val tag: String?,
    val width: Int?
)
