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

package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.message.mention.toDao
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlin.time.Instant

/** Shared content/status decision; callers retain their own notification and persistence boundaries. */
data class ReceivedMultipartEdit(
    val content: MessageContent.MultipartEdited,
    val instant: Instant,
    val markAsSent: Boolean,
    val notify: Boolean,
)

fun prepareMultipartEdit(current: Message, content: MessageContent.MultipartEdited, instant: Instant): ReceivedMultipartEdit {
    val editStatus = (current as? Message.Regular)?.editStatus
    val original = current.content
    val alreadyEdited = original is MessageContent.Multipart && editStatus is Message.EditStatus.Edited
    val keepLocalEdit = alreadyEdited && editStatus.lastEditInstant > instant
    return ReceivedMultipartEdit(
        content = if (keepLocalEdit) content.copy(
            newTextContent = original.value,
            newMentions = original.mentions,
            newAttachments = original.attachments,
        ) else content,
        instant = if (keepLocalEdit) editStatus.lastEditInstant else instant,
        markAsSent = alreadyEdited && !keepLocalEdit,
        notify = !keepLocalEdit,
    )
}

/** The app currently applies multipart edits to text/mentions while retaining the attachment rows. */
fun MessageContent.MultipartEdited.toEntity(): MessageEntityContent.Text = MessageEntityContent.Text(
    messageBody = newTextContent.orEmpty(),
    mentions = newMentions.map { it.toDao() },
)
