/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.tools.KtxSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/** Original queue identity and payload, kept in memory; only its fingerprint is persisted. */
data class ReceivedPayloadIdentity(val eventId: String, val content: EventContentDTO) {
    internal fun serialized(): String = if (content is EventContentDTO.Unknown) {
        // Unknown's type field collides with the sealed serializer discriminator.
        buildJsonObject { put("type", content.type) }.toString()
    } else KtxSerializer.json.encodeToJsonElement<EventContentDTO>(content).toString()
}
