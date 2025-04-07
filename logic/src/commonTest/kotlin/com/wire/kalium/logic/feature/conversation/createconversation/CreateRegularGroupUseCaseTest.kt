/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.feature.conversation.createconversation

import com.wire.kalium.logic.data.conversation.ConversationOptions
import com.wire.kalium.logic.framework.TestConversation
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CreateRegularGroupUseCaseTest {

    @Test
    fun givenRegularGroupOptions_WhenCreatingGroup_ThenRegularGroupIsCreated() = runTest {
        val groupName = "Test Group"
        val (arrangement, createRegularGroup) = Arrangement()
            .withGroupConversationCreator()
            .arrange()

        createRegularGroup.invoke(groupName, emptyList(), ConversationOptions())

        coVerify {
            arrangement.groupConversationCreator(
                name = groupName,
                userIdList = emptyList(),
                options = ConversationOptions().copy(
                    groupType = ConversationOptions.GroupType.REGULAR_GROUP
                )
            )
        }
    }

    private class Arrangement {

        @Mock
        val groupConversationCreator = mock(GroupConversationCreator::class)

        private val createRegularGroup = CreateRegularGroupUseCaseImpl(groupConversationCreator)

        suspend fun withGroupConversationCreator() = apply {
            coEvery { groupConversationCreator(any(), any(), any()) }.returns(ConversationCreationResult.Success(TestConversation.GROUP()))
        }

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let { this to createRegularGroup }
    }
}
