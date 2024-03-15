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

package com.wire.kalium.logic.data.message.mention

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.protobuf.messages.Mention

interface MessageMentionMapper {
    fun fromDaoToModel(mention: MessageEntity.Mention): MessageMention
    fun fromModelToDao(mention: MessageMention): MessageEntity.Mention
    fun fromProtoToModel(mention: Mention): MessageMention?
    fun fromModelToProto(mention: MessageMention): Mention
}

class MessageMentionMapperImpl(
    private val idMapper: IdMapper,
    private val selfUserId: UserId
) : MessageMentionMapper {

    override fun fromDaoToModel(mention: MessageEntity.Mention): MessageMention {
        return mention.toModel(selfUserId)
    }

    override fun fromModelToDao(mention: MessageMention): MessageEntity.Mention {
        return mention.toDao()
    }

    override fun fromProtoToModel(mention: Mention): MessageMention? = mention.qualifiedUserId?.let {
        MessageMention(
            start = mention.start,
            length = mention.length,
            userId = idMapper.fromProtoUserId(it),
            isSelfMention = idMapper.fromProtoUserId(it) == selfUserId
        )
    } ?: run {
        mention.mentionType?.let { mentionType ->
            val userId = UserId(mentionType.value as String, selfUserId.domain)
            MessageMention(
                start = mention.start,
                length = mention.length,
                userId = userId,
                isSelfMention = userId == selfUserId
            )

        } ?: run { null }
    }

    override fun fromModelToProto(mention: MessageMention): Mention = Mention(
        start = mention.start,
        length = mention.length,
        qualifiedUserId = idMapper.toProtoUserId(mention.userId),
        mentionType = Mention.MentionType.UserId(mention.userId.value)
    )
}

fun MessageEntity.Mention.toModel(selfUserId: UserId?): MessageMention = MessageMention(
    start = start,
    length = length,
    userId = userId.toModel(),
    isSelfMention = userId.toModel() == selfUserId
)

fun MessageMention.toDao(): MessageEntity.Mention = MessageEntity.Mention(
    start = start,
    length = length,
    userId = userId.toDao()
)
