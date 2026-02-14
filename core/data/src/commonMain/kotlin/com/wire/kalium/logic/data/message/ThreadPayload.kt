/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.message

/**
 * Allowed standalone message payload types that can carry thread metadata.
 */
public sealed interface ThreadPayload {
    public data class Text(val value: MessageContent.Text) : ThreadPayload
    public data class Asset(val value: MessageContent.Asset) : ThreadPayload
    public data class Multipart(val value: MessageContent.Multipart) : ThreadPayload
    public data class Composite(val value: MessageContent.Composite) : ThreadPayload
}

public fun MessageContent.Regular.toThreadPayloadOrNull(): ThreadPayload? = when (this) {
    is MessageContent.Text -> ThreadPayload.Text(this)
    is MessageContent.Asset -> ThreadPayload.Asset(this)
    is MessageContent.Multipart -> ThreadPayload.Multipart(this)
    is MessageContent.Composite -> ThreadPayload.Composite(this)
    is MessageContent.Knock,
    is MessageContent.Location,
    is MessageContent.RestrictedAsset,
    is MessageContent.FailedDecryption,
    is MessageContent.Unknown -> null
}

public fun ThreadPayload.toRegularMessageContent(): MessageContent.Regular = when (this) {
    is ThreadPayload.Text -> value
    is ThreadPayload.Asset -> value
    is ThreadPayload.Multipart -> value
    is ThreadPayload.Composite -> value
}
