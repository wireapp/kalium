package com.wire.kalium.persistence.dao.unread_content

import com.wire.kalium.persistence.util.JsonSerializer
import kotlinx.serialization.decodeFromString

object UnreadContentMapper {

    private val serializer = JsonSerializer()
    fun unreadContentTypeFromJsonString(unreadContentJson: String?): UnreadContentCountEntity =
        unreadContentJson?.let {
            serializer.decodeFromString(unreadContentJson)
        } ?: emptyMap()
}
