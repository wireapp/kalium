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

import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalKaliumApi::class)
class MessageEditStatusMapperTest {

    @Test
    fun givenNotEditedEntityStatus_whenMapping_thenNotEditedIsReturned() {
        assertEquals(Message.EditStatus.NotEdited, MessageEntity.EditStatus.NotEdited.toModel())
    }

    @Test
    fun givenEditedEntityStatus_whenMapping_thenEditInstantIsPreserved() {
        val editInstant = Instant.parse("2026-08-22T10:15:30Z")

        assertEquals(
            Message.EditStatus.Edited(editInstant),
            MessageEntity.EditStatus.Edited(editInstant).toModel(),
        )
    }
}
