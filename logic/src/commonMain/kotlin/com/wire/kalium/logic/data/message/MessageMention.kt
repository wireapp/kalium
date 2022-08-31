package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.protobuf.messages.Mention

data class MessageMention(
    val start: Int,
    val length: Int,
    val userId: UserId?
)

interface MessageMentionMapper {
    fun fromDaoToModel(mention: MessageEntity.Mention): MessageMention
    fun fromModelToDao(mention: MessageMention): MessageEntity.Mention
    fun fromProtoToModel(mention: Mention): MessageMention
    fun fromModelToProto(mention: MessageMention): Mention
}

class MessageMentionMapperImpl(private val idMapper: IdMapper) : MessageMentionMapper {

    override fun fromDaoToModel(mention: MessageEntity.Mention): MessageMention {
        return MessageMention(
            start = mention.start,
            length = mention.length,
            userId = mention.userId?.let { idMapper.fromDaoModel(it) }
        )
    }

    override fun fromModelToDao(mention: MessageMention): MessageEntity.Mention {
        return MessageEntity.Mention(
            start = mention.start,
            length = mention.length,
            userId = mention.userId?.let { idMapper.toDaoModel(it) }
        )
    }

    override fun fromProtoToModel(mention: Mention): MessageMention = MessageMention(
        start = mention.start,
        length = mention.length,
        userId = mention.qualifiedUserId?.let { idMapper.fromProtoUserId(it) }
    )

    override fun fromModelToProto(mention: MessageMention): Mention = Mention(
        start = mention.start,
        length = mention.length,
        qualifiedUserId = mention.userId?.let { idMapper.toProtoUserId(it) }
    )
}
