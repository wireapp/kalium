package com.wire.kalium.persistence.dao.reaction

import com.wire.kalium.persistence.util.JsonSerializer
import kotlinx.serialization.decodeFromString

object ReactionMapper {
    private val serializer = JsonSerializer()
    fun reactionsCountFromJsonString(allReactionJson: String?): ReactionsCountEntity =
        allReactionJson?.let {
            serializer.decodeFromString(allReactionJson)
        } ?: emptyMap()

    fun userReactionsFromJsonString(userReactionsJson: String?): UserReactionsEntity =
        userReactionsJson?.let {
            serializer.decodeFromString(userReactionsJson)
        } ?: emptySet()
}
