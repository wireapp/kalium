/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.notification

import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent

/** Null means no supported preview, not permission to suppress or present an alert. */
fun classifyNotificationMessage(type: MessageEntity.ContentType, assetMimeType: String? = null): NotificationMessageKind? =
    when (type) {
        MessageEntity.ContentType.TEXT, MessageEntity.ContentType.POLL, MessageEntity.ContentType.MULTIPART ->
            NotificationMessageKind.TEXT
        MessageEntity.ContentType.ASSET ->
            if (assetMimeType?.contains("image/") == true) NotificationMessageKind.PICTURE else NotificationMessageKind.FILE
        MessageEntity.ContentType.KNOCK -> NotificationMessageKind.PING
        MessageEntity.ContentType.LOCATION -> NotificationMessageKind.LOCATION
        MessageEntity.ContentType.MISSED_CALL -> NotificationMessageKind.MISSED_CALL
        else -> null
    }

/** Adapts canonical receiving rows to the same classification used by the foreground query. */
fun classifyNotificationMessage(content: MessageEntityContent.Regular): NotificationMessageKind? {
    val type = when (content) {
        is MessageEntityContent.Text -> MessageEntity.ContentType.TEXT
        is MessageEntityContent.Poll -> MessageEntity.ContentType.POLL
        is MessageEntityContent.Multipart -> MessageEntity.ContentType.MULTIPART
        is MessageEntityContent.Asset -> MessageEntity.ContentType.ASSET
        is MessageEntityContent.Knock -> MessageEntity.ContentType.KNOCK
        is MessageEntityContent.Location -> MessageEntity.ContentType.LOCATION
        else -> return null
    }
    return classifyNotificationMessage(type, (content as? MessageEntityContent.Asset)?.assetMimeType)
}
