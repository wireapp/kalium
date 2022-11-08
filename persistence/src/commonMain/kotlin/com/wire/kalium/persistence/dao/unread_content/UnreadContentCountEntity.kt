package com.wire.kalium.persistence.dao.unread_content

import com.wire.kalium.persistence.dao.message.MessageEntity

typealias UnreadContentCountEntity = Map<MessageEntity.ContentType, Int>
