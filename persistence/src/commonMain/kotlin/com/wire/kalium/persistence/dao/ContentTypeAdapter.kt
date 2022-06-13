package com.wire.kalium.persistence.dao

import app.cash.sqldelight.ColumnAdapter
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.TEXT

class ContentTypeAdapter : ColumnAdapter<ContentType, String> {

    override fun decode(databaseValue: String): ContentType =
        ContentType.values().firstOrNull { it.name == databaseValue } ?: TEXT

    override fun encode(value: ContentType) = value.name
    
}
