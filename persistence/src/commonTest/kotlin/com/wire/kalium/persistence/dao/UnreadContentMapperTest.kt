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

package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UnreadContentMapperTest {

    @Test
    fun givenAnUnreadKnocks_whenMappingFromDB_shouldReturnUnreadContentCount() = runTest {
        val unreadMessage = """ {"KNOCK":2} """
        val unreadContentCountEntity = mapOf(MessageEntity.ContentType.KNOCK to 2)
        val result = UnreadContentMapper.unreadContentTypeFromJsonString(unreadMessage)

        assertEquals(
            result,
            unreadContentCountEntity
        )
    }

    @Test
    fun givenAnUnreadMissedCalls_whenMappingFromDB_shouldReturnUnreadContentCount() = runTest {
        val unreadMessage = """ {"MISSED_CALL":2} """
        val unreadContentCountEntity = mapOf(MessageEntity.ContentType.MISSED_CALL to 2)
        val result = UnreadContentMapper.unreadContentTypeFromJsonString(unreadMessage)

        assertEquals(
            result,
            unreadContentCountEntity
        )
    }

    @Test
    fun givenAnUnreadTextMessages_whenMappingFromDB_shouldReturnUnreadContentCount() = runTest {
        val unreadMessage = """ {"TEXT":2} """
        val unreadContentCountEntity = mapOf(MessageEntity.ContentType.TEXT to 2)
        val result = UnreadContentMapper.unreadContentTypeFromJsonString(unreadMessage)

        assertEquals(
            result,
            unreadContentCountEntity
        )
    }

    @Test
    fun givenAnUnreadAssetMessages_whenMappingFromDB_shouldReturnUnreadContentCount() = runTest {
        val unreadMessage = """ {"ASSET":2} """
        val unreadContentCountEntity = mapOf(MessageEntity.ContentType.ASSET to 2)
        val result = UnreadContentMapper.unreadContentTypeFromJsonString(unreadMessage)

        assertEquals(
            result,
            unreadContentCountEntity
        )
    }

    // TODO test replies
}
