/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.wire.kalium.logic.feature.conversation.delete

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetLocallyDeletedConversationIdsUseCaseTest {
    @Test
    fun givenLocallyDeletedConversations_whenInvoked_thenReturnsTheirIds() = runTest {
        val repository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val expected = listOf(ConversationId("conversation", "example.com"))
        everySuspend { repository.getLocallyDeletedConversationIds() } returns Either.Right(expected)

        val result = GetLocallyDeletedConversationIdsUseCaseImpl(repository)()

        assertEquals(Either.Right(expected), result)
    }
}
