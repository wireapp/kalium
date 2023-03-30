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
package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.message.mention.MessageMentionMapperImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.protobuf.messages.Mention
import com.wire.kalium.protobuf.messages.QualifiedUserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageMentionMapperTest {

    val selfUserId = UserId("user-id", "domain")
    val idMapper: IdMapper = IdMapperImpl()
    private val messageMentionMapper: MessageMentionMapper = MessageMentionMapperImpl(idMapper, selfUserId)

    @Test
    fun givenASelfMentionEntity_whenMappingFromDaoToModel_thenMessageMentionIsMappedAsASelfMention() {
        val mention = MessageEntity.Mention(
            start = 0,
            length = 1,
            userId = selfUserId.toDao()
        )
        val result = messageMentionMapper.fromDaoToModel(mention)
        assertEquals(result.start, mention.start)
        assertEquals(result.length, mention.length)
        assertEquals(result.userId.value, mention.userId.value)
        assertEquals(result.userId.domain, mention.userId.domain)
        assertTrue(result.isSelfMention)
    }

    @Test
    fun givenAnotherUsersMentionEntity_whenMappingFromDaoToModel_thenMessageMentionIsNotMappedAsASelfMention() {
        val otherUser = UserId("other-user-id", "domain")
        val mention = MessageEntity.Mention(
            start = 0,
            length = 1,
            userId = otherUser.toDao()
        )
        val result = messageMentionMapper.fromDaoToModel(mention)
        assertEquals(result.start, mention.start)
        assertEquals(result.length, mention.length)
        assertEquals(result.userId.value, mention.userId.value)
        assertEquals(result.userId.domain, mention.userId.domain)
        assertFalse(result.isSelfMention)
    }

    @Test
    fun givenAModelSelfMention_whenMappingFromModelToDao_thenMessageMentionIsMappedAsASelfMentionEntity() {
        val mention = MessageMention(
            start = 0,
            length = 1,
            userId = selfUserId,
            isSelfMention = true
        )
        val result = messageMentionMapper.fromModelToDao(mention)
        assertEquals(result.start, mention.start)
        assertEquals(result.length, mention.length)
        assertEquals(result.userId.value, mention.userId.value)
        assertEquals(result.userId.domain, mention.userId.domain)
    }

    @Test
    fun givenAnotherUsersMention_whenMappingFromDaoToModel_thenMessageMentionIsNotMappedAsASelfMentionEntity() {
        val otherUser = UserId("other-user-id", "domain")
        val mention = MessageMention(
            start = 0,
            length = 1,
            userId = otherUser,
            isSelfMention = false
        )
        val result = messageMentionMapper.fromModelToDao(mention)
        assertEquals(result.start, mention.start)
        assertEquals(result.length, mention.length)
        assertEquals(result.userId.value, mention.userId.value)
        assertEquals(result.userId.domain, mention.userId.domain)
    }

    @Test
    fun givenAProtoSelfMention_whenMappingFromProtoToModel_thenMessageMentionIsMappedAsASelfMention() {
        val mention = Mention(
            start = 0,
            length = 1,
            qualifiedUserId = QualifiedUserId(selfUserId.value, selfUserId.domain),
            mentionType = Mention.MentionType.UserId(selfUserId.value)
        )
        val result = messageMentionMapper.fromProtoToModel(mention)
        assertEquals(result.start, mention.start)
        assertEquals(result.length, mention.length)
        assertEquals(result.userId.value, mention.qualifiedUserId?.id)
        assertEquals(result.userId.domain, mention.qualifiedUserId?.domain)
        assertTrue(result.isSelfMention)
    }

    @Test
    fun givenAProtoUserMention_whenMappingFromProtoToModel_thenMessageMentionIsNotMappedAsASelfMention() {
        val otherUser = UserId("other-user-id", "domain")
        val mention = Mention(
            start = 0,
            length = 1,
            qualifiedUserId = QualifiedUserId(otherUser.value, otherUser.domain),
            mentionType = Mention.MentionType.UserId(otherUser.value)
        )
        val result = messageMentionMapper.fromProtoToModel(mention)
        assertEquals(result.start, mention.start)
        assertEquals(result.length, mention.length)
        assertEquals(result.userId.value, mention.qualifiedUserId?.id)
        assertEquals(result.userId.domain, mention.qualifiedUserId?.domain)
        assertFalse(result.isSelfMention)
    }

    @Test
    fun givenAModelSelfMention_whenMappingFromModelToProto_thenMessageMentionIsMappedAsASelfMention() {
        val mention = MessageMention(
            start = 0,
            length = 1,
            userId = selfUserId,
            isSelfMention = true
        )
        val result = messageMentionMapper.fromModelToProto(mention)
        assertEquals(result.start, mention.start)
        assertEquals(result.length, mention.length)
        assertEquals(result.qualifiedUserId?.id, mention.userId.value)
        assertEquals(result.qualifiedUserId?.domain, mention.userId.domain)
        assertEquals(result.userId, mention.userId.value)
    }

    @Test
    fun givenAModelUserMention_whenMappingFromModelToProto_thenMessageMentionIsNotMappedAsASelfMention() {
        val otherUser = UserId("other-user-id", "domain")
        val mention = MessageMention(
            start = 0,
            length = 1,
            userId = otherUser,
            isSelfMention = true
        )
        val result = messageMentionMapper.fromModelToProto(mention)
        assertEquals(result.start, mention.start)
        assertEquals(result.length, mention.length)
        assertEquals(result.qualifiedUserId?.id, mention.userId.value)
        assertEquals(result.qualifiedUserId?.domain, mention.userId.domain)
        assertEquals(result.userId, mention.userId.value)
    }
}
