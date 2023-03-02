package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class EnqueueMessageSelfDeletionUseCase {

    val testScope = TestScope()

    private val selfDeletingMessageEnqueuer = EphemeralMessageDeletionHandler(testScope)

    @Test
    fun test() = runTest {
        selfDeletingMessageEnqueuer.enqueue(ConversationId("test", "test"), "test")
    }

}
