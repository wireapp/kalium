package com.wire.kalium.monkeys.actions

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.monkeys.conversation.Monkey
import com.wire.kalium.monkeys.conversation.MonkeyConversation
import com.wire.kalium.monkeys.importer.ActionType
import com.wire.kalium.monkeys.importer.UserCount
import com.wire.kalium.monkeys.pool.ConversationPool
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AddUserToConversationActionTest {
    @Test
    fun givenAddUserConfig_newUsersShouldBeAdded() = runTest {
        val config = ActionType.AddUsersToConversation(1u, UserCount.single())
        mockkObject(ConversationPool)
        val monkey = mockk<Monkey>(relaxed = true)
        val conversation = mockk<MonkeyConversation>(relaxed = true)
        val creator = mockk<Monkey>()
        val coreLogic = mockk<CoreLogic>()
        every { ConversationPool.randomDynamicConversations(config.countGroups.toInt()) } returns listOf(conversation)
        every { conversation.creator } returns creator
        every { creator.randomPeers(config.userCount) } returns listOf(monkey)
        AddUserToConversationAction(config).execute(coreLogic)
        coVerify(exactly = 1) { conversation.addMonkeys(listOf(monkey)) }
        verify(exactly = 1) { conversation.creator }
        confirmVerified(conversation)
    }
}
