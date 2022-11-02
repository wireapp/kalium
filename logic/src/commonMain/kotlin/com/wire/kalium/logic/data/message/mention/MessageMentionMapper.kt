package com.wire.kalium.logic.data.message.mention

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.protobuf.messages.Mention
import com.wire.kalium.protobuf.messages.QualifiedUserId

interface MessageMentionMapper {
    fun fromDaoToModel(mention: MessageEntity.Mention): MessageMention
    fun fromModelToDao(mention: MessageMention): MessageEntity.Mention
    fun fromProtoToModel(mention: Mention): MessageMention?
    fun fromModelToProto(mention: MessageMention): Mention
}

class MessageMentionMapperImpl(private val idMapper: IdMapper) : MessageMentionMapper {

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

    override fun fromProtoToModel(mention: Mention): MessageMention? = mention.qualifiedUserId?.let { qualifiedUserId: QualifiedUserId ->
        // for now we only support direct mentions which require userId https://github.com/wireapp/kalium/pull/857#discussion_r960302664
        MessageMention(
            start = mention.start,
            length = mention.length,
            userId = idMapper.fromProtoUserId(qualifiedUserId)
        )
    }

    override fun fromModelToProto(mention: MessageMention): Mention = Mention(
        start = mention.start,
        length = mention.length,
        qualifiedUserId = idMapper.toProtoUserId(mention.userId)
    )
}
