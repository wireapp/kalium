package com.wire.kalium.api.conversation

interface ConversationApi {

    suspend fun conversationsByBatch(queryStart: String?, querySize: Int)

    companion object {

    }
}
