package com.wire.kalium.persistence.dao

import app.cash.sqldelight.ColumnAdapter
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.ASSET
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.TEXT

class ContentTypeAdapter : ColumnAdapter<ContentType, String> {

    override fun decode(databaseValue: String): ContentType {
        return when (databaseValue) {
            TEXT.name -> TEXT
            ASSET.name -> ASSET
            else -> TEXT
        }
    }

    override fun encode(value: ContentType) = value.name
    
}
