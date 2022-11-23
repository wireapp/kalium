package com.wire.kalium.logic.data.message.mention

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.protobuf.messages.Mention

interface MessageMentionMapper {
    fun fromDaoToModel(mention: MessageEntity.Mention): MessageMention
    fun fromModelToDao(mention: MessageMention): MessageEntity.Mention
    fun fromProtoToModel(mention: Mention): MessageMention
    fun fromModelToProto(mention: MessageMention): Mention
}

class MessageMentionMapperImpl(
    private val idMapper: IdMapper,
    private val selfUserId: UserId
) : MessageMentionMapper {

    override fun fromDaoToModel(mention: MessageEntity.Mention): MessageMention {
        return MessageMention(
            start = mention.start,
            length = mention.length,
            userId = idMapper.fromDaoModel(mention.userId)
        )
    }

    override fun fromModelToDao(mention: MessageMention): MessageEntity.Mention {
        return MessageEntity.Mention(
            start = mention.start,
            length = mention.length,
            userId = idMapper.toDaoModel(mention.userId)
        )
    }

    override fun fromProtoToModel(mention: Mention): MessageMention {
        val userId = mention.qualifiedUserId?.let {
            idMapper.fromProtoUserId(it)
        } ?: UserId(
            mention.mentionType?.value as String,
            selfUserId!!.domain
        )
        return MessageMention(
            start = mention.start,
            length = mention.length,
            userId = userId,
        )
    }

    override fun fromModelToProto(mention: MessageMention): Mention = Mention(
        start = mention.start,
        length = mention.length,
        qualifiedUserId = idMapper.toProtoUserId(mention.userId),
        mentionType = Mention.MentionType.UserId(mention.userId.value)
    )
}
