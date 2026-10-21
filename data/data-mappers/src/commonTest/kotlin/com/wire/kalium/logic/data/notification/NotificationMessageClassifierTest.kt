/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.notification

import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationMessageClassifierTest {
    @Test
    fun preservesForegroundTypesAndUnsupportedContent() {
        val expected = mapOf(
            ContentType.TEXT to NotificationMessageKind.TEXT,
            ContentType.POLL to NotificationMessageKind.TEXT,
            ContentType.MULTIPART to NotificationMessageKind.TEXT,
            ContentType.ASSET to NotificationMessageKind.FILE,
            ContentType.KNOCK to NotificationMessageKind.PING,
            ContentType.LOCATION to NotificationMessageKind.LOCATION,
            ContentType.MISSED_CALL to NotificationMessageKind.MISSED_CALL,
        )
        for (type in ContentType.entries) assertEquals(expected[type], classifyNotificationMessage(type), type.name)
    }

    @Test
    fun onlyImagesHaveDistinctAssetWording() {
        for (mime in listOf("image/jpeg", "image/png")) {
            assertEquals(NotificationMessageKind.PICTURE, classifyNotificationMessage(ContentType.ASSET, mime))
        }
        for (mime in listOf(null, "", "video/mp4", "audio/ogg", "application/pdf")) {
            assertEquals(NotificationMessageKind.FILE, classifyNotificationMessage(ContentType.ASSET, mime))
        }
    }

    @Test
    fun receivingRowsUseForegroundClassification() {
        val rows = listOf(
            MessageEntityContent.Text("hello") to ContentType.TEXT,
            MessageEntityContent.Poll("Question?", emptyList(), false) to ContentType.POLL,
            MessageEntityContent.Knock(false) to ContentType.KNOCK,
            MessageEntityContent.Location(1f, 2f) to ContentType.LOCATION,
        )
        for ((content, type) in rows) assertEquals(classifyNotificationMessage(type), classifyNotificationMessage(content))
        assertNull(classifyNotificationMessage(MessageEntityContent.Unknown()))
    }
}
