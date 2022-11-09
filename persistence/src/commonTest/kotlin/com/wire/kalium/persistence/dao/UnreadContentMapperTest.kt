package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.dao.UnreadContentMapper
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
